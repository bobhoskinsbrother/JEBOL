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
import java.util.concurrent.ConcurrentHashMap;
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

    private final Map<String, String> borrowedLoadFailures = new LinkedHashMap<>();

    private final AtomicBoolean cancellationRequested = new AtomicBoolean();
    private volatile long deadlineNanos = Long.MAX_VALUE;

    private final Context systemInternals;

    private Interpreter(OutputPort output, Bounds bounds) {
        Set<HostService> duringTheBoot =
                EnumSet.of(HostService.CLOCK, HostService.WINDOWS);
        duringTheBoot.addAll(bounds.grantedServices());
        Natives natives = Natives.standard(duringTheBoot);
        natives.useFileSeparator(java.io.File.separatorChar);
        natives.useOperatingSystemNamed(whatRebolCallsThisOperatingSystem());
        if (bounds.grantedServices().contains(HostService.PROCESSES)) {
            natives.useBootLauncher(writtenBootLauncher());
        }
        String catalogue = resourceText("/org/jebol/errors.reb");
        natives.useErrorCatalogue(catalogue == null ? "" : catalogue);
        natives.useFunctionDeclarations(
                declarationsIn("/org/jebol/actions.reb"),
                declarationsIn("/org/jebol/natives.reb"),
                declarationsIn("/org/jebol/gen-natives.reb"));
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
        evaluator.useBundledModules(Interpreter::theModuleBundledAs);
        loadPrelude();
        putTheAddressesOfTheModulesRebolPublishes();
        loadRebolsOwnLibrary();
        registerTheSchemesJebolCanServe();
        openTheEventPort();
        natives.grantOnly(bounds.grantedServices());
        natives.forgetStartupState();
    }


    private void putTheAddressesOfTheModulesRebolPublishes() {
        runTheBootStep("modules.reb");
    }



    private boolean theSchemesAreRegistered;

    private void registerTheSchemesJebolCanServe() {
        if (theSchemesAreRegistered || !systemInternals.knows("make-scheme")) {
            return;
        }
        theSchemesAreRegistered = true;
        for (String scheme : new String[] {
                "schemes.reb",
                "scheme-system.reb",
                "scheme-file.reb",
                "scheme-dir.reb",
                "scheme-checksum.reb",
                "scheme-crypt.reb"}) {
            runTheBootStep(scheme);
        }
    }

    private void runTheBootStep(String name) {
        run(bootStepNamed(name));
    }

    /** One boot step as it was written, for a caller that has to fill it in. */
    public static String bootStepNamed(String name) {
        String source = resourceText(BOOT + name);
        if (source == null) {
            throw new IllegalStateException(name + " is missing from the build");
        }
        return source;
    }

    private static final String BOOT = "/org/jebol/boot/";

    private void openTheEventPort() {
        if (!systemInternals.knows("make-scheme")) {
            return;
        }
        runTheBootStep("ports.reb");
    }

    private void openTheSystemPort() {
        run("unless port? system/ports/system "
                + "[system/ports/system: lib/open [scheme: 'system]]");
    }

    private void openTheOutputPort() {
        run("unless port? system/ports/output "
                + "[system/ports/output: lib/open [scheme: 'console]]");
    }

    private static final String
            THE_BORROWED_FILE_THAT_FORCES_THE_SCHEMES_TO_BE_REGISTERED_MIDWAY =
            "view-funcs.reb";

    private static final String PRELUDE = "/org/jebol/prelude.reb";

    private void loadPrelude() {
        String source = resourceText(PRELUDE);
        if (source == null) {
            throw new IllegalStateException("the prelude is missing from the build");
        }
        TranscodeResult read = LibrarySource.reading(PRELUDE, source);
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

    private static final String MEZZANINE = "/org/jebol/mezz/";

    private void loadRebolsOwnLibrary() {
        declareEverySystemWordBeforeBindingAny();
        for (String entry : borrowedFileNames()) {
            String name = fileNameIn(entry);
            if (name.equals(
                    THE_BORROWED_FILE_THAT_FORCES_THE_SCHEMES_TO_BE_REGISTERED_MIDWAY)) {
                registerTheSchemesJebolCanServe();
                openTheEventPort();
            }
            String source = resourceText(MEZZANINE + name);
            if (source == null) {
                continue;
            }
            TranscodeResult read = LibrarySource.reading(MEZZANINE + name, source);
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

            Outcome outcome = header.declaresAModule()
                    || isAProtocolAndSoAModuleWhateverItsHeaderSays(name)
                    ? loadAsAModule(body, header)
                    : entry.endsWith(INTO_SYS)
                            ? loadAsASystemFile(body)
                            : loadInto(body, systemContext);
            if (outcome instanceof Outcome.Raised raised) {
                borrowedLoadFailures.put(name, raised.failure().toString());
            }
        }
        describeTheQoiCodec();
    }

    private void describeTheQoiCodec() {
        runTheBootStep("qoi-codec.reb");
    }

    private Outcome loadAsASystemFile(BlockValue body) {
        declareTheSetWordsOf(body);
        return evaluator.evaluate(
                Binder.bind(body, systemInternals), systemInternals);
    }

    private void declareEverySystemWordBeforeBindingAny() {
        for (String entry : borrowedFileNames()) {
            if (!entry.endsWith(INTO_SYS)) {
                continue;
            }
            String name = fileNameIn(entry);
            String source = resourceText(MEZZANINE + name);
            if (source == null) {
                continue;
            }
            TranscodeResult read = LibrarySource.reading(MEZZANINE + name, source);
            if (read.values().isEmpty()) {
                continue;
            }
            BlockValue values = read.values().orElseThrow();
            declareTheSetWordsOf(
                    startsWithARebolHeader(values) ? values.atIndex(3) : values);
        }
    }

    private void declareTheSetWordsOf(BlockValue body) {
        for (Value item : body.remaining()) {
            if (item instanceof WordValue word
                    && word.datatype() == Datatype.SET_WORD
                    && !systemInternals.holds(word.canonical())) {
                systemInternals.define(word.spelling());
            }
        }
    }

    private static boolean startsWithARebolHeader(BlockValue values) {
        List<Value> items = values.remaining();
        return items.size() >= 2
                && items.get(0) instanceof WordValue opening
                && "rebol".equals(opening.canonical())
                && items.get(1) instanceof BlockValue;
    }

    private Outcome loadInto(BlockValue body, Context target) {
        defineAssignedWordsIn(body, target);
        return evaluator.evaluate(Binder.bind(body, target), target);
    }

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

    private void publishTheUserContext() {
        if (systemContext.knows("system")
                && systemContext.slotFor("system").value()
                        instanceof ObjectValue system
                && system.context().holds("contexts")
                && system.context().ownSlotFor("contexts").value()
                        instanceof ObjectValue contexts) {
            contexts.context().set("user", new ObjectValue(userContext));
            openTheUserContextWithRebolAndItself(system);
        }
    }

    private void openTheUserContextWithRebolAndItself(ObjectValue system) {
        userContext.set("REBOL", system);
        userContext.set("lib-local", new ObjectValue(userContext));
    }

    /**
     * Which borrowed files stopped partway, and on what, keyed by file name in
     * the order ORDER.txt lists them.
     */
    public Map<String, String> borrowedLoadFailures() {
        return Map.copyOf(borrowedLoadFailures);
    }

    private static final String INTO_SYS = "-> sys";

    private static boolean isAProtocolAndSoAModuleWhateverItsHeaderSays(
            String fileName) {
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

    private static String fileNameIn(String entry) {
        int marker = entry.indexOf("->");
        return marker < 0 ? entry : entry.substring(0, marker).strip();
    }

    private static String whatRebolCallsThisOperatingSystem() {
        String reported = System.getProperty("os.name", "");
        if (reported.startsWith("Windows")) {
            return "Windows";
        }
        if (reported.startsWith("Mac")) {
            return "macOS";
        }
        if (reported.startsWith("Linux")) {
            return "Linux";
        }
        return reported.isEmpty() ? "JVM" : reported;
    }

    private static String aClasspathMadeAbsoluteSoItWorksFromAnyDirectory() {
        return java.util.Arrays.stream(
                        System.getProperty("java.class.path", "")
                                .split(java.io.File.pathSeparator))
                .filter(entry -> !entry.isEmpty())
                .map(entry -> java.nio.file.Path.of(entry).toAbsolutePath().toString())
                .collect(java.util.stream.Collectors.joining(
                        java.io.File.pathSeparator));
    }

    private static String writtenBootLauncher() {
        return writtenBootLauncher("/", "");
    }

    private static String writtenBootLauncher(String hostRoot, String dataDirectory) {
        String jvm = ProcessHandle.current().info().command()
                .orElse(System.getProperty("java.home", "") + "/bin/java");
        try {
            java.nio.file.Path launcher =
                    java.nio.file.Files.createTempFile("jebol-boot", ".sh");
            java.nio.file.Files.writeString(launcher, "#!/bin/sh\nexec \"" + jvm
                    + "\" -cp \"" + aClasspathMadeAbsoluteSoItWorksFromAnyDirectory()
                    + "\" org.jebol.adapter.cli.Repl --root \"" + hostRoot + "\""
                    + whereTheApplicationDataIsSwitch(dataDirectory)
                    + " \"$@\"\n");
            if (!launcher.toFile().setExecutable(true)) {
                return "";
            }
            launcher.toFile().deleteOnExit();
            return launcher.toString().replace('\\', '/');
        } catch (IOException unwritable) {
            return "";
        }
    }

    private static final String THE_DATA_SWITCH = "--data";

    private static String whereTheApplicationDataIsSwitch(String dataDirectory) {
        return dataDirectory.isEmpty()
                ? ""
                : " " + THE_DATA_SWITCH + " \"" + dataDirectory + "\"";
    }

    private static String declarationsIn(String path) {
        String source = resourceText(path);
        return source == null ? "" : source;
    }

    private static Optional<byte[]> theModuleBundledAs(String name) {
        return resourceBytes(MODULES + name);
    }

    private static final String MODULES = "/org/jebol/modules/";

    private static Optional<byte[]> resourceBytes(String path) {
        byte[] held = RESOURCES.computeIfAbsent(path, Interpreter::readingTheResource);
        return held.length == 0 && !RESOURCES_THAT_ARE_THERE.contains(path)
                ? Optional.empty()
                : Optional.of(aCopySoNoCallerCanWriteIntoTheCache(held));
    }

    private static byte[] aCopySoNoCallerCanWriteIntoTheCache(byte[] held) {
        return held.clone();
    }

    private static final Map<String, byte[]> RESOURCES = new ConcurrentHashMap<>();

    private static final Set<String> RESOURCES_THAT_ARE_THERE =
            ConcurrentHashMap.newKeySet();

    private static byte[] readingTheResource(String path) {
        try (InputStream reading = Interpreter.class.getResourceAsStream(path)) {
            if (reading == null) {
                return new byte[0];
            }
            RESOURCES_THAT_ARE_THERE.add(path);
            return reading.readAllBytes();
        } catch (IOException unreadable) {
            return new byte[0];
        }
    }

    private static String resourceText(String path) {
        return resourceBytes(path)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .orElse(null);
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

    private Value runHostFunction(
            String name, HostFunction function, List<Value> arguments) {

        if (!bounds.hostAccess().allowsCalling()) {
            throw new Raised(ErrorValue.of(
                    ErrorCategory.ACCESS, "host-access",
                    "this interpreter may not call out to " + name));
        }
        List<Object> supplied = arguments.stream().map(HostValues::toHost).toList();
        try {
            return HostValues.fromHost(function.call(supplied));
        } catch (RuntimeException | Error thrown) {
            throw new Raised(ErrorValue.of(
                    ErrorCategory.USER, "host-error",
                    name + " failed: " + thrown.getMessage()));
        }
    }

    /** Tells this interpreter where its script's network may reach. */
    public void useNetwork(NetworkPort port) {
        evaluator.useNetwork(port);
    }

    /** Gives the script a way to start another program. */
    public void useProcesses(ProcessPort port) {
        evaluator.useProcesses(port);
    }

    /** Gives the script a console to read a line from. Writing goes elsewhere. */
    public void useConsole(ConsolePort port) {
        evaluator.useConsole(port);
    }

    /** Gives the script a screen to put a window on: one grant for all five dialogs. */
    public void useWindows(WindowPort port) {
        evaluator.useWindows(port);
    }

    /**
     * Gives the script a screen to draw a gob tree on. Behind the same grant as
     * the five dialogs, and a separate port because it holds windows open and
     * sends events back rather than asking one question.
     */
    public void useScreen(ScreenPort port) {
        evaluator.useScreen(port);
        handOverTheRootGobTo(port);
        handOverTheDrawDialectTo(port);
    }

    private void handOverTheDrawDialectTo(ScreenPort port) {
        Value declared = pathInto("system", "dialects", "draw");
        if (!(declared instanceof UnsetValue)) {
            port.useDrawDialect(declared);
        }
    }

    private void handOverTheRootGobTo(ScreenPort port) {
        if (pathInto("system", "view", "screen-gob") instanceof GobValue root) {
            ScreenPort.takeAsTheRoot(port, root);
        }
    }

    /** Gives the script the host's environment. */
    public void useEnvironment(EnvironmentPort port) {
        evaluator.useEnvironment(port);
    }

    /**
     * Gives the script the host's image codec. Until this is called, IMAGE/LOAD
     * and IMAGE/SAVE refuse, and so do the png, jpeg, gif and bmp entries in
     * {@code system/codecs}.
     */
    public void useImages(ImagePort port) {
        evaluator.useImages(port);
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
        noteTheRootAnInterpreterThisOneStartsMustShare(port);
        followTheApplicationDataDirectory();
    }

    /**
     * Settles everything that hangs off {@code system/options/data}: the
     * modules directory beside it, which is made, and what an interpreter this
     * one starts is told about where to find both. Call it again whenever a
     * host moves the data directory.
     */
    public void followTheApplicationDataDirectory() {
        runTheBootStep("modules-directory.reb");
        confineAnyInterpreterThisOneStarts();
    }

    private String theRootAnyChildMustShare = "";

    private void noteTheRootAnInterpreterThisOneStartsMustShare(FilePort port) {
        if (!bounds.grantedServices().contains(HostService.PROCESSES)) {
            return;
        }
        try {
            theRootAnyChildMustShare = port.hostPathOf("/");
        } catch (RuntimeException noRoot) {
            theRootAnyChildMustShare = "";
        }
    }

    private void confineAnyInterpreterThisOneStarts() {
        if (theRootAnyChildMustShare.isEmpty()) {
            return;
        }
        String launcher = writtenBootLauncher(
                theRootAnyChildMustShare, theDataDirectoryThisScriptSees());
        if (launcher.isEmpty()) {
            return;
        }
        String saying = "system/options/boot: %" + launcher;
        defineFreshWordsIn(saying);
        run(saying);
    }

    private String theDataDirectoryThisScriptSees() {
        return pathInto("system", "options", "data") instanceof StringValue written
                ? written.text()
                : "";
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
