package org.jebol.adapter.cli;

import org.jebol.adapter.host.JavaImages;
import org.jebol.adapter.host.JavaProcesses;
import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.application.Bounds;
import org.jebol.application.Conclusion;
import org.jebol.application.FileSystemPort;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.IntegerValue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    private static final String CONTINUATION = "   ";

    private static final String SECURITY_SWITCH_THERE_IS_NOTHING_HERE_TO_TURN_OFF = "-s";
    private static final String EVALUATE_THIS_AND_LEAVE = "--do";
    private static final Path THE_WHOLE_MACHINE = Path.of("/");
    private static final String A_BYTE_ORDER_MARK = "﻿";

    static final int KEEP_THE_PROCESS = -1;

    static final String THE_ROOT_SWITCH = "--root";

    private final Interpreter interpreter;
    private final BufferedReader input;
    private final PrintStream output;

    public Repl(Interpreter interpreter, BufferedReader input, PrintStream output) {
        this.interpreter = interpreter;
        this.input = input;
        this.output = output;
    }

    public static void main(String[] arguments) {
        int status = runTheCommandLine(arguments, System.out,
                System.getProperty("user.dir", "."));
        if (status != KEEP_THE_PROCESS) {
            System.exit(status);
        }
    }

    static int runTheCommandLine(
            String[] arguments, PrintStream out, String startedIn) {

        Path root = theRootAskedFor(arguments);
        String[] rest = withoutTheSwitchesThatSayNothing(
                ChosenScreen.withoutTheSwitch(arguments));
        boolean namesAScript = rest.length >= 1 && !rest[0].startsWith("-");
        Interpreter interpreter = anInterpreterFor(arguments, out, namesAScript, root);
        if (rest.length >= 2 && rest[0].equals(EVALUATE_THIS_AND_LEAVE)) {
            interpreter.defineFreshWordsIn(rest[1]);
            return exitCodeOf(interpreter.run(rest[1]));
        }
        if (namesAScript) {
            return new ScriptOnTheCommandLine(rest, startedIn, root)
                    .runThrough(interpreter, out);
        }
        BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        new Repl(interpreter, in, out).run();
        return KEEP_THE_PROCESS;
    }

    private static String[] withoutTheSwitchesThatSayNothing(String[] arguments) {
        List<String> kept = new ArrayList<>();
        for (int at = 0; at < arguments.length; at++) {
            if (arguments[at].equals(SECURITY_SWITCH_THERE_IS_NOTHING_HERE_TO_TURN_OFF)) {
                continue;
            }
            if (arguments[at].equals(THE_ROOT_SWITCH)) {
                at++;
                continue;
            }
            kept.add(arguments[at]);
        }
        return kept.toArray(String[]::new);
    }

    private static Path theRootAskedFor(String[] arguments) {
        for (int at = 0; at + 1 < arguments.length; at++) {
            if (arguments[at].equals(THE_ROOT_SWITCH)) {
                return Path.of(arguments[at + 1]);
            }
        }
        return THE_WHOLE_MACHINE;
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
            interpreter.useFileSystem(FileSystemPort.rootedAt(root));
            interpreter.useEnvironment(new ProcessEnvironment());
            interpreter.useProcesses(new JavaProcesses());
        }
        if (ChosenScreen.wasAskedFor(arguments)) {
            ChosenScreen.attachTo(interpreter, arguments, out);
        }
        return interpreter;
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

    private record ScriptOnTheCommandLine(
            String[] arguments, String startedIn, Path root) {

        private int runThrough(Interpreter interpreter, PrintStream out) {
            Path script = theScriptNamed();
            String source;
            try {
                source = withoutAnyByteOrderMark(Files.readAllBytes(script));
            } catch (IOException unreadable) {
                out.println("** access error: script not found: %" + script);
                return 1;
            }
            tellItWhereItIs(interpreter, script);
            interpreter.defineFreshWordsIn(source);
            ScriptOutcome outcome = interpreter.run(source);
            if (!outcome.succeeded() && outcome.conclusion() != Conclusion.QUIT_EARLY) {
                out.println(outcome.display());
            }
            return exitCodeOf(outcome);
        }

        private void tellItWhereItIs(Interpreter interpreter, Path script) {
            String saying = Interpreter.bootStepNamed("script-position.reb").formatted(
                    asTheScriptSeesIt(script),
                    dirized(asTheScriptSeesIt(Path.of(startedIn))),
                    theArgumentsAfterTheScript(),
                    dirized(asTheScriptSeesIt(script.getParent())));
            interpreter.defineFreshWordsIn(saying);
            interpreter.run(saying);
        }

        private String asTheScriptSeesIt(Path host) {
            Path absolute = host.toAbsolutePath().normalize();
            if (!absolute.startsWith(root)) {
                return theRootItself();
            }
            String inside = root.relativize(absolute).toString().replace('\\', '/');
            return inside.isEmpty() ? theRootItself() : "/" + inside;
        }

        private static String theRootItself() {
            return "/";
        }

        private static String withoutAnyByteOrderMark(byte[] bytes) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            return text.startsWith(A_BYTE_ORDER_MARK) ? text.substring(1) : text;
        }

        private Path theScriptNamed() {
            Path written = Path.of(arguments[0]);
            Path resolved = written.isAbsolute()
                    ? root.resolve(THE_WHOLE_MACHINE.relativize(written))
                    : written;
            return resolved.toAbsolutePath().normalize();
        }

        private static String dirized(String path) {
            return path.endsWith("/") ? path : path + "/";
        }

        private String theArgumentsAfterTheScript() {
            StringBuilder written = new StringBuilder("[");
            for (int at = 1; at < arguments.length; at++) {
                written.append('{').append(arguments[at]).append('}');
            }
            return written.append(']').toString();
        }
    }

    private static int exitCodeOf(ScriptOutcome outcome) {
        if (outcome.conclusion() == Conclusion.QUIT_EARLY) {
            return outcome.value() instanceof IntegerValue whole
                    ? (int) whole.magnitude()
                    : 0;
        }
        return outcome.succeeded() ? 0 : 1;
    }

    /** Runs until the input ends or the user asks to stop. */
    public void run() {
        output.println("JEBOL -- REBOL 3 on the JVM. Type quit to leave.");
        StringBuilder pending = new StringBuilder();

        while (true) {
            output.print(pending.isEmpty() ? PROMPT : CONTINUATION);
            output.flush();

            String line = readLine();
            if (line == null) {
                output.println();
                return;
            }
            if (pending.isEmpty() && isQuit(line)) {
                return;
            }

            pending.append(line).append('\n');
            String source = pending.toString();

            if (theReaderWantsMoreRatherThanHavingFoundAMistake(source)) {
                continue;
            }
            pending.setLength(0);
            show(source);
        }
    }

    private void show(String source) {
        interpreter.defineFreshWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        String displayed = interpreter.display(outcome);
        if (!displayed.isEmpty()) {
            output.println(outcome.succeeded() ? "== " + displayed : displayed);
        }
    }

    private boolean theReaderWantsMoreRatherThanHavingFoundAMistake(String source) {
        var read = interpreter.read(source);
        if (read.succeeded()) {
            return false;
        }
        return read.error()
                .map(error -> error.errorId().equals("missing-close")
                        || error.errorId().equals("unterminated-string"))
                .orElse(false);
    }

    private static boolean isQuit(String line) {
        String trimmed = line.trim();
        return trimmed.equals("quit") || trimmed.equals("q");
    }

    private String readLine() {
        try {
            return input.readLine();
        } catch (IOException problem) {
            throw new UncheckedIOException("cannot read from the console", problem);
        }
    }
}
