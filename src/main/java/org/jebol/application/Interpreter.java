package org.jebol.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.FilePort;
import org.jebol.domain.eval.Interruption;
import org.jebol.domain.eval.Natives;
import org.jebol.domain.eval.Outcome;
import org.jebol.domain.eval.OutputPort;
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

    private final AtomicBoolean cancellationRequested = new AtomicBoolean();
    private volatile long deadlineNanos = Long.MAX_VALUE;

    private Interpreter(OutputPort output, Bounds bounds) {
        Natives natives = Natives.standard();
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
    }

    /** An interpreter with the standard bounds, whose output goes nowhere. */
    public static Interpreter create() {
        return new Interpreter(OutputPort.discarding(), Bounds.standard());
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

    private void defineWordsIn(BlockValue block) {
        for (Value item : block.remaining()) {
            switch (item) {
                case WordValue word -> {
                    if (!userContext.knows(word.canonical())) {
                        userContext.define(word.spelling());
                    }
                }
                case BlockValue nested -> defineWordsIn(nested);
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
