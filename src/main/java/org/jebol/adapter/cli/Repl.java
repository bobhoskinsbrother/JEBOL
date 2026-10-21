package org.jebol.adapter.cli;

import org.jebol.adapter.host.JavaClipboard;
import org.jebol.adapter.host.JavaImage;
import org.jebol.adapter.host.JavaProcess;
import org.jebol.adapter.host.JavaCertificateAuthority;
import org.jebol.adapter.host.JavaHost;
import org.jebol.adapter.host.JavaSocket;
import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.adapter.host.ThreadTasks;
import org.jebol.application.Bounds;
import org.jebol.application.Conclusion;
import org.jebol.adapter.host.FileSystemPort;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.UrlValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The console: read a line, evaluate it, print the result, repeat.
 *
 * <p>An adapter, not part of the language. Two conveniences live here and
 * deliberately nowhere else: a word nobody has defined gets a slot before
 * evaluation, and an error ends the expression rather than the session.
 * Putting either in the evaluator would make a block behave differently
 * depending on whether anyone was watching.
 */
public final class Repl {

    private static final String PROMPT = ">> ";
    private static final String CONTINUATION_AROUND_BRACKET = " ";

    private static final Path WHOLE_MACHINE = Path.of("/");

    static final int KEEP_PROCESS = -1;

    private static final int REPORT_HALTED = -15;

    private static final int REPORT_ERROR = -1;

    private static final int REPORT_PRODUCED_VALUE = 0;

    private static final int REPORT_MISUSED_COMMAND_LINE = 2;

    private static final String MAIN_REBOL_FILE = "main.reb";

    private static final Set<String> LEVELS_THAT_STOP_SHORT_OF_START = Set.of("base", "sys");

    private static final String REBOL_CLOSE_PRINT_COMMAND =
            "unless system/options/quiet [print {^[[mClosing in 3s!} wait 3]";

    static final String ROOT_SWITCH = "--root";

    static final String DATA_SWITCH = "--data";

    static final String CAFILE_SWITCH = "--cafile";

    private static final String HTTPS_PREFIX = "https://";

    private static final String REBOL_HOME = "REBOL_HOME";

    private final Interpreter interpreter;
    private final BufferedReader input;
    private final PrintStream output;

    public Repl(Interpreter interpreter, BufferedReader input, PrintStream output) {
        this.interpreter = interpreter;
        this.input = input;
        this.output = output;
    }

    static void main(String[] arguments) {
        int status;
        try {
            status = runCommandLine(arguments, System.out, System.err,
                    System.getProperty("user.dir", "."));
        } catch (IllegalArgumentException refused) {
            System.err.println(refused.getMessage());
            status = REPORT_MISUSED_COMMAND_LINE;
        }
        System.exit(status == KEEP_PROCESS ? REPORT_PRODUCED_VALUE : status);
    }

    static int runCommandLine(
            String[] arguments, PrintStream out, PrintStream errors, String startedIn) {

        return runCommandLine(arguments, new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8)), out, errors, startedIn);
    }

    static int runCommandLine(String[] arguments, BufferedReader typed,
                              PrintStream out, PrintStream errors, String startedIn) {

        ChosenScreen.setHeadlessBasedOnWhetherScreenIsNeeded(arguments);

        Path root = readRoot(arguments);
        RebolArguments asked = new RebolArguments(List.of(
                argumentsWithoutJebolOnlySwitches(ChosenScreen.withoutSwitch(arguments))));
        asked.script().ifPresent(script -> asked.nameScript(findAppsEntry(script)));
        boolean anythingWasAsked = asked.script().isPresent() || !asked.flags().isEmpty();
        Interpreter interpreter = buildInterpreter(arguments, out, anythingWasAsked, root);
        return new CommandLine(asked, startedIn, root)
                .runThrough(interpreter, typed, out, errors);
    }

    private static String findAppsEntry(String whereItPoints) {
        if (whereItPoints.isEmpty() || isAnHttpsUrl(whereItPoints)) {
            return whereItPoints;
        }
        Path asked = Path.of(whereItPoints);
        if (!Files.isDirectory(asked)) {
            return whereItPoints;
        }
        Path entry = asked.resolve(MAIN_REBOL_FILE);
        if (!Files.isRegularFile(entry)) {
            throw new IllegalArgumentException(whereItPoints
                    + " is a directory, and an app is run from the "
                    + MAIN_REBOL_FILE + " inside it, which is not there");
        }
        return entry.toString();
    }

    private static String[] argumentsWithoutJebolOnlySwitches(String[] arguments) {
        List<String> reply = new ArrayList<>();
        for (int at = 0; at < arguments.length; at++) {
            if (arguments[at].equals(ROOT_SWITCH) || arguments[at].equals(DATA_SWITCH)
                    || arguments[at].equals(CAFILE_SWITCH)) {
                at++;
                continue;
            }
            reply.add(arguments[at]);
        }
        return reply.toArray(String[]::new);
    }

    private static Path readRoot(String[] arguments) {
        String trailingArgument = readTrailingArgument(ROOT_SWITCH, arguments);
        return trailingArgument.isEmpty() ? WHOLE_MACHINE : Path.of(trailingArgument);
    }

    private static boolean isAnHttpsUrl(String written) {
        return written.toLowerCase(Locale.ROOT).startsWith(HTTPS_PREFIX);
    }

    private static String readTrailingArgument(String switchName, String[] arguments) {
        for (int at = 0; at + 1 < arguments.length; at++) {
            if (arguments[at].equals(switchName)) {
                return arguments[at + 1];
            }
        }
        return "";
    }

    private static Interpreter buildInterpreter(String[] arguments, PrintStream out, boolean anythingWasAsked, Path root) {
        Bounds bounds = grantEveryHostService().withoutTimeLimit();
        ProcessEnvironment environment = new ProcessEnvironment();
        String cafile = readTrailingArgument(CAFILE_SWITCH, arguments);
        HostPorts ports = useJavaImagesCodecWhichReachesNothingAndIsNotAGrant(new JavaHost().ports()
                .withTasks(new ThreadTasks(bounds))
                .withFiles(FileSystemPort.rootFileSystem(root))
                .withEnvironment(environment)
                .withProcesses(new JavaProcess())
                .withClipboard(new JavaClipboard())
                .withNetwork(new JavaSocket())
                .withCertificateAuthority(cafile.isEmpty()
                        ? new JavaCertificateAuthority()
                        : JavaCertificateAuthority.trustingMachineAnd(Path.of(cafile))));
        if (ChosenScreen.isAskedFor(arguments)) {
            ports = ports.withScreen(ChosenScreen.screenFor(
                    arguments, out, anythingWasAsked, ports.carriedFiles()));
        }
        Interpreter interpreter = Interpreter.createOn(ports, new StreamOutput(out), bounds);
        applyDataSwitch(interpreter, environment, arguments);
        return interpreter;
    }

    private static void applyDataSwitch(
            Interpreter interpreter, ProcessEnvironment environment, String[] arguments) {

        String written = readTrailingArgument(DATA_SWITCH, arguments);
        if (written.isEmpty()) {
            return;
        }
        environment.assign(REBOL_HOME, written);
        interpreter.setSystemValue("data", FileValue.of(written));
        interpreter.followApplicationDataDirectory();
    }

    private static HostPorts useJavaImagesCodecWhichReachesNothingAndIsNotAGrant(HostPorts ports) {
        return ports.withImages(new JavaImage());
    }

    private static Bounds grantEveryHostService() {
        Bounds everything = Bounds.standard();
        for (HostService service : HostService.values()) {
            everything = everything.grant(service);
        }
        return everything;
    }

    private record CommandLine(RebolArguments arguments, String startedIn, Path root) {

        private int runThrough(Interpreter interpreter, BufferedReader typed, PrintStream out, PrintStream errors) {
            tellItWhatWasAsked(interpreter);
            int reported = REPORT_PRODUCED_VALUE;
            if (!bootStopsShortOfStart()) {
                ScriptOutcome outcome = interpreter.start();
                if (outcome.conclusion() == Conclusion.QUIT_EARLY) {
                    return readWhatQuitCarries(outcome);
                }
                reported = readStartReport(interpreter, outcome, errors);
            }
            if (arguments.has(RebolArguments.Flag.CGI) || !followsWithConsole(reported)) {
                return 0;
            }
            if (reported < 0 && !arguments.has(RebolArguments.Flag.HALT)) {
                interpreter.run(REBOL_CLOSE_PRINT_COMMAND);
                return -reported;
            }
            new Repl(interpreter, typed, out).run();
            return KEEP_PROCESS;
        }

        private boolean bootStopsShortOfStart() {
            return arguments.valueOf(RebolArguments.Flag.BOOT)
                    .map(level -> LEVELS_THAT_STOP_SHORT_OF_START.contains(level.toLowerCase(Locale.ROOT)))
                    .orElse(false);
        }

        private boolean followsWithConsole(int reported) {
            return arguments.script().isEmpty() || reported < 0 || arguments.has(RebolArguments.Flag.HALT);
        }

        private int readStartReport(Interpreter interpreter, ScriptOutcome outcome, PrintStream errors) {

            if (outcome.conclusion() == Conclusion.HALTED) {
                return REPORT_HALTED;
            }
            if (escapesEveryCatch(outcome) || outcome.succeeded()) {
                return REPORT_PRODUCED_VALUE;
            }
            errors.print(interpreter.responseBasedOnOutcome(outcome));
            return REPORT_ERROR;
        }

        private void tellItWhatWasAsked(Interpreter interpreter) {
            tellItWhereItStarted(interpreter);
            interpreter.setSystemValue("flags", listFlagsGiven());
            if (arguments.has(RebolArguments.Flag.QUIET)) {
                interpreter.setSystemValue("quiet", LogicValue.yes());
            }
            if (arguments.has(RebolArguments.Flag.NO_COLOR)) {
                interpreter.setSystemValue("no-color", LogicValue.yes());
            }
            arguments.script().ifPresent(script ->
                    interpreter.setSystemValue("script", isAnHttpsUrl(script)
                            ? UrlValue.of(script)
                            : FileValue.of(script)));
            arguments.valueOf(RebolArguments.Flag.BOOT).ifPresent(level ->
                    interpreter.setSystemValue("boot-level", WordValue.of(level)));
            interpreter.setSystemValue("args", listArgumentsForScript());
            tellAsText(interpreter, "do-arg", RebolArguments.Flag.DO);
            tellAsText(interpreter, "debug", RebolArguments.Flag.DEBUG);
            tellAsText(interpreter, "version", RebolArguments.Flag.VERSION);
            tellAsText(interpreter, "import", RebolArguments.Flag.IMPORT);
            arguments.valueOf(RebolArguments.Flag.SECURE).ifPresent(policy ->
                    interpreter.setSystemValue("secure", WordValue.of(policy)));
        }

        private void tellAsText(Interpreter interpreter, String option, RebolArguments.Flag flag) {
            arguments.valueOf(flag).ifPresent(text ->
                    interpreter.setSystemValue(option, StringValue.of(text)));
        }

        private void tellItWhereItStarted(Interpreter interpreter) {
            interpreter.setSystemValue("path", FileValue.of(
                    dirize(asScriptSeesIt(Path.of(startedIn)))));
            interpreter.run("change-dir system/options/path");
        }

        private AnyBlockValue listArgumentsForScript() {
            List<Value> given = new ArrayList<>();
            arguments.listTheArgumentsForTheScript().forEach(each -> given.add(StringValue.of(each)));
            return BlockValue.of(given);
        }

        private AnyBlockValue listFlagsGiven() {
            List<Value> given = new ArrayList<>();
            arguments.flags().forEach(flag -> given.add(WordValue.of(flag.spelling())));
            given.add(LogicValue.yes());
            return BlockValue.of(given);
        }

        private String asScriptSeesIt(Path host) {
            Path absolute = realPath(host);
            Path realRoot = realPath(root);
            if (!absolute.startsWith(realRoot)) {
                return nameRoot();
            }
            String inside = realRoot.relativize(absolute).toString().replace('\\', '/');
            return inside.isEmpty() ? nameRoot() : "/" + inside;
        }

        private Path realPath(Path host) {
            try {
                return host.toRealPath();
            } catch (IOException notThere) {
                return host.toAbsolutePath().normalize();
            }
        }

        private static String nameRoot() {
            return "/";
        }

        private static String dirize(String path) {
            return path.endsWith("/") ? path : path + "/";
        }
    }

    private static boolean escapesEveryCatch(ScriptOutcome outcome) {
        return outcome.conclusion() == Conclusion.RAISED
                && outcome.value() instanceof ErrorValue error
                && error.category() == ErrorCategory.THROW;
    }

    private static int readWhatQuitCarries(ScriptOutcome outcome) {
        return outcome.value() instanceof IntegerValue(long magnitude) ? (int) magnitude : 0;
    }

    /** Runs until the input ends or the user asks to stop. */
    public void run() {
        StringBuilder pending = new StringBuilder();
        ConsoleContinuation continuation = new ConsoleContinuation();

        while (true) {
            output.print(continuation.isWaitingForMore()
                    ? CONTINUATION_AROUND_BRACKET + continuation.findWhatIsStillOpen()
                            + CONTINUATION_AROUND_BRACKET
                    : PROMPT);
            output.flush();

            String line = readLine();
            if (line == null) {
                if (!continuation.isWaitingForMore()) {
                    return;
                }
                continuation.takeInput();
                pending.setLength(0);
                continue;
            }
            String terminated = line + "\n";
            continuation.read(terminated);
            pending.append(terminated);
            if (continuation.isWaitingForMore()) {
                continue;
            }
            String source = pending.toString();
            pending.setLength(0);
            continuation.takeInput();
            ScriptOutcome outcome = evaluate(source);
            if (outcome.conclusion() == Conclusion.QUIT_EARLY) {
                return;
            }
            output.print(interpreter.responseBasedOnOutcome(outcome));
        }
    }

    private ScriptOutcome evaluate(String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.run(source);
    }

    private String readLine() {
        try {
            return input.readLine();
        } catch (IOException problem) {
            throw new UncheckedIOException("cannot read from the console", problem);
        }
    }
}
