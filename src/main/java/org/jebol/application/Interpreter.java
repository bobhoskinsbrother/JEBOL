package org.jebol.application;

import org.jebol.domain.eval.*;
import org.jebol.domain.host.*;
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

    private static final String RESULT_MARKER = "== ";

    private final Context systemContext;
    private final SystemPaths systemPaths;
    private final Evaluator evaluator;
    private final RebolLibraryLoader library;
    private final Bounds bounds;
    private final MachinePort machine;
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();
    private volatile long deadlineNanos = Long.MAX_VALUE;
    private final List<ExtensionReport> extensionReports;

    private Context userContext;
    private String sharedRoot = "";

    private Interpreter(MachinePort machine, OutputPort output, Bounds bounds) {
        this.machine = refuseUnknownOperatingSystem(machine);
        Set<HostService> duringBoot = EnumSet.of(HostService.CLOCK, HostService.WINDOWS);
        duringBoot.addAll(bounds.grantedServices());
        RebolFiles rebolFiles = new RebolFiles(new ClasspathFiles());
        Boot boot = Boot.makeStandard(machine, rebolFiles, duringBoot);
        this.bounds = bounds;
        SystemObject system = boot.start();
        this.systemContext = system.lib();
        this.systemPaths = new SystemPaths(systemContext);
        this.userContext = Context.makeChildOf(systemContext);
        this.evaluator = new Evaluator(
                systemContext,
                boot.construction(),
                boot::makeMakerFor,
                output,
                bounds.maximumNesting(),
                this::reasonToStop,
                bounds.checkEvery());
        evaluator.usePause(new ThreadPause());
        evaluator.useMachine(machine);
        evaluator.putRuntimeWordsIn(userContext);
        this.library = new RebolLibraryLoader(rebolFiles, systemContext, system.internals(), this::run);
        library.supplySymbolsAndModules(evaluator);
        library.declareSystemObject(evaluator);
        publishUserContext();
        library.load(evaluator);
        declareLauncherAfterLibraryLoads();
        library.registerSchemes();
        boot.grantOnly(bounds.grantedServices());
        boot.markOnlyUnavailable(bounds.unavailableServices());
        boot.useHostName(bounds.hostName());
        system.forgetStartupState();
        SealedLibrary library = new SealedLibrary(systemContext);
        library.seal();
        ExtensionInstallation installation = new ExtensionInstallation(evaluator, library);
        this.extensionReports = bounds.extensions().stream().map(installation::installAndReport).toList();
        evaluator.symbols().startCountingSessionReads();
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

    public void setSystemValue(String option, Value value) {
        evaluator.setSystemOption(option, value);
    }

    public ScriptOutcome start() {
        library.handOverToStart();
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

    private void declareLauncherAfterLibraryLoads() {
        if (!bounds.grantedServices().contains(HostService.PROCESSES)) {
            return;
        }
        pointBootAt(machine.launcherFor("/", ""));
    }

    private void publishUserContext() {
        if (systemContext.contains("system")
                && systemContext.findSlotFor("system").value()
                        instanceof ObjectValue system
                && system.context().holds("contexts")
                && system.context().ownSlotFor("contexts").value() instanceof ObjectValue(Context context)) {
            ObjectValue user = new ObjectValue(userContext);
            userContext.pointItsOwnSelfAt(user);
            context.register("user", user);
            registerContextWithRebolAndLibLocal(system, user);
        }
    }

    private void registerContextWithRebolAndLibLocal(ObjectValue system, ObjectValue user) {
        userContext.register("REBOL", system);
        userContext.register("lib-local", user);
    }

    public Map<String, String> borrowedLoadFailures() {
        return library.borrowedLoadFailures();
    }

    private void adoptUserContextStartMade() {
        if (systemPaths.follow("system", "contexts", "user") instanceof ObjectValue(Context made)
                && made != userContext) {
            userContext = made;
        }
    }

    private void giveItUserSlotHoldingWhatLibHolds(AnyWordValue word, Context context) {
        context.register(word.spelling(), libValueFromContext(word, context));
    }

    private Value libValueFromContext(AnyWordValue word, Context context) {
        if (context.contains(word.canonical())) {
            return context.findSlotFor(word.canonical()).value();
        }
        return systemContext.contains(word.canonical())
                ? systemContext.findSlotFor(word.canonical()).value()
                : UnsetValue.unset();
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

    private Optional<String> reasonToStop() {
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

    public void defineNewWordsIn(String source) {
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
        Value declared = systemPaths.follow("system", "dialects", "draw");
        if (!(declared instanceof UnsetValue)) {
            port.useDrawDialect(declared);
        }
    }

    private void handOverRootGobTo(ScreenPort port) {
        if (systemPaths.follow("system", "view", "screen-gob") instanceof GobValue root) {
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
        library.placeModulesDirectory();
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
        if (systemPaths.follow("system", "options") instanceof ObjectValue(Context options)) {
            options.register("boot", launcher);
        }
    }

    private String findDataDirectory() {
        return systemPaths.follow("system", "options", "data") instanceof AnyStringValue written
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
        return systemPaths.follow("system", "options", "result-types") instanceof TypesetValue shown
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

    public String show(Value value) {
        return Molder.mold(value);
    }
}
