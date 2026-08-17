package org.jebol.application;

import org.jebol.domain.eval.*;
import org.jebol.domain.host.HostService;
import org.jebol.domain.read.LibraryFileHeader;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

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

    /** Where the sys files define their words: {@code system/contexts/sys}. */
    private final Context systemInternals;

    private Interpreter(OutputPort output, Bounds bounds) {
        Set<HostService> duringTheBoot = EnumSet.of(HostService.CLOCK);
        duringTheBoot.addAll(bounds.grantedServices());
        Natives natives = Natives.standard(duringTheBoot);
        natives.useFileSeparator(java.io.File.separatorChar);
        if (bounds.grantedServices().contains(HostService.PROCESSES)) {
            natives.useBootLauncher(writtenBootLauncher());
        }
        String catalogue = resourceText("/org/jebol/errors.reb");
        natives.useErrorCatalogue(catalogue == null ? "" : catalogue);
        this.bounds = bounds;
        this.systemContext = natives.asContext();
        this.systemInternals = natives.systemInternals();
        this.userContext = Context.childOf(systemContext);
        publishTheUserContext();
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
        registerTheSchemesJebolCanServe();
        natives.grantOnly(bounds.grantedServices());
        natives.forgetStartupState();
    }

    /**
     * Registers the schemes JEBOL has an actor for.
     *
     * <p>Rebol does this in {@code init-schemes}, which registers every scheme
     * its host can reach. JEBOL registers the one it can serve, through the
     * same REBOL function Rebol uses: MAKE-SCHEME in {@code sys-ports.reb}
     * builds the scheme and calls SET-SCHEME, and SET-SCHEME is the native
     * that attaches an actor.
     *
     * <p>After the borrowed library, because MAKE-SCHEME comes from it. This
     * is the seam the other way about: Java calls REBOL, exactly as Rebol's C
     * calls {@code make-port*}.
     */
    private void registerTheSchemesJebolCanServe() {
        if (!systemInternals.knows("make-scheme")) {
            return;
        }
        run("sys/decode-url: lib/decode-url: :sys/url-parser/parse-url");
        run("sys/make-scheme [title: \"Console Access\" name: 'console]");
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
        BlockValue body = values.remaining().size() >= 2
                ? values.atIndex(3)
                : values;
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
     * <p>Always, now. This was behind a switch while the borrowed versions were
     * worse than the natives they replaced -- forty-six corpus entries and a
     * hundred suite assertions worse, at the last count -- and each difference
     * named a native that did not do what Rebol's own code expected of it. That
     * list emptied and the switch came off.
     *
     * <p>The measurement went with it. Comparing an interpreter with the library
     * against one without needs two different interpreters, and there is only one
     * kind now. When it is worth having again it should be a comparison against a
     * real 3.22.1 rather than against JEBOL's own smaller self.
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
        for (String entry : borrowedFileNames()) {
            String name = fileNameIn(entry);
            String source = resourceText(MEZZANINE + name);
            if (source == null) {
                continue;
            }
            TranscodeResult read = Transcoder.transcode(source);
            if (read.values().isEmpty()) {
                borrowedLoadFailures.put(name, read.error().orElseThrow().toString());
                continue;
            }
            BlockValue values = read.values().orElseThrow();
            boolean hasHeader = startsWithARebolHeader(values);
            LibraryFileHeader header = hasHeader
                    ? LibraryFileHeader.readFrom(values.remaining().get(1))
                    : LibraryFileHeader.none();
            BlockValue body = hasHeader ? values.atIndex(3) : values;

            Outcome outcome = header.declaresAModule() || isAProtocol(name)
                    ? loadAsAModule(body, header)
                    : entry.endsWith(INTO_SYS)
                            ? loadAsASystemFile(body)
                            : loadInto(body, systemContext);
            if (outcome instanceof Outcome.Raised raised) {
                borrowedLoadFailures.put(name, raised.failure().toString());
            }
        }
    }

    /**
     * Loads a sys file: its own set-words go to sys, everything else to lib.
     *
     * <p>R3 runs these with rebind 2, which is two binds in this order:
     *
     * <pre>
     * Bind_Block(Sys_Context, BLK_HEAD(block), BIND_SET);   // new set-words to sys
     * Bind_Block(Lib_Context, BLK_HEAD(block), BIND_DEEP);  // the rest to lib
     * </pre>
     *
     * <p>Two binds rather than one, because a sys file can use the same
     * spelling for both. base-defs.reb declares {@code decode-url: none} with
     * the note "set in sys init", and sys-ports.reb sets it with
     * {@code set 'decode-url} from inside a nested block. That lit-word has to
     * reach the library, while the top-level {@code decode-url: none} on the
     * line after has to reach sys. One bind cannot do both.
     *
     * <p>Loading these into the library instead is what made DECODE-URL none:
     * the two lines became the same word and the none, being last, won.
     */
    private Outcome loadAsASystemFile(BlockValue body) {
        for (Value item : body.remaining()) {
            if (item instanceof WordValue word
                    && word.datatype() == Datatype.SET_WORD
                    && !systemInternals.holds(word.canonical())) {
                systemInternals.define(word.spelling());
            }
        }
        return evaluator.evaluate(
                Binder.bind(body, systemInternals), systemInternals);
    }

    private static boolean startsWithARebolHeader(BlockValue values) {
        List<Value> items = values.remaining();
        return items.size() >= 2
                && items.get(0) instanceof WordValue opening
                && "rebol".equals(opening.canonical())
                && items.get(1) instanceof BlockValue;
    }

    /** Defines a body's assigned words in a context, then runs it there. */
    private Outcome loadInto(BlockValue body, Context target) {
        defineAssignedWordsIn(body, target);
        return evaluator.evaluate(Binder.bind(body, target), target);
    }

    /**
     * Loads a file whose header says {@code Type: module}.
     *
     * <p>The body runs in a context of its own, a child of the library so
     * that it still sees every standard function, and then only the words the
     * header exports are copied out. Everything else stays where it was put.
     *
     * <p>This is the whole point of the datatype. Rebol's JSON codec declares
     * {@code Type: module} and defines a parse rule named {@code exp} and
     * another named {@code stack}. Loaded into the library, those two replace
     * the library functions of the same spelling, and nothing reports it: the
     * word still answers, it just answers a block.
     */
    private Outcome loadAsAModule(BlockValue body, LibraryFileHeader header) {
        Context own = Context.childOf(systemContext);
        for (String exported : header.exportedNames()) {
            if (!own.holds(exported)) {
                own.define(exported);
            }
        }
        for (Value item : body.remaining()) {
            if (item instanceof WordValue word
                    && word.datatype() == Datatype.SET_WORD
                    && !own.holds(word.canonical())) {
                own.define(word.spelling());
            }
        }
        Outcome outcome = evaluator.evaluate(Binder.bind(body, own), own);
        if (outcome instanceof Outcome.Raised) {
            return outcome;
        }
        for (String exported : header.exportedNames()) {
            if (own.holds(exported)) {
                systemContext.set(exported, own.ownSlotFor(exported).value());
            }
        }
        registerTheModule(header, own);
        return outcome;
    }

    /**
     * Puts a loaded module in {@code system/modules} under its own name.
     *
     * <p>What LOAD-MODULE does when it has finished: {@code repend
     * system/modules [name module]}. In this fork that object starts out
     * holding a URL per external extension, and REPEND on an object writes
     * fields, so a loaded module takes the place of the address it would have
     * been fetched from.
     *
     * <p>IMPORT reads it back with {@code select system/modules name} and
     * answers the module it finds rather than loading anything, which is how
     * importing the same module twice costs nothing. Without this every IMPORT
     * of an already-booted module went looking for a file: {@code import
     * 'quoted-printable} in codec-mime-field.reb is the first one to try.
     */
    private void registerTheModule(LibraryFileHeader header, Context own) {
        String name = header.moduleName();
        if (name.isEmpty()
                || !(pathInto("system", "modules") instanceof ObjectValue modules)) {
            return;
        }
        Context spec = Context.root();
        spec.set("name", WordValue.of(name));
        spec.set("type", WordValue.of("module"));
        spec.set("exports", BlockValue.block(header.exportedNames().stream()
                .<Value>map(WordValue::of).toList()));
        modules.context().set(name, new ModuleValue(own, new ObjectValue(spec)));
    }

    /** A value read out of the system object by a path of field names. */
    private Value pathInto(String... names) {
        Value here = systemContext.knows(names[0])
                ? systemContext.slotFor(names[0]).value()
                : UnsetValue.unset();
        for (int at = 1; at < names.length; at++) {
            if (!(here instanceof ObjectValue object)
                    || !object.context().holds(names[at])) {
                return UnsetValue.unset();
            }
            here = object.context().ownSlotFor(names[at]).value();
        }
        return here;
    }

    /**
     * Publishes the user context as {@code system/contexts/user}.
     *
     * <p>{@code sysobj.reb} names four contexts and JEBOL published two of
     * them. INTERN, MODULE and IMPORT all reach for this one.
     */
    private void publishTheUserContext() {
        if (systemContext.knows("system")
                && systemContext.slotFor("system").value()
                        instanceof ObjectValue system
                && system.context().holds("contexts")
                && system.context().ownSlotFor("contexts").value()
                        instanceof ObjectValue contexts) {
            contexts.context().set("user", new ObjectValue(userContext));
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

    /** Where a file's new words go, as ORDER.txt says. */
    private static final String INTO_SYS = "-> sys";

    /**
     * Whether a file is one of the protocols, which are modules whatever
     * their headers say.
     *
     * <p>{@code sys-start.reb} line 187 is the whole reason: {@code foreach
     * [spec body] boot-prot [module spec body]}. R3 forces module semantics
     * on that group rather than reading each header, and its own files show
     * why -- {@code prot-tls.reb} declares {@code Yype: 'module}, a typo in
     * Rebol's own source, so header-based detection could never have worked.
     *
     * <p>Without this the group's words go to the library, and the damage is
     * not theoretical: {@code prot-tls.reb} ends with {@code log-error:
     * log-info: log-more: log-debug: log-----: none}, which is fine inside a
     * module of its own and wipes five working functions out of the library
     * from anywhere else.
     */
    private static boolean isAProtocol(String fileName) {
        return fileName.startsWith("prot-");
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

    /** The file name, without the target ORDER.txt may have written after it. */
    private static String fileNameIn(String entry) {
        int marker = entry.indexOf("->");
        return marker < 0 ? entry : entry.substring(0, marker).strip();
    }

    /**
     * A launcher script that starts this very interpreter, written once
     * per run and told to the natives as {@code system/options/boot}.
     *
     * <p>The C's boot is the running executable. JEBOL's executable is a
     * JVM plus a classpath, so the equivalent is one small script that
     * carries both. Written only for a host that granted the process
     * service, because CALL is the one thing that runs the field: without
     * that grant the script would be an unusable file in a shared
     * temporary directory. When it cannot be written, the field stays
     * none, which is the state Rebol's own boot files guard for.
     */
    private static String writtenBootLauncher() {
        String jvm = ProcessHandle.current().info().command()
                .orElse(System.getProperty("java.home", "") + "/bin/java");
        try {
            java.nio.file.Path launcher =
                    java.nio.file.Files.createTempFile("jebol-boot", ".sh");
            java.nio.file.Files.writeString(launcher, "#!/bin/sh\nexec \"" + jvm
                    + "\" -cp \"" + System.getProperty("java.class.path", "")
                    + "\" org.jebol.adapter.cli.Repl \"$@\"\n");
            if (!launcher.toFile().setExecutable(true)) {
                return "";
            }
            launcher.toFile().deleteOnExit();
            return launcher.toString().replace('\\', '/');
        } catch (IOException unwritable) {
            return "";
        }
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
        } catch (HaltRequested halted) {
            return new ScriptOutcome(
                    Conclusion.HALTED,
                    UnsetValue.unset(),
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
                return new Step(
                        conclude(new Outcome.Raised(raised.error()), startedAt),
                        Molder.moldOnly(bound.atIndex(2)));
            }
        } catch (QuitRequested quit) {
            return new Step(new ScriptOutcome(
                    Conclusion.QUIT_EARLY,
                    quit.answer(),
                    Duration.ofNanos(System.nanoTime() - startedAt)), "");
        } catch (HaltRequested halted) {
            return new Step(new ScriptOutcome(
                    Conclusion.HALTED,
                    UnsetValue.unset(),
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
     * Gives the script a screen to put a window on.
     *
     * <p>One grant for all five dialogs. A host that will show one will show
     * any of them, so a grant per dialog would say which verb and not which
     * screen.
     */
    public void useWindows(org.jebol.domain.eval.WindowPort port) {
        evaluator.useWindows(port);
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
