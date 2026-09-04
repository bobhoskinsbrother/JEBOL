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
}
