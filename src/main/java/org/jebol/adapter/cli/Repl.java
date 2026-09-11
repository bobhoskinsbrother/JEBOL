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

    /**
     * What the command line asked for, and the number the process should leave
     * with -- or {@link #KEEP_THE_PROCESS} where it opened a console instead.
     *
     * <p>Separated from {@code main} so the argument handling can be driven
     * without ending the test's own process, which is the only thing
     * {@code main} adds.
     */
    static int runTheCommandLine(
            String[] arguments, PrintStream out, String startedIn) {

        Path root = theRootAskedFor(arguments);
        String[] rest = withoutTheSwitchesThatSayNothing(
                ChosenScreen.withoutTheSwitch(arguments));
        boolean namesAScript = rest.length >= 1 && !rest[0].startsWith("-");
        Interpreter interpreter = anInterpreterFor(arguments, out, namesAScript, root);
        if (rest.length >= 2 && rest[0].equals("--do")) {
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

    /** What {@link #runTheCommandLine} answers when it ran a console. */
    static final int KEEP_THE_PROCESS = -1;

    /**
     * The switches this build reads and does nothing about.
     *
     * <p>{@code -s} turns Rebol's security off, and there is none here to turn
     * off: a script run from the command line reaches the machine either way.
     * Accepting it rather than reading it as a script path is what lets a
     * command written for a real Rebol run here unchanged, which is how
     * Rebol's own lexer test starts a second interpreter.
     */
    private static String[] withoutTheSwitchesThatSayNothing(String[] arguments) {
        List<String> kept = new ArrayList<>();
        for (int at = 0; at < arguments.length; at++) {
            if (arguments[at].equals("-s")) {
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

    /** What confines an interpreter this one starts, and this one when started. */
    static final String THE_ROOT_SWITCH = "--root";

    /**
     * The directory this run may reach, or the whole machine when none was
     * named.
     *
     * <p>An interpreter that was confined hands this to any interpreter it
     * starts, so the second is bounded the way the first is. Without it a
     * script given one directory could start a copy of itself that had the
     * machine, and confinement a script can step out of by running its own
     * name is not confinement.
     */
    private static Path theRootAskedFor(String[] arguments) {
        for (int at = 0; at + 1 < arguments.length; at++) {
            if (arguments[at].equals(THE_ROOT_SWITCH)) {
                return Path.of(arguments[at + 1]);
            }
        }
        return Path.of("/");
    }

    /**
     * An interpreter with whatever screen was asked for, and none otherwise.
     *
     * <p>The screen is the only thing this grants, and only when somebody said
     * so. A console session that never mentions graphics gets exactly what it
     * always got.
     *
     * <p>The image codec is not a grant and is always there. It reaches no
     * file, no window and no network: bytes go in and pixels come out, and the
     * reading of a file is READ's business and asks for READ's grant. Holding
     * it back would empty {@code system/codecs} instead, because Rebol's own
     * codec-image.reb writes png, jpeg, gif and bmp as calls to it.
     */
    private static Interpreter anInterpreterFor(
            String[] arguments, PrintStream out, boolean forAScript, Path root) {

        Bounds bounds = forAScript ? theWholeMachine() : Bounds.standard();
        if (ChosenScreen.wasAskedFor(arguments)) {
            bounds = bounds.granting(HostService.WINDOWS);
        }
        Interpreter interpreter =
                Interpreter.writingTo(new StreamOutput(out), bounds);
        interpreter.useImages(new JavaImages());
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

    /**
     * Every host service, which is what a script named on the command line
     * gets.
     *
     * <p>A person typing a script's name means what they mean typing it at a
     * real Rebol: the script may read their files and start their programs,
     * because they chose to run it. Confinement is for the other way in -- a
     * host embedding the interpreter and handing it somebody else's script --
     * and that host builds its own bounds and is granted nothing by default.
     *
     * <p>So the two defaults are opposite on purpose. Embedding grants nothing
     * because the useful set is the one nobody guessed; a shell tool that
     * cannot read a file is not a tool.
     */
    private static Bounds theWholeMachine() {
        Bounds everything = Bounds.standard();
        for (HostService service : HostService.values()) {
            everything = everything.granting(service);
        }
        return everything;
    }

    /**
     * A path on the command line, which is a script to run.
     *
     * <p>The first thing anybody asks of a language's command line, and the
     * thing this could not do: a path was dropped without a word and the
     * console opened instead. That is worse than a refusal -- the script that
     * was meant to run has not, nothing said so, and whoever called it is
     * looking at a prompt they did not ask for.
     */
    private record ScriptOnTheCommandLine(
            String[] arguments, String startedIn, Path root) {

        private int runThrough(Interpreter interpreter, PrintStream out) {
            Path script = theScriptNamed();
            String source;
            try {
                source = theSourceDecodedFrom(Files.readAllBytes(script));
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

        /**
         * Puts the script in its own directory and writes down where it came
         * from, which is what a real Rebol does before it runs one.
         *
         * <p>A script that ships beside its data reads that data wherever it
         * is called from, and a script that wants the caller's own files finds
         * where they were in {@code system/options/path} and has to say so.
         */
        private void tellItWhereItIs(Interpreter interpreter, Path script) {
            String saying = """
                    system/options/script: %%%s
                    system/options/path: %%%s
                    system/options/args: %s
                    change-dir %%%s""".formatted(
                    asTheScriptSeesIt(script),
                    dirized(asTheScriptSeesIt(Path.of(startedIn))),
                    theArgumentsAfterTheScript(),
                    dirized(asTheScriptSeesIt(script.getParent())));
            interpreter.defineFreshWordsIn(saying);
            interpreter.run(saying);
        }

        /**
         * A path written the way the script can read it back.
         *
         * <p>The filesystem is rooted, so what the script sees counts from
         * that root rather than from the machine. Handing it the machine's own
         * path would name something it cannot reach -- and for a run rooted at
         * the machine the two are the same string, which is why this was
         * invisible until the first confined run.
         *
         * <p>Anything outside the root is the root itself, there being nothing
         * else it could honestly be called.
         */
        private String asTheScriptSeesIt(Path host) {
            Path absolute = host.toAbsolutePath().normalize();
            if (!absolute.startsWith(root)) {
                return "/";
            }
            String inside = root.relativize(absolute).toString().replace('\\', '/');
            return inside.isEmpty() ? "/" : "/" + inside;
        }

        /**
         * The script's bytes as source, which is where a byte order mark goes.
         *
         * <p>Decoded rather than read as text, because the mark marks the
         * encoding rather than being part of the source -- the same thing LOAD
         * of a binary does. Reading the file as text keeps it, and the
         * script's first word becomes one nobody can have defined: a file an
         * editor marked fails on its own first line.
         */
        private static String theSourceDecodedFrom(byte[] bytes) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        }

        /**
         * The file the first argument names, read inside the root when one
         * was given.
         *
         * <p>A confined script writes `%/units/files/x.r3` and means a file
         * inside what it can see, so an interpreter it starts has to read the
         * path the same way. Resolving it against the machine instead names
         * nothing, or something else entirely.
         */
        private Path theScriptNamed() {
            Path written = Path.of(arguments[0]);
            Path resolved = written.isAbsolute()
                    ? root.resolve(Path.of("/").relativize(written))
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
