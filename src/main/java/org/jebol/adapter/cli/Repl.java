package org.jebol.adapter.cli;

import org.jebol.adapter.host.JavaClipboard;
import org.jebol.adapter.host.JavaImages;
import org.jebol.adapter.host.JavaProcesses;
import org.jebol.adapter.host.JavaSockets;
import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.application.Bounds;
import org.jebol.application.Conclusion;
import org.jebol.application.FileSystemPort;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
    private static final String CONTINUATION_AROUND_THE_BRACKET = " ";

    private static final Path THE_WHOLE_MACHINE = Path.of("/");

    static final int KEEP_THE_PROCESS = -1;

    private static final int WHAT_A_HALT_REPORTS = -15;

    private static final int WHAT_AN_ERROR_REPORTS = -1;

    private static final int WHAT_A_FINISHED_START_REPORTS = 0;

    private static final Set<String> LEVELS_THAT_STOP_SHORT_OF_START = Set.of("base", "sys");

    private static final String SAYING_IT_IS_CLOSING_UNLESS_QUIET =
            "unless system/options/quiet [print {^[[mClosing in 3s!} wait 3]";

    static final String THE_ROOT_SWITCH = "--root";

    static final String THE_DATA_SWITCH = "--data";

    private static final String WHERE_REBOL_IS_TOLD_ITS_DATA_LIVES = "REBOL_HOME";

    private final Interpreter interpreter;
    private final BufferedReader input;
    private final PrintStream output;

    public Repl(Interpreter interpreter, BufferedReader input, PrintStream output) {
        this.interpreter = interpreter;
        this.input = input;
        this.output = output;
    }

    static void main(String[] arguments) {
        int status = runTheCommandLine(arguments, System.out, System.err,
                System.getProperty("user.dir", "."));
        if (status != KEEP_THE_PROCESS) {
            System.exit(status);
        }
    }

    static int runTheCommandLine(
            String[] arguments, PrintStream out, PrintStream errors, String startedIn) {

        return runTheCommandLine(arguments, new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8)), out, errors, startedIn);
    }

    static int runTheCommandLine(String[] arguments, BufferedReader typed,
            PrintStream out, PrintStream errors, String startedIn) {

        Path root = theRootAskedFor(arguments);
        RebolArguments asked = new RebolArguments(List.of(
                withoutTheSwitchesOnlyJebolTakes(ChosenScreen.withoutTheSwitch(arguments))));
        boolean anythingWasAsked = asked.script().isPresent() || !asked.flags().isEmpty();
        Interpreter interpreter = anInterpreterFor(arguments, out, anythingWasAsked, root);
        if (!anythingWasAsked) {
            new Repl(interpreter, typed, out).run();
            return KEEP_THE_PROCESS;
        }
        return new TheCommandLine(asked, startedIn, root)
                .runThrough(interpreter, typed, out, errors);
    }

    private static String[] withoutTheSwitchesOnlyJebolTakes(String[] arguments) {
        List<String> kept = new ArrayList<>();
        for (int at = 0; at < arguments.length; at++) {
            if (arguments[at].equals(THE_ROOT_SWITCH)
                    || arguments[at].equals(THE_DATA_SWITCH)) {
                at++;
                continue;
            }
            kept.add(arguments[at]);
        }
        return kept.toArray(String[]::new);
    }

    private static Path theRootAskedFor(String[] arguments) {
        String written = whatFollows(THE_ROOT_SWITCH, arguments);
        return written.isEmpty() ? THE_WHOLE_MACHINE : Path.of(written);
    }

    private static String whatFollows(String switchName, String[] arguments) {
        for (int at = 0; at + 1 < arguments.length; at++) {
            if (arguments[at].equals(switchName)) {
                return arguments[at + 1];
            }
        }
        return "";
    }

    private static Interpreter anInterpreterFor(
            String[] arguments, PrintStream out, boolean forAScript, Path root) {

        Bounds bounds = forAScript ? everyHostService() : Bounds.standard();
        if (ChosenScreen.wasAskedFor(arguments)) {
            bounds = bounds.granting(HostService.WINDOWS);
        }
        Interpreter interpreter =
                Interpreter.writingTo(new StreamOutput(out), bounds);
        giveItTheImageCodecWhichReachesNothingAndIsNotAGrant(interpreter);
        if (forAScript) {
            ProcessEnvironment environment = new ProcessEnvironment();
            interpreter.useFileSystem(FileSystemPort.rootedAt(root));
            interpreter.useEnvironment(environment);
            interpreter.useProcesses(new JavaProcesses());
            interpreter.useClipboard(new JavaClipboard());
            interpreter.useNetwork(new JavaSockets());
            putTheApplicationDataWhereTheParentKeepsIt(interpreter, environment, arguments);
        }
        if (ChosenScreen.wasAskedFor(arguments)) {
            ChosenScreen.attachTo(interpreter, arguments, out);
        }
        return interpreter;
    }

    private static void putTheApplicationDataWhereTheParentKeepsIt(
            Interpreter interpreter, ProcessEnvironment environment, String[] arguments) {

        String written = whatFollows(THE_DATA_SWITCH, arguments);
        if (written.isEmpty()) {
            return;
        }
        environment.nameHolds(WHERE_REBOL_IS_TOLD_ITS_DATA_LIVES, written);
        interpreter.tellTheSystem("data", StringValue.of(written, Datatype.FILE));
        interpreter.followTheApplicationDataDirectory();
    }

    private static void giveItTheImageCodecWhichReachesNothingAndIsNotAGrant(
            Interpreter interpreter) {

        interpreter.useImages(new JavaImages());
    }

    private static Bounds everyHostService() {
        Bounds everything = Bounds.standard();
        for (HostService service : HostService.values()) {
            everything = everything.granting(service);
        }
        return everything;
    }

    private record TheCommandLine(RebolArguments asked, String startedIn, Path root) {

        private int runThrough(Interpreter interpreter, BufferedReader typed,
                PrintStream out, PrintStream errors) {
            tellItWhatWasAsked(interpreter);
            int reported = WHAT_A_FINISHED_START_REPORTS;
            if (!theBootStopsShortOfStart()) {
                ScriptOutcome outcome = interpreter.started();
                if (outcome.conclusion() == Conclusion.QUIT_EARLY) {
                    return whatQuitCarried(outcome);
                }
                reported = whatStartReports(interpreter, outcome, errors);
            }
            if (asked.has(RebolArguments.Flag.CGI) || !theConsoleFollows(reported)) {
                return 0;
            }
            if (reported < 0 && !asked.has(RebolArguments.Flag.HALT)) {
                interpreter.run(SAYING_IT_IS_CLOSING_UNLESS_QUIET);
                return -reported;
            }
            new Repl(interpreter, typed, out).readEvaluatePrint();
            return KEEP_THE_PROCESS;
        }

        private boolean theBootStopsShortOfStart() {
            return asked.valueOf(RebolArguments.Flag.BOOT)
                    .map(level -> LEVELS_THAT_STOP_SHORT_OF_START.contains(level.toLowerCase(Locale.ROOT)))
                    .orElse(false);
        }

        private boolean theConsoleFollows(int reported) {
            return asked.script().isEmpty() || reported < 0 || asked.has(RebolArguments.Flag.HALT);
        }

        private int whatStartReports(
                Interpreter interpreter, ScriptOutcome outcome, PrintStream errors) {

            if (outcome.conclusion() == Conclusion.HALTED) {
                return WHAT_A_HALT_REPORTS;
            }
            if (threwPastEveryCatch(outcome) || outcome.succeeded()) {
                return WHAT_A_FINISHED_START_REPORTS;
            }
            errors.print(interpreter.whatTheConsolePrints(outcome));
            return WHAT_AN_ERROR_REPORTS;
        }

        private void tellItWhatWasAsked(Interpreter interpreter) {
            tellItWhereItStarted(interpreter);
            interpreter.tellTheSystem("flags", theFlagsGiven());
            if (asked.has(RebolArguments.Flag.QUIET)) {
                interpreter.tellTheSystem("quiet", LogicValue.yes());
            }
            if (asked.has(RebolArguments.Flag.NO_COLOR)) {
                interpreter.tellTheSystem("no-color", LogicValue.yes());
            }
            asked.script().ifPresent(script ->
                    interpreter.tellTheSystem("script", StringValue.of(script, Datatype.FILE)));
            asked.valueOf(RebolArguments.Flag.BOOT).ifPresent(level ->
                    interpreter.tellTheSystem("boot-level", WordValue.of(level)));
            interpreter.tellTheSystem("args", theArgumentsForTheScript());
            tellAsText(interpreter, "do-arg", RebolArguments.Flag.DO);
            tellAsText(interpreter, "debug", RebolArguments.Flag.DEBUG);
            tellAsText(interpreter, "version", RebolArguments.Flag.VERSION);
            tellAsText(interpreter, "import", RebolArguments.Flag.IMPORT);
            asked.valueOf(RebolArguments.Flag.SECURE).ifPresent(policy ->
                    interpreter.tellTheSystem("secure", WordValue.of(policy)));
        }

        private void tellAsText(Interpreter interpreter, String option, RebolArguments.Flag flag) {
            asked.valueOf(flag).ifPresent(text ->
                    interpreter.tellTheSystem(option, StringValue.of(text)));
        }

        private void tellItWhereItStarted(Interpreter interpreter) {
            interpreter.tellTheSystem("path", StringValue.of(
                    dirized(asTheScriptSeesIt(Path.of(startedIn))), Datatype.FILE));
            interpreter.run("change-dir system/options/path");
        }

        private BlockValue theArgumentsForTheScript() {
            List<Value> given = new ArrayList<>();
            asked.argumentsForTheScript().forEach(each -> given.add(StringValue.of(each)));
            return BlockValue.block(given);
        }

        private BlockValue theFlagsGiven() {
            List<Value> given = new ArrayList<>();
            asked.flags().forEach(flag -> given.add(WordValue.of(flag.spelling())));
            given.add(LogicValue.yes());
            return BlockValue.block(given);
        }

        private String asTheScriptSeesIt(Path host) {
            Path absolute = theRealPathOf(host);
            Path realRoot = theRealPathOf(root);
            if (!absolute.startsWith(realRoot)) {
                return theRootItself();
            }
            String inside = realRoot.relativize(absolute).toString().replace('\\', '/');
            return inside.isEmpty() ? theRootItself() : "/" + inside;
        }

        private Path theRealPathOf(Path host) {
            try {
                return host.toRealPath();
            } catch (IOException notThere) {
                return host.toAbsolutePath().normalize();
            }
        }

        private static String theRootItself() {
            return "/";
        }

        private static String dirized(String path) {
            return path.endsWith("/") ? path : path + "/";
        }
    }

    private static boolean threwPastEveryCatch(ScriptOutcome outcome) {
        return outcome.conclusion() == Conclusion.RAISED
                && outcome.value() instanceof ErrorValue error
                && error.category() == ErrorCategory.THROW;
    }

    private static int whatQuitCarried(ScriptOutcome outcome) {
        return outcome.value() instanceof IntegerValue(long magnitude) ? (int) magnitude : 0;
    }

    /** Runs until the input ends or the user asks to stop. */
    public void run() {
        output.println("JEBOL -- REBOL 3 on the JVM. Type quit to leave.");
        readEvaluatePrint();
    }

    private void readEvaluatePrint() {
        StringBuilder pending = new StringBuilder();
        ConsoleContinuation continuation = new ConsoleContinuation();

        while (true) {
            output.print(continuation.waitingForMore()
                    ? CONTINUATION_AROUND_THE_BRACKET + continuation.whatIsStillOpen()
                            + CONTINUATION_AROUND_THE_BRACKET
                    : PROMPT);
            output.flush();

            String line = readLine();
            if (line == null) {
                if (!continuation.waitingForMore()) {
                    return;
                }
                continuation.inputTaken();
                pending.setLength(0);
                continue;
            }
            String terminated = line + "\n";
            continuation.read(terminated);
            pending.append(terminated);
            if (continuation.waitingForMore()) {
                continue;
            }
            String source = pending.toString();
            pending.setLength(0);
            continuation.inputTaken();
            ScriptOutcome outcome = evaluated(source);
            if (outcome.conclusion() == Conclusion.QUIT_EARLY) {
                return;
            }
            output.print(interpreter.whatTheConsolePrints(outcome));
        }
    }

    private ScriptOutcome evaluated(String source) {
        interpreter.defineFreshWordsIn(source);
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
