package org.jebol.adapter.cli;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The console, driven the way a person drives it: text in, text out.
 *
 * <p>Nothing here reaches past the interface to call an evaluator directly.
 * Green unit tests prove the pieces; only this proves the system, and when the
 * two disagree this is the one to believe.
 */
class ReplEndToEndTest {

    /** A session: lines typed, everything the console printed. */
    private static String session(String... linesTyped) {
        String typed = String.join("\n", List.of(linesTyped)) + "\nquit\n";
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8);

        Interpreter interpreter = Interpreter.writingTo(new StreamOutput(output));
        new Repl(interpreter, new BufferedReader(new StringReader(typed)), output).run();

        return captured.toString(StandardCharsets.UTF_8);
    }

    /** The same session, with a filesystem to reach under one directory. */
    private static String sessionWithFiles(java.nio.file.Path directory, String... linesTyped) {
        String typed = String.join("\n", List.of(linesTyped)) + "\nquit\n";
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8);

        Interpreter interpreter = Interpreter.writingTo(new StreamOutput(output),
                org.jebol.application.Bounds.standard()
                        .granting(org.jebol.domain.host.HostService.FILES));
        interpreter.useFileSystem(
                org.jebol.application.FileSystemPort.rootedAt(directory));
        new Repl(interpreter, new BufferedReader(new StringReader(typed)), output).run();

        return captured.toString(StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("arithmetic, the way the guide's first example reads")
    class Arithmetic {

        @Test
        void addsTwoNumbers() {
            assertThat(session("1 + 2")).contains("== 3");
        }

        @Test
        @DisplayName("with no precedence: 2 + 3 * 4 is 20")
        void hasNoOperatorPrecedence() {
            assertThat(session("2 + 3 * 4")).contains("== 20");
        }

        @Test
        @DisplayName("a paren is the only way to group")
        void parensGroup() {
            assertThat(session("2 + (3 * 4)")).contains("== 14");
        }

        @Test
        @DisplayName("an operator reaches into a prefix argument")
        void operatorsBindInsidePrefixArguments() {
            assertThat(session("add 1 2 * 3")).contains("== 7");
        }

        @Test
        @DisplayName("prefix calls nest without parens")
        void prefixCallsNest() {
            assertThat(session("add 1 add 2 3")).contains("== 6");
        }
    }

    @Nested
    @DisplayName("words and assignment")
    class Words {

        @Test
        void assignsAndReadsBack() {
            assertThat(session("total: 10", "total * 2")).contains("== 20");
        }

        @Test
        @DisplayName("a set-word produces what it assigned, so chains work")
        void chainedAssignment() {
            assertThat(session("a: b: 7", "add a b")).contains("== 14");
        }

        @Test
        @DisplayName("a lit-word gives the word, not what it names")
        void litWordsAreNotLookedUp() {
            assertThat(session("x: 1", "'x")).contains("== x");
        }
    }

    @Nested
    @DisplayName("conditional truth, where REBOL surprises people")
    class ConditionalTruth {

        @Test
        @DisplayName("zero is a value, so zero is true")
        void zeroIsTrue() {
            assertThat(session("if 0 [\"taken\"]")).contains("== \"taken\"");
        }

        @Test
        void emptyBlockIsTrue() {
            assertThat(session("if [] [\"taken\"]")).contains("== \"taken\"");
        }

        @Test
        void noneIsFalse() {
            assertThat(session("if none [\"taken\"]")).contains("== _");
        }

        @Test
        @DisplayName("any returns the value, which is how defaults are written")
        void anyReturnsAValue() {
            assertThat(session("any [none none 100]")).contains("== 100");
        }
    }

    @Nested
    @DisplayName("errors end the expression, never the session")
    class Errors {

        @Test
        void divisionByZeroIsReported() {
            assertThat(session("divide 1 0")).contains("math error");
        }

        @Test
        @DisplayName("and the prompt comes back afterwards")
        void theSessionCarriesOnAfterAnError() {
            String transcript = session("divide 1 0", "1 + 1");

            assertThat(transcript).contains("math error");
            assertThat(transcript).contains("== 2");
        }

        @Test
        @DisplayName("a word nobody defined is reported, not a crash")
        void undefinedWordIsReported() {
            String transcript = session("nosuchword", "2 + 2");

            assertThat(transcript).contains("script error");
            assertThat(transcript).contains("== 4");
        }

        @Test
        @DisplayName("a syntax error is reported like any other")
        void syntaxErrorIsReported() {
            String transcript = session("1 + ]", "3 + 3");

            assertThat(transcript).contains("error");
            assertThat(transcript).contains("== 6");
        }
    }

    @Nested
    @DisplayName("printing goes through the output port")
    class Printing {

        @Test
        void printWritesItsArgument() {
            assertThat(session("print \"hello from JEBOL\"")).contains("hello from JEBOL");
        }

        @Test
        @DisplayName("print itself returns nothing, so nothing is echoed for it")
        void printEchoesNoResult() {
            String transcript = session("print \"once\"");

            assertThat(transcript).contains("once");
            assertThat(transcript).doesNotContain("== ");
        }
    }

    @Nested
    @DisplayName("multi-line input")
    class MultiLineInput {

        @Test
        @DisplayName("an unclosed block asks for more rather than failing")
        void unclosedBlockContinues() {
            assertThat(session("either true [", "  \"yes\"", "][", "  \"no\"", "]"))
                    .contains("== \"yes\"");
        }

        @Test
        @DisplayName("a string spanning lines is not an unclosed block")
        void bracedStringsSpanLines() {
            assertThat(session("length? {one", "two}")).contains("== 7");
        }
    }

    @Nested
    @DisplayName("series behave as series")
    class Series {

        @Test
        void appendMutatesAndIsVisible() {
            assertThat(session("name: \"world\"", "append name \"!\"", "name"))
                    .contains("== \"world!\"");
        }

        @Test
        void lengthCountsCharacters() {
            assertThat(session("length? \"abc\"")).contains("== 3");
        }

        @Test
        @DisplayName("an astral character counts as one")
        void lengthCountsCodepoints() {
            assertThat(session("length? \"a😀b\"")).contains("== 3");
        }

        @Test
        void firstTakesTheHead() {
            assertThat(session("first [a b c]")).contains("== a");
        }
    }

    @Nested
    @DisplayName("reading text as data, a value at a time")
    class ReadingSourceText {

        @Test
        @DisplayName("a walk through three lines names each line it reaches")
        void aWalkKeepsItsOwnLineCount() {
            String session = session(
                    "code: rejoin [{first} newline {second} newline {third}]",
                    "line: 1",
                    "set [value code line] transcode/next/line :code :line",
                    "reduce [value line]",
                    "set [value code line] transcode/next/line :code :line",
                    "reduce [value line]",
                    "set [value code line] transcode/next/line :code :line",
                    "reduce [value line]");

            assertThat(session)
                    .contains("== [first 1]")
                    .contains("== [second 2]")
                    .contains("== [third 3]");
        }

        @Test
        @DisplayName("and a mistake on the fourth line is reported as the fourth line")
        void aFailureNamesTheLineInTheWholeFile() {
            assertThat(session(
                    "e: try [transcode/line \"1d\" 4]",
                    "e/near"))
                    .contains("== \"(line 4) 1d\"");
        }

        @Test
        @DisplayName("and asking to start counting from nothing is refused, not guessed at")
        void aStartOfZeroIsRefused() {
            assertThat(session("transcode/line \"1 2\" 0"))
                    .contains("a number outside the range this operation allows")
                    .contains("line one or later, not 0");
        }
    }

    @Nested
    @DisplayName("modules, typed the way a person types them")
    class Modules {

        @Test
        @DisplayName("a module typed at the prompt answers a module")
        void aTypedModuleAnswersAModule() {
            assertThat(session("module? make module! [[Title: \"t\"] [a: 1]]"))
                    .contains("== #(true)");
        }

        @Test
        @DisplayName("none of a module's words are left behind in the session")
        void noModuleWordEscapesIntoTheSession() {
            String transcript = session(
                    "m: make module! [[Title: \"t\" Exports: [shown]] "
                            + "[shown: 1 kept-back: 2]]",
                    "value? 'kept-back",
                    "value? 'shown",
                    "m/shown");

            assertThat(transcript.split("== #\\(false\\)", -1))
                    .as("neither the private word nor the exported one is in the session")
                    .hasSize(3);
            assertThat(transcript)
                    .as("and the module itself still holds the value")
                    .contains("== 1");
        }

        @Test
        @DisplayName("EXP answers a number at the prompt, not a block")
        void expAnswersANumber() {
            assertThat(session("exp 0")).contains("== 1");
        }

        @Test
        @DisplayName("DECODE-URL is a function at the prompt, not none")
        void decodeUrlIsAFunction() {
            assertThat(session("any-function? :decode-url")).contains("== #(true)");
        }

        @Test
        @DisplayName("a log function writes a line, which is the whole point of the five")
        void aLogFunctionWrites() {
            assertThat(session("log-info 'app \"a message\"")).contains("a message");
        }
    }

    @Nested
    @DisplayName("the session survives whatever is typed at it")
    class Robustness {

        @Test
        @DisplayName("nesting past the limit is an ordinary error, not a crash")
        void absurdNestingIsAnOrdinaryError() {
            String deeplyNested = "(".repeat(20_000) + "1" + ")".repeat(20_000);

            String transcript = session(deeplyNested, "1 + 1");

            assertThat(transcript)
                    .as("nesting past the limit must be reported as a REBOL error")
                    .contains("error");
            assertThat(transcript)
                    .as("and the session must still be usable afterwards")
                    .contains("== 2");
        }

        @Test
        @DisplayName("nesting just inside the limit still works")
        void deepButLegalNestingWorks() {
            int depth = 900;
            String nested = "(".repeat(depth) + "1" + ")".repeat(depth);

            assertThat(session(nested))
                    .as("real source never nests this deep, but it must not break")
                    .contains("== 1");
        }

        @Test
        void emptyInputIsHarmless() {
            assertThat(session("", "", "1 + 1")).contains("== 2");
        }
    }

    /** The same session, allowed to start real programs on this machine. */
    private static String sessionWithProcesses(String... linesTyped) {
        String typed = String.join("\n", List.of(linesTyped)) + "\nquit\n";
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8);

        Interpreter interpreter = Interpreter.writingTo(new StreamOutput(output),
                org.jebol.application.Bounds.standard()
                        .granting(org.jebol.domain.host.HostService.PROCESSES));
        interpreter.useProcesses(new org.jebol.adapter.host.JavaProcesses());
        new Repl(interpreter, new BufferedReader(new StringReader(typed)), output).run();

        return captured.toString(StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("running another program, for real")
    class RunningAnotherProgram {

        @Test
        @DisplayName("what echo prints lands in the caller's buffer")
        void whatEchoPrintsLandsInTheBuffer() {
            String transcript = sessionWithProcesses(
                    "buf: copy {}",
                    "call/wait/output [{echo} {hello from a child}] buf",
                    "buf");

            assertThat(transcript).contains("hello from a child");
        }

        @Test
        @DisplayName("the shell reads only the first entry as its command line")
        void theShellReadsOnlyTheFirstEntry() {
            String transcript = sessionWithProcesses(
                    "buf: copy {}",
                    "call/wait/shell/output [{echo} {spilled}] buf",
                    "find buf {spilled}");

            assertThat(transcript)
                    .as("later entries are the shell's positional parameters,"
                            + " not part of the command line")
                    .contains("== _");
        }

        @Test
        @DisplayName("a program that is not there is an error, not a hang")
        void aMissingProgramIsAnError() {
            String transcript = sessionWithProcesses(
                    "call [{jebol-no-such-program-e2e}]",
                    "1 + 1");

            assertThat(transcript)
                    .contains("error")
                    .as("the mistake ends the expression, never the session")
                    .contains("== 2");
        }
    }

    @Nested
    @DisplayName("writing a file, the way a script keeps a list")
    class WritingAFile {

        @Test
        @DisplayName("lines written, a line appended, the whole read back")
        void aListGrowsALineAtATime(@TempDir java.nio.file.Path directory) {
            String transcript = sessionWithFiles(directory,
                    "write/lines %list.txt [{milk} {eggs}]",
                    "write/append %list.txt {jam^/}",
                    "length? read %list.txt");

            assertThat(transcript)
                    .as("milk, eggs and jam, each with its line feed, is 14")
                    .contains("== 14");
        }

        @Test
        @DisplayName("a correction seeks back and overwrites in place")
        void aCorrectionOverwritesInPlace(@TempDir java.nio.file.Path directory) {
            String transcript = sessionWithFiles(directory,
                    "write %score.txt {score 0}",
                    "write/seek %score.txt {9} 6",
                    "read/string %score.txt");

            assertThat(transcript).contains("== \"score 9\"");
        }

        @Test
        @DisplayName("a negative bound is reported as an error, not obeyed")
        void aNegativeBoundIsReported(@TempDir java.nio.file.Path directory) {
            String transcript = sessionWithFiles(directory,
                    "write/part %a.txt {abc} -1",
                    "1 + 1");

            assertThat(transcript)
                    .contains("a number outside the range this operation allows")
                    .as("the mistake ends the expression, never the session")
                    .contains("== 2");
        }
    }

    /**
     * A path on the command line is a script to run, which is the first thing
     * anybody asks of a language's command line and the thing JEBOL could not
     * do: a path was dropped without a word and the console opened instead,
     * which is worse than a refusal -- the script that was meant to run has
     * not, nothing said so, and whoever called it is looking at a prompt.
     *
     * <p>Every figure here was read off {@code ./r3-head} 3.22.5 running the
     * same script.
     */
    @Nested
    @DisplayName("a script named on the command line")
    class AScriptOnTheCommandLine {

        private static Ran running(java.nio.file.Path directory, String... arguments) {
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            PrintStream output =
                    new PrintStream(captured, true, StandardCharsets.UTF_8);
            int status = Repl.runTheCommandLine(arguments, output, directory.toString());
            return new Ran(status, captured.toString(StandardCharsets.UTF_8));
        }

        private record Ran(int exitStatus, String printed) {
        }

        private static java.nio.file.Path scriptSaying(
                java.nio.file.Path directory, String named, String source)
                throws java.io.IOException {

            java.nio.file.Path written = directory.resolve(named);
            java.nio.file.Files.writeString(written, source);
            return written;
        }

        @Test
        @DisplayName("runs, prints what it prints, and leaves with nought")
        void itRunsTheScript(@TempDir java.nio.file.Path directory) throws Exception {
            scriptSaying(directory, "hello.r3", "print [{two and two is} 2 + 2]\n");

            Ran ran = running(directory, directory.resolve("hello.r3").toString());

            assertThat(ran.printed()).isEqualTo("two and two is 4\n");
            assertThat(ran.exitStatus()).isZero();
        }

        @Test
        @DisplayName("a script with a header runs the same way")
        void aheaderChangesNothing(@TempDir java.nio.file.Path directory)
                throws Exception {
            scriptSaying(directory, "hdr.r3",
                    "Rebol [Title: {T}]\nprint {with header}\n");

            assertThat(running(directory, directory.resolve("hdr.r3").toString())
                    .printed()).isEqualTo("with header\n");
        }

        /**
         * The directory a script counts from is its own, so a script that
         * ships beside its data reads that data wherever it is called from.
         * Where the caller was is kept in {@code system/options/path}.
         */
        @Test
        @DisplayName("and counts relative paths from its own directory, not the caller's")
        void itcountsFromItsOwnDirectory(@TempDir java.nio.file.Path directory)
                throws Exception {

            java.nio.file.Path beside = directory.resolve("beside");
            java.nio.file.Files.createDirectory(beside);
            java.nio.file.Files.writeString(beside.resolve("data.txt"), "found it");
            java.nio.file.Files.writeString(beside.resolve("reader.r3"),
                    "print read/string %data.txt\n");

            Ran ran = running(directory, beside.resolve("reader.r3").toString());

            assertThat(ran.printed()).isEqualTo("found it\n");
            assertThat(ran.exitStatus()).isZero();
        }

        @Test
        @DisplayName("it can say where it is, where it came from and what followed it")
        void itcanSayWhereItIs(@TempDir java.nio.file.Path directory) throws Exception {
            scriptSaying(directory, "where.r3",
                    "print [what-dir system/options/script system/options/path]\n"
                    + "print mold system/options/args\n");

            Ran ran = running(directory,
                    directory.resolve("where.r3").toString(), "one", "two");

            assertThat(ran.printed())
                    .contains(directory.resolve("where.r3").toString())
                    .contains("[\"one\" \"two\"]");
        }

        @Test
        @DisplayName("QUIT/RETURN decides the number the process leaves with")
        void quitDecidesTheStatus(@TempDir java.nio.file.Path directory)
                throws Exception {
            scriptSaying(directory, "q.r3", "quit/return 42\n");

            assertThat(running(directory, directory.resolve("q.r3").toString())
                    .exitStatus()).isEqualTo(42);
        }

        @Test
        @DisplayName("a script that fails says so and leaves with one")
        void afailingScriptLeavesWithOne(@TempDir java.nio.file.Path directory)
                throws Exception {
            scriptSaying(directory, "boom.r3", "print {before} 1 / 0 print {after}\n");

            Ran ran = running(directory, directory.resolve("boom.r3").toString());

            assertThat(ran.printed())
                    .contains("before")
                    .contains("zero")
                    .as("nothing after the failure runs")
                    .doesNotContain("after");
            assertThat(ran.exitStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("a path that names nothing is a failure, not a console")
        void amissingScriptIsAFailure(@TempDir java.nio.file.Path directory) {
            Ran ran = running(directory, directory.resolve("nope.r3").toString());

            assertThat(ran.printed()).contains("nope.r3");
            assertThat(ran.exitStatus()).isEqualTo(1);
        }

        /**
         * A script run from the command line may reach the machine, because
         * the person who typed the command chose to run it. Confinement is
         * for a host embedding the interpreter, which builds its own bounds
         * and is granted nothing by default.
         */
        @Test
        @DisplayName("and it is granted the machine, as a shell tool has to be")
        void itisGrantedTheMachine(@TempDir java.nio.file.Path directory)
                throws Exception {
            scriptSaying(directory, "writes.r3",
                    "write %made.txt {by the script} print read/string %made.txt\n");

            Ran ran = running(directory, directory.resolve("writes.r3").toString());

            assertThat(ran.printed()).isEqualTo("by the script\n");
            assertThat(java.nio.file.Files.exists(directory.resolve("made.txt"))).isTrue();
        }

        /**
         * {@code --root} is what an interpreter starting another one hands
         * over, so the second is bounded the way the first is. A script
         * confined to a directory writes {@code %/x} and means a file inside
         * it, so the child reads the path it is given the same way -- resolving
         * it against the machine instead would name nothing, or something else.
         */
        @Test
        @DisplayName("--root confines the filesystem and every path is read inside it")
        void therootSwitchConfinesTheScript(@TempDir java.nio.file.Path directory)
                throws Exception {

            java.nio.file.Path inside = directory.resolve("inside");
            java.nio.file.Files.createDirectory(inside);
            java.nio.file.Files.writeString(inside.resolve("data.txt"), "inside it");
            java.nio.file.Files.writeString(inside.resolve("s.r3"),
                    "print read/string %data.txt print mold what-dir\n");

            Ran ran = running(directory, "--root", directory.toString(),
                    "/inside/s.r3");

            assertThat(ran.printed()).isEqualTo("inside it\n%/inside/\n");
            assertThat(ran.exitStatus()).isZero();
        }

        @Test
        @DisplayName("and a rooted script cannot read above its root")
        void arootedScriptCannotReachAbove(@TempDir java.nio.file.Path directory)
                throws Exception {

            java.nio.file.Path inside = directory.resolve("inside");
            java.nio.file.Files.createDirectory(inside);
            java.nio.file.Files.writeString(directory.resolve("secret.txt"), "not yours");
            java.nio.file.Files.writeString(inside.resolve("peek.r3"),
                    "print mold try [read/string %/../secret.txt]\n");

            Ran ran = running(directory, "--root", inside.toString(), "/peek.r3");

            assertThat(ran.printed()).doesNotContain("not yours");
        }

        /**
         * A file written by an editor that marks its encoding starts with
         * three bytes that are not part of the source. Decoding drops them,
         * as it does for LOAD of a binary; reading the file as text keeps
         * them, and the script's first word becomes one nobody can define.
         */
        @Test
        @DisplayName("a byte order mark at the front is not part of the script")
        void abyteOrderMarkIsNotSource(@TempDir java.nio.file.Path directory)
                throws Exception {

            java.nio.file.Files.write(directory.resolve("marked.r3"),
                    concatenated(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                            "Rebol [Title: {T}]\nprint {ran anyway}\n"
                                    .getBytes(StandardCharsets.UTF_8)));

            Ran ran = running(directory, directory.resolve("marked.r3").toString());

            assertThat(ran.printed()).isEqualTo("ran anyway\n");
            assertThat(ran.exitStatus()).isZero();
        }

        private static byte[] concatenated(byte[] first, byte[] second) {
            byte[] both = new byte[first.length + second.length];
            System.arraycopy(first, 0, both, 0, first.length);
            System.arraycopy(second, 0, both, first.length, second.length);
            return both;
        }

        @Test
        @DisplayName("-s is accepted and changes nothing, there being no security to lift")
        void thesecuritySwitchIsAccepted(@TempDir java.nio.file.Path directory)
                throws Exception {
            scriptSaying(directory, "s.r3", "print {ran}\n");

            Ran ran = running(directory, "-s", directory.resolve("s.r3").toString());

            assertThat(ran.printed()).isEqualTo("ran\n");
            assertThat(ran.exitStatus()).isZero();
        }
    }
}
