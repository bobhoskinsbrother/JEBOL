package org.jebol.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.FilePort;
import org.jebol.domain.eval.Interruption;
import org.jebol.domain.eval.Natives;
import org.jebol.domain.eval.Outcome;
import org.jebol.domain.eval.OutputPort;
import org.jebol.domain.eval.QuitRequested;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.eval.Stopped;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.JavaObjectValue;
import org.jebol.domain.value.NativeValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

/**
 * One REBOL interpreter, embedded in a host application.
 *
 * <p>An instance is owned by one thread and holds every value reachable from
 * it. Series share mutable storage by design, so aliasing is observable, and
 * confining that to one thread is what makes it need no synchronisation at
 * all. A host wanting concurrency runs several instances; handing one to two
 * threads is a mistake this class does not defend against and does not make
 * safe.
 *
 * <p>The exception is {@link #cancel()}, which is meant to be called from
 * another thread and does nothing but set a flag the running script notices.
 *
 * <p>Nothing here throws for anything a script can do. Failing, running too
 * long and nesting too deep all arrive as a {@link ScriptOutcome}, because a
 * host that had to catch a throwable to learn a script misbehaved could not
 * tell that apart from a bug in JEBOL.
 */
public final class Interpreter {

    private final Context systemContext;
    private final Context userContext;
    private final Evaluator evaluator;
    private final Bounds bounds;

    /** What each borrowed file stopped on, in the order they were tried. */
    private final Map<String, String> borrowedLoadFailures = new LinkedHashMap<>();

    private final AtomicBoolean cancellationRequested = new AtomicBoolean();
    private volatile long deadlineNanos = Long.MAX_VALUE;

    /** Whether to load Rebol's own library over the prelude. */
    private final boolean borrowFromRebol;

    private Interpreter(OutputPort output, Bounds bounds) {
        this(output, bounds, false);
    }

    private Interpreter(OutputPort output, Bounds bounds, boolean borrowFromRebol) {
        this.borrowFromRebol = borrowFromRebol;
        Natives natives = Natives.standard(bounds.grantedServices());
        natives.useFileSeparator(java.io.File.separatorChar);
        this.bounds = bounds;
        this.systemContext = natives.asContext();
        this.userContext = Context.childOf(systemContext);
        this.evaluator = new Evaluator(
                natives.behaviours(),
                systemContext,
                output,
                bounds.maximumNesting(),
                this::reasonToStop,
                bounds.checkEvery());
        evaluator.putRuntimeWordsIn(userContext);
        loadPrelude();
        loadRebolsOwnLibrary();
        // What the library's own loading caught is not what the script
        // did, and system/state is the script's view.
        natives.forgetStartupState();
    }

    /** Where the REBOL half of the standard library lives. */
    private static final String PRELUDE = "/org/jebol/prelude.reb";

    /**
     * Evaluates the prelude into the context the natives are in.
     *
     * <p>The standard function set is two layers: natives written in Java
     * because they reach something the language cannot, and this, written
     * in REBOL because it can be. Loading it here rather than lazily means
     * its functions see the natives and each other, and that nothing can
     * observe an interpreter without it.
     *
     * <p>A failure here is a defect in JEBOL rather than something a
     * script did, so it is raised as one rather than leaving an
     * interpreter half-built.
     */
    private void loadPrelude() {
        String source;
        try (InputStream reading = Interpreter.class.getResourceAsStream(PRELUDE)) {
            if (reading == null) {
                throw new IllegalStateException("the prelude is missing from the build");
            }
            source = new String(reading.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new IllegalStateException("the prelude could not be read", unreadable);
        }
        TranscodeResult read = Transcoder.transcode(source);
        BlockValue values = read.values().orElseThrow(() -> new IllegalStateException(
                "the prelude does not read: " + read.error().orElseThrow()));
        // The header is data, not code. Everything after it defines the
        // library, into the system context so it sits beside the natives.
        BlockValue body = values.remaining().size() >= 2
                ? values.atIndex(3)
                : values;
        // Only the words the prelude assigns, not every word it mentions.
        // Defining them all put a slot in the system context for every
        // parameter name inside every function spec, and those slots then
        // shadowed the real parameters: a loop body inside a prelude
        // function could not see an argument called `body`.
        defineAssignedWordsIn(body, systemContext);
        Outcome outcome = evaluator.evaluate(Binder.bind(body, systemContext), systemContext);
        if (outcome instanceof Outcome.Raised raised) {
            throw new IllegalStateException("the prelude failed to load: " + raised.failure());
        }
    }

    /** Where the files borrowed from Rebol's own library live. */
    private static final String MEZZANINE = "/org/jebol/mezz/";

    /**
     * Loads the files of Rebol's own library that JEBOL can run.
     *
     * <p>These are Rebol's, not JEBOL's, and they are loaded rather than
     * rewritten because that is what the two-layer design is for. An
     * implementation that can only be extended in its host language
     * cannot borrow, and there are twenty-five thousand lines here worth
     * borrowing from.
     *
     * <p>Off by default, and the reason is the work rather than a
     * doubt about the approach. Loading these replaces JEBOL's own
     * definition of every word they define, and where the natives
     * underneath are wrong the borrowed version is worse than what it
     * replaced -- forty-six corpus entries and a hundred suite
     * assertions worse, at the last count.
     *
     * <p>That list is the point. Each failure names a native that does
     * not do what Rebol's own code expects of it, which is a better
     * work-list than any inventory: it is driven by what the language
     * actually needs rather than by what is missing from a catalogue.
     * REDUCE taking only a block and the eight typeset predicates being
     * absent were both found this way.
     *
     * <p>The switch comes off when the list empties. Until then the
     * default interpreter is the one that works.
     *
     * <p>A file that fails here is skipped rather than fatal, unlike the
     * prelude. The prelude is JEBOL's and its failure is a defect; these
     * are borrowed, and one that stops working leaves an interpreter
     * that is smaller rather than broken.
     *
     * <p>What each failure was is kept, because a file that stops halfway
     * defines nothing below the line it stopped on and says so nowhere
     * else. Swallowing that hid base-defs.reb generating its six reflector
     * functions into a scope that was thrown away straight afterwards.
     */
    private void loadRebolsOwnLibrary() {
        for (String name : borrowedFileNames()) {
            String source = resourceText(MEZZANINE + name);
            if (source == null) {
                continue;
            }
            TranscodeResult read = Transcoder.transcode(source);
            if (read.values().isEmpty()) {
                borrowedLoadFailures.put(name, "did not read");
                continue;
            }
            BlockValue values = read.values().orElseThrow();
            // The REBOL [...] header is data, not code.
            BlockValue body = values.remaining().size() >= 2
                    ? values.atIndex(3)
                    : values;
            defineAssignedWordsIn(body, systemContext);
            Outcome outcome = evaluator.evaluate(
                    Binder.bind(body, systemContext), systemContext);
            if (outcome instanceof Outcome.Raised raised) {
                borrowedLoadFailures.put(name, raised.failure().toString());
            }
        }
    }

    /**
     * Which borrowed files stopped partway, and on what.
     *
     * <p>Keyed by file name, in the order ORDER.txt lists them.
     */
    public Map<String, String> borrowedLoadFailures() {
        return Map.copyOf(borrowedLoadFailures);
    }

    private List<String> borrowedFileNames() {
        String order = resourceText(MEZZANINE + "ORDER.txt");
        if (order == null) {
            return List.of();
        }
        return order.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }

    private static String resourceText(String path) {
        try (InputStream reading = Interpreter.class.getResourceAsStream(path)) {
            return reading == null
                    ? null
                    : new String(reading.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return null;
        }
    }

    /** An interpreter with the standard bounds, whose output goes nowhere. */
    public static Interpreter create() {
        return new Interpreter(OutputPort.discarding(), Bounds.standard());
    }

    /**
     * An interpreter that also loads Rebol's own library.
     *
     * <p>For measuring what the borrowed code still needs from the
     * native layer. Not the default: see loadRebolsOwnLibrary.
     */
    public static Interpreter borrowingFromRebol() {
        Interpreter interpreter = new Interpreter(
                OutputPort.discarding(), Bounds.standard(), true);
        return interpreter;
    }

    /** An interpreter with bounds the host chose. */
    public static Interpreter withBounds(Bounds bounds) {
        return new Interpreter(OutputPort.discarding(), bounds);
    }

    /** An interpreter writing to somewhere the host chose. */
    public static Interpreter writingTo(OutputPort output) {
        return new Interpreter(output, Bounds.standard());
    }

    public static Interpreter writingTo(OutputPort output, Bounds bounds) {
        return new Interpreter(output, bounds);
    }

    public Bounds bounds() {
        return bounds;
    }

    /** The context a console assigns fresh words into. */
    public Context userContext() {
        return userContext;
    }

    /**
     * Reads a script and runs it, within the bounds this interpreter was
     * given. Never throws for anything the script did.
     */
    public ScriptOutcome run(String source) {
        cancellationRequested.set(false);
        long startedAt = System.nanoTime();
        deadlineNanos = startedAt + bounds.wallClockLimit().toNanos();
        try {
            return conclude(evaluate(source), startedAt);
        } catch (QuitRequested quit) {
            return new ScriptOutcome(
                    Conclusion.QUIT_EARLY,
                    quit.answer(),
                    Duration.ofNanos(System.nanoTime() - startedAt));
        } catch (Stopped stopped) {
            return new ScriptOutcome(
                    conclusionFor(stopped),
                    ErrorValue.of(ErrorCategory.ACCESS, idFor(stopped), stopped.reason()),
                    Duration.ofNanos(System.nanoTime() - startedAt));
        } finally {
            deadlineNanos = Long.MAX_VALUE;
        }
    }

    /**
     * Asks a running script to stop. Safe to call from another thread, and
     * harmless when nothing is running.
     */
    public void cancel() {
        cancellationRequested.set(true);
    }

    /**
     * One expression's outcome, and the source still unread.
     *
     * <p>"The value of this source" and "the value of its first
     * expression" are different questions, and {@link #run} only answers
     * the first. A console asks it for a line; anything reading a script
     * where the first expression is the interesting one and the rest is
     * ordinary code needs the second.
     */
    public record Step(ScriptOutcome outcome, String rest) {
    }

    /**
     * Evaluates the first expression of the source, within the same bounds
     * {@link #run} uses and with the same promise that nothing escapes as a
     * host exception.
     */
    public Step runNext(String source) {
        cancellationRequested.set(false);
        long startedAt = System.nanoTime();
        deadlineNanos = startedAt + bounds.wallClockLimit().toNanos();
        try {
            TranscodeResult read = Transcoder.transcode(source);
            if (!read.succeeded()) {
                return new Step(
                        conclude(new Outcome.Raised(read.error().orElseThrow()), startedAt),
                        "");
            }
            BlockValue values = read.values().orElseThrow();
            if (values.atTail()) {
                return new Step(conclude(
                        new Outcome.Completed(UnsetValue.unset()), startedAt), "");
            }
            defineWordsIn(values);
            BlockValue bound = Binder.bind(values, userContext);
            try {
                Evaluator.Step taken = evaluator.evaluateNextOrRaise(bound, userContext);
                return new Step(
                        conclude(new Outcome.Completed(taken.value()), startedAt),
                        Molder.moldOnly(bound.atIndex(taken.nextIndex())));
            } catch (Raised raised) {
                // What follows a failed expression is still handed back, so
                // a caller walking a script can carry on past one that went
                // wrong rather than losing the rest of the source.
                return new Step(
                        conclude(new Outcome.Raised(raised.error()), startedAt),
                        Molder.moldOnly(bound.atIndex(2)));
            }
        } catch (QuitRequested quit) {
            return new Step(new ScriptOutcome(
                    Conclusion.QUIT_EARLY,
                    quit.answer(),
                    Duration.ofNanos(System.nanoTime() - startedAt)), "");
        } catch (Stopped stopped) {
            return new Step(new ScriptOutcome(
                    conclusionFor(stopped),
                    ErrorValue.of(ErrorCategory.ACCESS, idFor(stopped), stopped.reason()),
                    Duration.ofNanos(System.nanoTime() - startedAt)), "");
        } finally {
            deadlineNanos = Long.MAX_VALUE;
        }
    }

    private Outcome evaluate(String source) {
        TranscodeResult read = Transcoder.transcode(source);
        if (!read.succeeded()) {
            return new Outcome.Raised(read.error().orElseThrow());
        }
        defineWordsIn(read.values().orElseThrow());
        BlockValue bound = Binder.bind(read.values().orElseThrow(), userContext);
        return evaluator.evaluate(bound, userContext);
    }

    private ScriptOutcome conclude(Outcome outcome, long startedAt) {
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        return switch (outcome) {
            case Outcome.Completed completed ->
                    new ScriptOutcome(Conclusion.PRODUCED_A_VALUE, completed.result(), elapsed);
            case Outcome.Raised raised ->
                    new ScriptOutcome(Conclusion.RAISED, raised.failure(), elapsed);
        };
    }

    /**
     * Whether the running script should stop. Consulted by the evaluator
     * every so often; the deadline is checked before cancellation because a
     * script past its time is stopped whether or not anyone asked.
     */
    private Optional<String> reasonToStop() {
        if (System.nanoTime() >= deadlineNanos) {
            return Optional.of("the script ran longer than "
                    + bounds.wallClockLimit().toMillis() + "ms");
        }
        if (cancellationRequested.get()) {
            return Optional.of("the host asked the script to stop");
        }
        return Optional.empty();
    }

    private Conclusion conclusionFor(Stopped stopped) {
        return stopped.reason().startsWith("the host")
                ? Conclusion.CANCELLED
                : Conclusion.TIMED_OUT;
    }

    private String idFor(Stopped stopped) {
        return conclusionFor(stopped) == Conclusion.CANCELLED ? "cancelled" : "timeout";
    }

    /**
     * Gives every word in the source a slot if nothing already knows it.
     *
     * <p>The console's convenience, kept at the edge on purpose: the
     * evaluator creates no slots, so a block handed to a function behaves the
     * same whether or not anyone is watching. Every word rather than only the
     * assigned ones, because that is what published REBOL does: referring to
     * a word nobody defined reports "has no value" rather than "not defined".
     */
    public void defineFreshWordsIn(String source) {
        TranscodeResult read = Transcoder.transcode(source);
        read.values().ifPresent(this::defineWordsIn);
    }

    /** The words a block assigns to, rather than every word it mentions. */
    private void defineAssignedWordsIn(BlockValue block, Context into) {
        for (Value item : block.remaining()) {
            if (item instanceof WordValue word && word.datatype() == Datatype.SET_WORD
                    && !into.knows(word.canonical())) {
                into.define(word.spelling());
            }
        }
    }

    private void defineWordsIn(BlockValue block) {
        defineWordsIn(block, userContext);
    }

    private void defineWordsIn(BlockValue block, Context into) {
        for (Value item : block.remaining()) {
            switch (item) {
                case WordValue word -> {
                    if (!into.knows(word.canonical())) {
                        into.define(word.spelling());
                    }
                }
                case BlockValue nested -> defineWordsIn(nested, into);
                default -> {
                }
            }
        }
    }

    // ---- interop ---------------------------------------------------------

    /**
     * Hands the script a value under a name of the host's choosing.
     *
     * <p>Converted where there is an obvious counterpart and held as it is
     * otherwise. What the host does with the object afterwards is the host's
     * concern: JEBOL owns its REBOL values and makes no promise about a Java
     * object it was handed, because freezing everything crossing the boundary
     * would make interop useless for what people want it for.
     */
    public void define(String name, Object supplied) {
        userContext.set(name, HostValues.fromHost(supplied));
    }

    /** Hands the script a host null, which is not REBOL's none. */
    public void defineNull(String name, Class<?> type) {
        userContext.set(name, JavaObjectValue.hostNull(type.getName()));
    }

    /**
     * Hands the script something it can call, if the bounds allow calling.
     *
     * <p>Whatever the host function throws becomes an ordinary {@code error!}
     * the script could catch. Nothing crosses back into REBOL as an
     * exception, which keeps the promise that a script can catch anything a
     * script can cause; the cost is that a caught-and-rethrown host exception
     * arrives as an error value rather than the throwable it started as.
     */
    public void defineFunction(String name, int arity, HostFunction function) {
        List<Parameter> parameters = new ArrayList<>(arity);
        for (int position = 1; position <= arity; position++) {
            parameters.add(Parameter.required("argument" + position));
        }
        userContext.set(name, new NativeValue(name, parameters));
        evaluator.defineNative(name, (arguments, ignored, context) ->
                runHostFunction(name, function, arguments));
    }

    /**
     * Runs a host function, refusing if the bounds do not allow calling out
     * and turning whatever it throws into an ordinary error.
     */
    private Value runHostFunction(
            String name, HostFunction function, List<Value> arguments) {

        if (!bounds.hostAccess().allowsCalling()) {
            throw new org.jebol.domain.eval.Raised(ErrorValue.of(
                    ErrorCategory.ACCESS, "host-access",
                    "this interpreter may not call out to " + name));
        }
        List<Object> supplied = arguments.stream().map(HostValues::toHost).toList();
        try {
            return HostValues.fromHost(function.call(supplied));
        } catch (RuntimeException | Error thrown) {
            throw new org.jebol.domain.eval.Raised(ErrorValue.of(
                    ErrorCategory.USER, "host-error",
                    name + " failed: " + thrown.getMessage()));
        }
    }

    /** Gives the script a way to start another program. */
    public void useProcesses(org.jebol.domain.eval.ProcessPort port) {
        evaluator.useProcesses(port);
    }

    /**
     * Gives the script a console to read a line from.
     *
     * <p>Writing goes elsewhere. A host almost always wants to see what a
     * script printed and almost never wants it to stop and wait.
     */
    public void useConsole(org.jebol.domain.eval.ConsolePort port) {
        evaluator.useConsole(port);
    }

    /**
     * Gives the script the host's environment to read.
     *
     * <p>Reading only. A JVM cannot change the environment of its own
     * process, thus SET-ENV has nothing to call and refuses.
     */
    public void useEnvironment(org.jebol.domain.eval.EnvironmentPort port) {
        evaluator.useEnvironment(port);
    }

    /**
     * Gives the script a filesystem to reach.
     *
     * <p>Until this is called, a script reaches nothing: reading and writing
     * both refuse. That is the same default as {@link HostAccess}, for the
     * same reason.
     */
    public void useFileSystem(FilePort port) {
        evaluator.useFiles(port);
    }

    /** Reads source without evaluating it, leaving every word unbound. */
    public TranscodeResult read(String source) {
        return Transcoder.transcode(source);
    }

    /** What a console would show for an outcome. */
    public String display(ScriptOutcome outcome) {
        if (outcome.succeeded() && outcome.value().datatype() == Datatype.UNSET) {
            return "";
        }
        return outcome.display();
    }

    /** The value of an empty script, for a caller that wants a starting point. */
    public static Value nothing() {
        return UnsetValue.unset();
    }

    /** Molds any value the way the console would. */
    public static String show(Value value) {
        return Molder.mold(value);
    }
}
