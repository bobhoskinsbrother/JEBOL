package org.jebol.application;

import org.jebol.domain.eval.*;
import org.jebol.domain.host.*;
import org.jebol.domain.read.LibraryFile;
import org.jebol.domain.read.LibraryFileHeader;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.value.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

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

    private static final int CONSOLE_PRINT_MAX_LENGTH = 500;
    private static final int CONSOLE_PRINT_MAX_ERROR_LENGTH = 640;

    private static final String DECLARATION = "the-declaration";
    private static final String BOOT_MEZZ = "boot-mezz";
    private static final String RESULT_MARKER = "== ";
    private static final String INTO_SYS = "-> sys";

    private final RebolFiles rebolFiles = new RebolFiles(new ClasspathFiles());
    private final Context systemContext;
    private final Evaluator evaluator;
    private final Bounds bounds;
    private final MachinePort machine;
    private final Map<String, String> borrowedLoadFailures = new LinkedHashMap<>();
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();
    private volatile long deadlineNanos = Long.MAX_VALUE;
    private final Context systemInternals;
    private final List<ExtensionReport> extensionReports;

    private Context userContext;
    private boolean schemesAreRegistered;
    private String sharedRoot = "";

    private Interpreter(MachinePort machine, OutputPort output, Bounds bounds) {
        this.machine = refuseUnknownOperatingSystem(machine);
        Set<HostService> duringBoot = EnumSet.of(HostService.CLOCK, HostService.WINDOWS);
        duringBoot.addAll(bounds.grantedServices());
        Boot boot = Boot.makeStandard(machine, duringBoot);
        boot.useErrorCatalogue(rebolFiles.errors().textSource());
        boot.useDatatypeSpecs(rebolFiles.typespec().textSource());
        boot.useOperatorTable(rebolFiles.ops().textSource());
        boot.useModeTable(rebolFiles.modes().textSource());
        boot.useFunctionDeclarations(
                rebolFiles.actions().textSource(),
                rebolFiles.natives().textSource(),
                rebolFiles.genNatives().textSource());
        this.bounds = bounds;
        SystemObject system = boot.start();
        this.systemContext = system.lib();
        this.systemInternals = system.internals();
        this.userContext = Context.makeChildOf(systemContext);
        this.evaluator = new Evaluator(
                systemContext,
                boot.construction(),
                boot::makeMakerFor,
                output,
                bounds.maximumNesting(),
                this::findReasonToStop,
                bounds.checkEvery());
        evaluator.usePause(new ThreadPause());
        evaluator.useMachine(machine);
        evaluator.putRuntimeWordsIn(userContext);
        evaluator.symbols().useBootSymbols(rebolFiles.bootSymbolsTxt().textSource());
        evaluator.useBundledModules(this::readBundledModule);
        declareRebolSequentially();
        publishUserContext();
        putAddressesOfModulesRebolPublishes();
        loadRebolsOwnLibrary();
        nameLauncherOnceLibraryLoads();
        registerSchemesRebolDeclares();
        boot.grantOnly(bounds.grantedServices());
        boot.markOnlyUnavailable(bounds.unavailableServices());
        boot.useHostName(bounds.hostName());
        system.forgetStartupState();
        SealedLibrary library = new SealedLibrary(systemContext);
        library.seal();
        ExtensionInstallation installation = new ExtensionInstallation(evaluator, library);
        this.extensionReports = bounds.extensions().stream().map(installation::installAndReport).toList();
        evaluator.symbols().startCountingWhatSessionReads();
    }


    public static Interpreter create() {
        return new Interpreter(new JavaMachine(), OutputPort.discard(), Bounds.standard());
    }

    public static Interpreter withBounds(Bounds bounds) {
        return new Interpreter(new JavaMachine(), OutputPort.discard(), bounds);
    }

    public static Interpreter createWithOutput(OutputPort output) {
        return new Interpreter(new JavaMachine(), output, Bounds.standard());
    }

    public static Interpreter createWithOutput(OutputPort output, Bounds bounds) {
        return new Interpreter(new JavaMachine(), output, bounds);
    }

    public static Interpreter createOn(MachinePort machine, OutputPort output, Bounds bounds) {
        return new Interpreter(machine, output, bounds);
    }

    public Bounds bounds() {
        return bounds;
    }

    public Context userContext() {
        return userContext;
    }

    public List<ExtensionReport> extensions() {
        return extensionReports;
    }

    public ScriptOutcome run(String source) {
        return concludeWithinBounds(() -> evaluate(source));
    }

    public void tellSystem(String option, Value value) {
        evaluator.setSystemOption(option, value);
    }

    public ScriptOutcome start() {
        runBootStep(rebolFiles.handOverToStart());
        leaveBannerForStartToBuild();
        ScriptOutcome outcome =
                concludeWithinBounds(() -> evaluator.applyAsHostApplies("start"));
        adoptUserContextStartMade();
        return outcome;
    }

    private MachinePort refuseUnknownOperatingSystem(MachinePort machine) {
        if (machine == null || machine.operatingSystem().isBlank()) {
            throw new IllegalArgumentException("Unknown operating system");
        }
        return machine;
    }

    private void nameLauncherOnceLibraryLoads() {
        if (!bounds.grantedServices().contains(HostService.PROCESSES)) {
            return;
        }
        pointBootAt(machine.launcherFor("/", ""));
    }

    private void putAddressesOfModulesRebolPublishes() {
        runBootStep(rebolFiles.modules());
    }

    private void declareRebolSequentially() {
        AnyBlockValue body = rebolFiles.sysobj().readLibraryFile().body();
        Context whereDeclarationsOwnWordsLand = Context.makeChildOf(systemContext);
        declareSetWordsOf(body, whereDeclarationsOwnWordsLand, AssignmentMayLand.HERE_ONLY_SHADOWING_WHATEVER_IS_ABOVE);
        AnyBlockValue step = rebolFiles.systemObject().readLibraryFile().body();
        Context whereStepsOwnWordsLand = Context.makeChildOf(systemContext);
        whereStepsOwnWordsLand.register(DECLARATION, withoutFileLineBreaks(
                Binder.bind(body, whereDeclarationsOwnWordsLand)));
        declareSetWordsOf(step, whereStepsOwnWordsLand, AssignmentMayLand.HERE_OR_IN_WHATEVER_IS_ABOVE);
        Outcome outcome = evaluator.evaluate(Binder.bind(step, whereStepsOwnWordsLand), whereStepsOwnWordsLand);
        if (outcome instanceof Outcome.Raised(ErrorValue failure)) {
            throw new IllegalStateException( "the system object would not build from Rebol's declaration: " + failure);
        }
    }

    private AnyBlockValue withoutFileLineBreaks(AnyBlockValue block) {
        for (int at = block.index(); at <= block.storageLength(); at++) {
            block.storage().setLineBreakAt(at, false);
        }
        for (Value each : block.remaining()) {
            if (each instanceof AnyBlockValue nested) {
                withoutFileLineBreaks(nested);
            }
        }
        return block;
    }

    private void registerSchemesRebolDeclares() {
        if (schemesAreRegistered || !systemInternals.knows("make-scheme")) {
            return;
        }
        schemesAreRegistered = true;
        runBootStep(rebolFiles.schemes());
    }

    private void runBootStep(RebolFile step) {
        run(step.textSource());
    }

    private AnyBlockValue readPrelude() {
        return rebolFiles.prelude().readLibraryFile().body();
    }

    private void declareWordsPreludeSets() {
        declareSetWordsOf(readPrelude(), systemContext,
                AssignmentMayLand.HERE_OR_IN_WHATEVER_IS_ABOVE);
    }

    private void loadPrelude() {
        AnyBlockValue body = readPrelude();
        Outcome outcome = evaluator.evaluate(Binder.bind(body, systemContext), systemContext);
        if (outcome instanceof Outcome.Raised(ErrorValue failure)) {
            throw new IllegalStateException("the prelude failed to load: " + failure);
        }
    }

    private void loadRebolsOwnLibrary() {
        declareEverySystemWordBeforeBindingAny();
        declareEveryLibraryWordBeforeBindingAny();
        declareWordsPreludeSets();
        for (String entry : borrowedFileNames()) {
            String name = readFileNameIn(entry);
            RebolFile borrowed = rebolFiles.mezzDir(name);
            if (borrowed.equals(rebolFiles.protMysql())) {
                registerSchemesRebolDeclares();
            }
            if (!borrowed.exists()) {
                continue;
            }
            TranscodeResult read = borrowed.readTranscoded();
            if (read.values().isEmpty()) {
                borrowedLoadFailures.put(name, read.error().orElseThrow().toString());
                continue;
            }
            LibraryFile file = LibraryFile.readFrom(read.values().orElseThrow());
            LibraryFileHeader header = file.header();
            AnyBlockValue body = file.body();

            Outcome outcome = header.declaresModule()
                    || isProtocolAndSoModuleWhateverItsHeaderSays(name)
                    ? loadAsModule(body, header)
                    : entry.endsWith(INTO_SYS)
                            ? loadAsSystemFile(body)
                            : loadInto(body, systemContext);
            if (outcome instanceof Outcome.Raised(ErrorValue failure)) {
                borrowedLoadFailures.put(name, failure.toString());
            }
            if (borrowed.equals(rebolFiles.baseFuncs())) {
                loadPrelude();
            }
        }
        describeQoiCodec();
    }

    private void describeQoiCodec() {
        runBootStep(rebolFiles.qoiCodec());
    }

    private Outcome loadAsSystemFile(AnyBlockValue body) {
        declareSetWordsOf(body, systemInternals,
                AssignmentMayLand.HERE_ONLY_SHADOWING_WHATEVER_IS_ABOVE);
        return evaluator.evaluate(
                Binder.bind(body, systemInternals), systemInternals);
    }

    private void declareEveryLibraryWordBeforeBindingAny() {
        for (String entry : borrowedFileNames()) {
            String name = readFileNameIn(entry);
            if (entry.endsWith(INTO_SYS) || isProtocolAndSoModuleWhateverItsHeaderSays(name)) {
                continue;
            }
            RebolFile borrowed = rebolFiles.mezzDir(name);
            if (borrowed.readsAsLibraryFile() && !borrowed.readLibraryFile().header().declaresModule()) {
                declareSetWordsOf(borrowed.readLibraryFile().body(), systemContext,
                        AssignmentMayLand.HERE_OR_IN_WHATEVER_IS_ABOVE);
            }
        }
    }

    private void declareEverySystemWordBeforeBindingAny() {
        for (String entry : borrowedFileNames()) {
            if (!entry.endsWith(INTO_SYS)) {
                continue;
            }
            RebolFile borrowed = rebolFiles.mezzDir(readFileNameIn(entry));
            if (borrowed.readsAsLibraryFile()) {
                declareSetWordsOf(borrowed.readLibraryFile().body(), systemInternals,
                        AssignmentMayLand.HERE_ONLY_SHADOWING_WHATEVER_IS_ABOVE);
            }
        }
    }

    private enum AssignmentMayLand {
        HERE_ONLY_SHADOWING_WHATEVER_IS_ABOVE,
        HERE_OR_IN_WHATEVER_IS_ABOVE
    }

    private void declareSetWordsOf(
            AnyBlockValue body, Context into, AssignmentMayLand mayLand) {

        for (AnyWordValue word : body.setWordsFromHere()) {
            if (!isAlreadyAnswered(into, word.canonical(), mayLand)) {
                into.register(word.spelling());
            }
        }
    }

    private boolean isAlreadyAnswered(
            Context into, String canonical, AssignmentMayLand mayLand) {

        return mayLand == AssignmentMayLand.HERE_ONLY_SHADOWING_WHATEVER_IS_ABOVE
                ? into.holds(canonical)
                : into.knows(canonical);
    }

    private Outcome loadInto(AnyBlockValue body, Context target) {
        declareSetWordsOf(body, target,
                AssignmentMayLand.HERE_OR_IN_WHATEVER_IS_ABOVE);
        return evaluator.evaluate(Binder.bind(body, target), target);
    }

    private Outcome loadAsModule(AnyBlockValue body, LibraryFileHeader header) {
        Context own = Context.makeChildOf(systemContext);
        for (String exported : header.exportedNames()) {
            if (!own.holds(exported)) {
                own.register(exported);
            }
        }
        declareSetWordsOf(body, own,
                AssignmentMayLand.HERE_ONLY_SHADOWING_WHATEVER_IS_ABOVE);
        Outcome outcome = evaluator.evaluate(Binder.bind(body, own), own);
        if (outcome instanceof Outcome.Raised) {
            return outcome;
        }
        for (String exported : header.exportedNames()) {
            if (own.holds(exported)) {
                systemContext.register(exported, own.ownSlotFor(exported).value());
            }
        }
        registerModule(header, own);
        return outcome;
    }

    private void registerModule(LibraryFileHeader header, Context own) {
        String name = header.moduleName();
        if (name.isEmpty()
                || !(followPathInto("system", "modules") instanceof ObjectValue(Context context))) {
            return;
        }
        Context spec = Context.root();
        spec.register("name", WordValue.of(name));
        spec.register("type", WordValue.of("module"));
        spec.register("exports", BlockValue.of(header.exportedNames().stream()
                .<Value>map(WordValue::of).toList()));
        context.register(name, new ModuleValue(own, new ObjectValue(spec)));
    }

    private Value followPathInto(String... names) {
        Value here = systemContext.knows(names[0])
                ? systemContext.findSlotFor(names[0]).value()
                : UnsetValue.unset();
        for (int at = 1; at < names.length; at++) {
            if (!(here instanceof ObjectValue(Context context))
                    || !context.holds(names[at])) {
                return UnsetValue.unset();
            }
            here = context.ownSlotFor(names[at]).value();
        }
        return here;
    }

    private void publishUserContext() {
        if (systemContext.knows("system")
                && systemContext.findSlotFor("system").value()
                        instanceof ObjectValue system
                && system.context().holds("contexts")
                && system.context().ownSlotFor("contexts").value() instanceof ObjectValue(Context context)) {
            ObjectValue user = new ObjectValue(userContext);
            userContext.pointItsOwnSelfAt(user);
            context.register("user", user);
            openUserContextWithRebolAndItself(system, user);
        }
    }

    private void openUserContextWithRebolAndItself(ObjectValue system, ObjectValue user) {
        userContext.register("REBOL", system);
        userContext.register("lib-local", user);
    }

    public Map<String, String> borrowedLoadFailures() {
        return Map.copyOf(borrowedLoadFailures);
    }

    private boolean isProtocolAndSoModuleWhateverItsHeaderSays(
            String fileName) {
        return fileName.startsWith("prot-");
    }

    private List<String> borrowedFileNames() {
        return TextLines.of(rebolFiles.orderTxt().textSource()).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }

    private String readFileNameIn(String entry) {
        int marker = entry.indexOf("->");
        return marker < 0 ? entry : entry.substring(0, marker).strip();
    }

    private Optional<byte[]> readBundledModule(String name) {
        RebolFile module = rebolFiles.modulesDir(name);
        return module.exists() ? Optional.of(module.readBytes()) : Optional.empty();
    }


    private void adoptUserContextStartMade() {
        if (followPathInto("system", "contexts", "user") instanceof ObjectValue(Context made)
                && made != userContext) {
            userContext = made;
        }
    }

    private void giveItUserSlotHoldingWhatLibHolds(AnyWordValue word, Context into) {
        into.register(word.spelling(), readWhatLibHolds(word, into));
    }

    private Value readWhatLibHolds(AnyWordValue word, Context into) {
        if (into.knows(word.canonical())) {
            return into.findSlotFor(word.canonical()).value();
        }
        return systemContext.knows(word.canonical())
                ? systemContext.findSlotFor(word.canonical()).value()
                : UnsetValue.unset();
    }

    private void leaveBannerForStartToBuild() {
        if (!(systemInternals.readValueAt(BOOT_MEZZ) instanceof AnyBlockValue protecting)) {
            return;
        }
        RebolFile banner = rebolFiles.mezzBanner();
        if (!banner.readsAsLibraryFile()) {
            return;
        }
        List<Value> runFirst = new ArrayList<>(banner.readLibraryFile().body().remaining());
        runFirst.addAll(protecting.remaining());
        systemInternals.register(BOOT_MEZZ, BlockValue.of(runFirst));
    }

    private ScriptOutcome concludeWithinBounds(Supplier<Outcome> evaluating) {
        cancellationRequested.set(false);
        evaluator.screen().acceptPendingReports();
        long startedAt = System.nanoTime();
        deadlineNanos = bounds.computeDeadline(startedAt);
        try {
            return conclude(evaluating.get(), startedAt);
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
                    concludeFrom(stopped),
                    reportStoppedRun(stopped),
                    Duration.ofNanos(System.nanoTime() - startedAt));
        } finally {
            deadlineNanos = Long.MAX_VALUE;
            evaluator.screen().offerShown();
        }
    }

    public void cancel() {
        cancellationRequested.set(true);
    }

    public record Step(ScriptOutcome outcome, String rest) {
    }

    public Step runNext(String source) {
        cancellationRequested.set(false);
        evaluator.screen().acceptPendingReports();
        long startedAt = System.nanoTime();
        deadlineNanos = bounds.computeDeadline(startedAt);
        try {
            TranscodeResult read = evaluator.read(source);
            if (!read.succeeded()) {
                return new Step(
                        conclude(new Outcome.Raised(read.error().orElseThrow()), startedAt),
                        "");
            }
            AnyBlockValue values = read.values().orElseThrow();
            if (values.atTail()) {
                return new Step(conclude(
                        new Outcome.Completed(UnsetValue.unset()), startedAt), "");
            }
            defineWordsIn(values);
            AnyBlockValue bound = Binder.bind(values, userContext);
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
                    concludeFrom(stopped),
                    reportStoppedRun(stopped),
                    Duration.ofNanos(System.nanoTime() - startedAt)), "");
        } finally {
            deadlineNanos = Long.MAX_VALUE;
            evaluator.screen().offerShown();
        }
    }

    private Outcome evaluate(String source) {
        TranscodeResult read = evaluator.read(source);
        if (!read.succeeded()) {
            return new Outcome.Raised(read.error().orElseThrow());
        }
        defineWordsIn(read.values().orElseThrow());
        AnyBlockValue bound = Binder.bind(read.values().orElseThrow(), userContext);
        return evaluator.evaluate(bound, userContext);
    }

    private ScriptOutcome conclude(Outcome outcome, long startedAt) {
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        return switch (outcome) {
            case Outcome.Completed completed ->
                    new ScriptOutcome(Conclusion.PRODUCED_VALUE, completed.result(), elapsed);
            case Outcome.Raised raised ->
                    new ScriptOutcome(Conclusion.RAISED,
                            evaluator.speakHere(raised.failure()), elapsed);
        };
    }

    private Optional<String> findReasonToStop() {
        if (System.nanoTime() >= deadlineNanos) {
            return Optional.of("the script ran longer than "
                    + bounds.timeLimit().toMillis() + "ms");
        }
        if (cancellationRequested.get()) {
            return Optional.of("the host asked the script to stop");
        }
        return Optional.empty();
    }

    private Conclusion concludeFrom(Stopped stopped) {
        return stopped.reason().startsWith("the host")
                ? Conclusion.CANCELLED
                : Conclusion.TIMED_OUT;
    }

    private ErrorValue reportStoppedRun(Stopped stopped) {
        return evaluator.speakHere(ErrorValue.of(ErrorCategory.ACCESS, identify(stopped),
                stopped.reason(), StringValue.of(stopped.reason())));
    }

    private String identify(Stopped stopped) {
        return concludeFrom(stopped) == Conclusion.CANCELLED ? "cancelled" : "timeout";
    }

    public void defineFreshWordsIn(String source) {
        TranscodeResult read = evaluator.read(source);
        read.values().ifPresent(this::defineWordsIn);
    }

    private void defineWordsIn(AnyBlockValue block) {
        defineWordsIn(block, userContext);
    }

    private void defineWordsIn(AnyBlockValue block, Context into) {
        for (Value item : block.remaining()) {
            switch (item) {
                case AnyWordValue word -> {
                    if (!into.answersAlreadyTo(word.canonical())) {
                        giveItUserSlotHoldingWhatLibHolds(word, into);
                    }
                }
                case AnyBlockValue nested -> defineWordsIn(nested, into);
                default -> {
                }
            }
        }
    }

    public void define(String name, Object supplied) {
        userContext.register(name, HostValues.fromHost(supplied));
    }

    public void defineNull(String name, Class<?> type) {
        userContext.register(name, JavaObjectValue.holdHostNull(type.getName()));
    }

    public void useNetwork(NetworkPort port) {
        evaluator.useNetwork(port);
    }

    public void useCertificateAuthority(CertificateAuthority authority) {
        evaluator.useCertificateAuthority(authority);
    }

    public void useHttp(HttpPort port) {
        evaluator.useHttp(port);
    }

    public void useClipboard(ClipboardPort port) {
        evaluator.useClipboard(port);
    }

    public void useMidiDevices(MidiDevices devices) {
        evaluator.useMidiDevices(devices);
    }

    public void useProcesses(ProcessPort port) {
        evaluator.useProcesses(port);
    }

    public void useConsole(ConsolePort port) {
        evaluator.useConsole(port);
    }

    public void useTasks(TaskPort port) {
        evaluator.useTasks(port);
    }

    public void useWindows(WindowPort port) {
        evaluator.useWindows(port);
    }

    public void useScreen(ScreenPort port) {
        evaluator.useScreen(port);
        handOverRootGobTo(port);
        handOverDrawDialectTo(port);
    }

    public void usePause(PausePort port) {
        evaluator.usePause(port);
    }

    private void handOverDrawDialectTo(ScreenPort port) {
        Value declared = followPathInto("system", "dialects", "draw");
        if (!(declared instanceof UnsetValue)) {
            port.useDrawDialect(declared);
        }
    }

    private void handOverRootGobTo(ScreenPort port) {
        if (followPathInto("system", "view", "screen-gob") instanceof GobValue root) {
            port.takeAsRoot(root);
        }
    }

    public void useEnvironment(EnvironmentPort port) {
        evaluator.useEnvironment(port);
    }

    public void useImages(ImagePort port) {
        evaluator.useImages(port);
    }

    public void useFileSystem(FilePort port) {
        evaluator.useFiles(port);
        noteRootChildInterpretersMustShare(port);
        followApplicationDataDirectory();
    }

    public void followApplicationDataDirectory() {
        runBootStep(rebolFiles.modulesDirectory());
        confineAnyInterpreterThisOneStarts();
    }

    private void noteRootChildInterpretersMustShare(FilePort port) {
        if (!bounds.grantedServices().contains(HostService.PROCESSES)) {
            return;
        }
        try {
            sharedRoot = port.hostPath("/");
        } catch (RuntimeException noRoot) {
            sharedRoot = "";
        }
    }

    private void confineAnyInterpreterThisOneStarts() {
        if (sharedRoot.isEmpty()) {
            return;
        }
        Value launcher = machine.launcherFor(sharedRoot, findDataDirectory());
        if (launcher instanceof FileValue file) {
            evaluator.files().acceptFileFromOutsideRoot(file.text());
        }
        pointBootAt(launcher);
    }

    private void pointBootAt(Value launcher) {
        if (followPathInto("system", "options") instanceof ObjectValue(Context options)) {
            options.register("boot", launcher);
        }
    }

    private String findDataDirectory() {
        return followPathInto("system", "options", "data") instanceof AnyStringValue written
                ? written.text()
                : "";
    }

    public String writeWhatConsolePrints(ScriptOutcome outcome) {
        Value result = outcome.value();
        evaluator.setSystemState("last-result", result);
        if (outcome.conclusion() == Conclusion.HALTED || result instanceof UnsetValue) {
            return "";
        }
        if (result instanceof ErrorValue error) {
            return cutShortAt(CONSOLE_PRINT_MAX_ERROR_LENGTH, error.formAsRebolDoes()) + "\n";
        }
        if (!showsResultsOfType(result.datatype())) {
            return "";
        }
        return RESULT_MARKER + cutShortAt(CONSOLE_PRINT_MAX_LENGTH, Molder.mold(result)) + "\n\n";
    }

    private boolean showsResultsOfType(Datatype type) {
        return followPathInto("system", "options", "result-types") instanceof TypesetValue shown
                && shown.holds(type);
    }

    private String cutShortAt(int longest, String written) {
        if (written.codePointCount(0, written.length()) <= longest) {
            return written;
        }
        return written.substring(0, written.offsetByCodePoints(0, longest)) + "...";
    }

    public TranscodeResult read(String source) {
        return evaluator.read(source);
    }

    public String display(ScriptOutcome outcome) {
        if (outcome.succeeded() && outcome.value() instanceof UnsetValue) {
            return "";
        }
        return outcome.display();
    }

    public Value produceNothing() {
        return UnsetValue.unset();
    }

    public String show(Value value) {
        return Molder.mold(value);
    }
}
