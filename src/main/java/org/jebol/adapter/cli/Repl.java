package org.jebol.adapter.cli;

import org.jebol.application.Bounds;
import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.IntegerValue;

import java.io.*;
import java.nio.charset.StandardCharsets;

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

    private final Interpreter interpreter;
    private final BufferedReader input;
    private final PrintStream output;

    public Repl(Interpreter interpreter, BufferedReader input, PrintStream output) {
        this.interpreter = interpreter;
        this.input = input;
        this.output = output;
    }

    public static void main(String[] arguments) {
        PrintStream out = System.out;
        Interpreter interpreter = anInterpreterFor(arguments, out);
        String[] rest = ChosenScreen.withoutTheSwitch(arguments);
        if (rest.length >= 2 && rest[0].equals("--do")) {
            interpreter.defineFreshWordsIn(rest[1]);
            System.exit(exitCodeOf(interpreter.run(rest[1])));
        }
        BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        new Repl(interpreter, in, out).run();
    }

    /**
     * An interpreter with whatever screen was asked for, and none otherwise.
     *
     * <p>The screen is the only thing this grants, and only when somebody said
     * so. A console session that never mentions graphics gets exactly what it
     * always got.
     */
    private static Interpreter anInterpreterFor(String[] arguments, PrintStream out) {
        if (!ChosenScreen.wasAskedFor(arguments)) {
            return Interpreter.writingTo(new StreamOutput(out));
        }
        Interpreter interpreter = Interpreter.writingTo(new StreamOutput(out),
                Bounds.standard().granting(HostService.WINDOWS));
        ChosenScreen.attachTo(interpreter, arguments, out);
        return interpreter;
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

            if (isIncomplete(source)) {
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

    /**
     * Whether the reader wants more input rather than having found a mistake.
     * Asked of the reader itself rather than guessed at by counting brackets,
     * because a brace inside a string is not an unclosed brace.
     */
    private boolean isIncomplete(String source) {
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
