package org.jebol.adapter.cli;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleAsRebolPrintsItEndToEndTest {

    private static final String WHAT_WAS_TYPED = "/console/a-session.typed";

    private static final String WHAT_A_REAL_3_22_5_PRINTED = "/console/a-session.recorded";

    private String readFromTheClasspath(String named) throws IOException {
        try (InputStream carried = getClass().getResourceAsStream(named)) {
            assertThat(carried).as("%s is not on the test classpath", named).isNotNull();
            return new String(carried.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String whatTheConsolePrintsAfterItsBanner(String typed) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8);
        Interpreter interpreter = Interpreter.writingTo(new StreamOutput(output));
        new Repl(interpreter, new BufferedReader(new StringReader(typed)), output).run();
        String printed = captured.toString(StandardCharsets.UTF_8);
        return printed.substring(printed.indexOf('\n') + 1);
    }

    @Nested
    @DisplayName("a whole session, against what a real 3.22.5 console printed for the same typing")
    class AWholeSession {

        @Test
        @DisplayName("results, errors raised and errors held, unprinted types, a cut string and a halt")
        void printsWhatRebolPrinted() throws IOException {
            assertThat(whatTheConsolePrintsAfterItsBanner(readFromTheClasspath(WHAT_WAS_TYPED)))
                    .isEqualTo(readFromTheClasspath(WHAT_A_REAL_3_22_5_PRINTED));
        }
    }

    @Nested
    @DisplayName("a result is molded to five hundred characters and an error formed to six hundred and forty")
    class TheLimits {

        @Test
        @DisplayName("a molded result of 499 and 500 characters is whole, and one of 501 is cut with an ellipsis")
        void aResultIsCutPastFiveHundred() {
            assertThat(whatTheConsolePrintsAfterItsBanner("""
                    append/dup copy "" "x" 497
                    append/dup copy "" "x" 498
                    append/dup copy "" "x" 499
                    """)).isEqualTo(
                    ">> == {" + "x".repeat(497) + "}\n\n"
                            + ">> == {" + "x".repeat(498) + "}\n\n"
                            + ">> == {" + "x".repeat(498) + "x..." + "\n\n"
                            + ">> ");
        }

        @Test
        @DisplayName("an error formed to 640 characters is whole, and to 641 or 642 is cut with an ellipsis")
        void anErrorIsCutPastSixHundredAndForty() {
            assertThat(whatTheConsolePrintsAfterItsBanner("""
                    make error! append/dup copy "" "x" 621
                    make error! append/dup copy "" "x" 622
                    make error! append/dup copy "" "x" 623
                    """)).isEqualTo(
                    ">> \n** User error: {" + "x".repeat(621) + "}\n\n"
                            + ">> \n** User error: {" + "x".repeat(622) + "}...\n"
                            + ">> \n** User error: {" + "x".repeat(623) + "...\n"
                            + ">> ");
        }
    }

    @Nested
    @DisplayName("the console waits for more while a bracket, paren or brace is open, and shows which")
    class Continuation {

        @Test
        @DisplayName("each kind of opening, and the ones a string, a comment or a character hide")
        void waitsAsRebolsHostWaits() {
            assertThat(whatTheConsolePrintsAfterItsBanner("""
                    (1 +
                    2)
                    {abc
                    def}
                    %{a
                    }%
                    "["
                    1 ; [
                    [(
                    1)]
                    #"["
                    first [a
                    ] ;]
                    {a^}
                    b}
                    """)).isEqualTo("""
                    >>  ( == 3

                    >>  { == "abc^/def"

                    >>  { == "a^/"

                    >> == "["

                    >> == 1

                    >>  ( == [(
                        1
                    )]

                    >> == #"["

                    >>  [ == a

                    >>  { == "a}^/b"

                    >>\s""");
        }

        @Test
        @DisplayName("input that ends while something is open is dropped, and the prompt comes back once more")
        void anOpenBlockAtTheEndIsDropped() {
            assertThat(whatTheConsolePrintsAfterItsBanner("""
                    [1
                    """)).isEqualTo(">>  [ >> ");
        }
    }

    @Nested
    @DisplayName("a script or --do on the command line leaves as r3 leaves")
    class TheCommandLine {

        @TempDir
        Path directory;

        private record Ran(int exitStatus, String printed, String reported) {
        }

        private Ran running(String... arguments) {
            ByteArrayOutputStream printed = new ByteArrayOutputStream();
            ByteArrayOutputStream reported = new ByteArrayOutputStream();
            int status = Repl.runTheCommandLine(arguments,
                    new PrintStream(printed, true, StandardCharsets.UTF_8),
                    new PrintStream(reported, true, StandardCharsets.UTF_8),
                    directory.toString());
            return new Ran(status, printed.toString(StandardCharsets.UTF_8),
                    reported.toString(StandardCharsets.UTF_8));
        }

        private Ran runningAScriptSaying(String line) throws IOException {
            Path script = directory.resolve("s.r3");
            Files.writeString(script, "REBOL []\nprint 1\n" + line + "\nprint 2\n");
            return running(script.toString());
        }

        @ParameterizedTest(name = "a script that runs {0} leaves with {1}")
        @CsvSource(delimiter = '|', textBlock = """
                break          | 0
                return 5       | 0
                throw 9        | 0
                halt           | 15
                quit/return 4  | 4
                """)
        void endsQuietlyWithTheStatusRebolGives(String line, int status) throws IOException {
            Ran ran = runningAScriptSaying(line);

            assertThat(ran.printed()).isEqualTo("1\n");
            assertThat(ran.reported()).isEmpty();
            assertThat(ran.exitStatus()).isEqualTo(status);
        }

        @Test
        @DisplayName("a script that finishes prints all of itself and leaves with nought")
        void aFinishedScript() throws IOException {
            Ran ran = runningAScriptSaying("x: 1");

            assertThat(ran.printed()).isEqualTo("1\n2\n");
            assertThat(ran.reported()).isEmpty();
            assertThat(ran.exitStatus()).isZero();
        }

        @Test
        @DisplayName("a script that fails reports the formed error on the error stream and leaves with one")
        void aFailingScript() throws IOException {
            Ran ran = runningAScriptSaying("1 / 0");

            assertThat(ran.printed()).isEqualTo("1\n");
            assertThat(ran.reported()).isEqualTo("""

                    ** Math error: attempt to divide by zero
                    ** Where: / do either either if -apply-
                    ** Near: / 0 print 2

                    """);
            assertThat(ran.exitStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("a script that is not there is reported by the name it was given")
        void aMissingScript() {
            Ran ran = running("nope.r3");

            assertThat(ran.printed()).isEmpty();
            assertThat(ran.reported()).isEqualTo("""

                    ** access error: script not found: %nope.r3

                    """);
            assertThat(ran.exitStatus()).isEqualTo(1);
        }

        @ParameterizedTest(name = "--do with {0} leaves with {1}")
        @CsvSource(delimiter = '|', textBlock = """
                halt           | 15
                quit/return 4  | 4
                """)
        void doEndsQuietlyWithTheStatusRebolGives(String line, int status) {
            Ran ran = running("--do", "print 1 " + line + " print 2");

            assertThat(ran.printed()).isEqualTo("1\n");
            assertThat(ran.reported()).isEmpty();
            assertThat(ran.exitStatus()).isEqualTo(status);
        }

        @Test
        @DisplayName("--do that fails reports the formed error and leaves with one")
        void aFailingDo() {
            Ran ran = running("--do", "print 1 1 / 0 print 2");

            assertThat(ran.printed()).isEqualTo("1\n");
            assertThat(ran.reported()).isEqualTo("""

                    ** Math error: attempt to divide by zero
                    ** Where: / do if -apply-
                    ** Near: / 0 print 2

                    """);
            assertThat(ran.exitStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("--do that finishes leaves with nought")
        void aFinishedDo() {
            Ran ran = running("--do", "print 1 x: 1 print 2");

            assertThat(ran.printed()).isEqualTo("1\n2\n");
            assertThat(ran.exitStatus()).isZero();
        }

        @Test
        @DisplayName("--do that throws past every catch never reaches its quit, so the console opens")
        void aThrowingDoOpensTheConsole() {
            Ran ran = running("--do", "print 1 throw 9 print 2");

            assertThat(ran.printed())
                    .startsWith("1\n")
                    .endsWith(">> ");
            assertThat(ran.reported()).isEmpty();
            assertThat(ran.exitStatus()).isEqualTo(Repl.KEEP_THE_PROCESS);
        }
    }
}
