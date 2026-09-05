package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DO given a file, which runs a script rather than evaluating a name.
 *
 * <p>{@code n-control.c} sends {@code REB_FILE}, {@code REB_URL},
 * {@code REB_STRING} and {@code REB_BINARY} to the same place:
 * {@code Do_Sys_Func(SYS_CTX_DO_P, ...)}, which is {@code sys/do*} in
 * {@code sys-base.reb}. So running a script is not written in C at all. That
 * function loads the file with its header, runs its needs, interns it, and
 * evaluates it with the working directory moved to the file's own -- which is
 * why the suite asserts {@code dir = what-dir} after each one.
 *
 * <p>JEBOL matched a file against the Java {@code StringValue} case, because a
 * file is one, and evaluated the file's <em>name</em> as source. So
 * {@code do %units/files/unset.r3} raised {@code no-value} on the word
 * {@code units}. The three assertions that would have shown it had been cut out
 * of the vendored suite and the fourth, which asks only that an error comes
 * back, passed on the wrong error.
 */
class DoFileFromTheSourceTest {

    private static Interpreter grantedFilesUnder(Path root) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.FILES)
                        .granting(HostService.WORKING_DIRECTORY));
        interpreter.useFileSystem(FileSystemPort.rootedAt(root));
        return interpreter;
    }

    private static String answerTo(Path root, String source) {
        Interpreter interpreter = grantedFilesUnder(root);
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(Path root, String source) {
        return answerTo(root, "failure: try [" + source + "] failure/id");
    }

    private static void script(Path root, String named, String body) throws IOException {
        Files.writeString(root.resolve(named), body);
    }

    @Nested
    @DisplayName("what the script answers")
    class WhatItAnswers {

        @Test
        @DisplayName("the last value the script evaluated")
        void theLastValue(@TempDir Path root) throws IOException {
            script(root, "adds.r3", """
                    Rebol [title: "adds"]
                    1 + 2""");

            assertThat(answerTo(root, "do %adds.r3")).isEqualTo("3");
        }

        @Test
        @DisplayName("unset, when the script ends on something that answers nothing")
        void unsetWhenTheScriptAnswersNothing(@TempDir Path root) throws IOException {
            script(root, "unset.r3", """
                    Rebol [title: "Script which returns UNSET"]
                    print "Hello\"""");

            assertThat(answerTo(root, "unset? do %unset.r3")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("QUIT ends the script and answers unset, and the host lives on")
        void quitEndsTheScript(@TempDir Path root) throws IOException {
            script(root, "quit.r3", """
                    Rebol [title: "Script which uses quit"]
                    quit
                    print "Hello\"""");

            assertThat(answerTo(root, """
                    reduce [unset? do %quit.r3 1 + 1]"""))
                    .as("the script stops, the thing that ran it does not")
                    .isEqualTo("[#(true) 2]");
        }

        @Test
        @DisplayName("QUIT/RETURN answers what it was given")
        void quitReturnAnswersItsValue(@TempDir Path root) throws IOException {
            script(root, "quit-return.r3", """
                    Rebol [title: "Script which uses quit/return"]
                    quit/return 42
                    print "Hello\"""");

            assertThat(answerTo(root, "do %quit-return.r3")).isEqualTo("42");
        }

        @Test
        @DisplayName("and the script's own line after QUIT never runs")
        void nothingAfterQuitRuns(@TempDir Path root) throws IOException {
            script(root, "quit.r3", """
                    Rebol [title: "quits"]
                    quit
                    set 'escaped true""");

            assertThat(answerTo(root, """
                    escaped: false
                    do %quit.r3
                    escaped"""))
                    .isEqualTo("#(false)");
        }
    }

    @Nested
    @DisplayName("when the script goes wrong")
    class WhenItGoesWrong {

        @Test
        @DisplayName("the error is the script's own, not one about its name")
        void theErrorIsTheScriptsOwn(@TempDir Path root) throws IOException {
            script(root, "error.r3", """
                    Rebol [title: "Script with evaluation error"]
                    1 / 0""");

            assertThat(errorIdFrom(root, "do %error.r3"))
                    .as("this assertion passed while DO was broken, because it asks "
                            + "only that some error came back")
                    .isEqualTo("zero-divide");
        }

        @Test
        @DisplayName("a file that is not there cannot be run")
        void afileThatIsNotThere(@TempDir Path root) {
            assertThat(errorIdFrom(root, "do %never-written.r3"))
                    .isEqualTo("cannot-open");
        }
    }

    @Nested
    @DisplayName("the working directory, which moves and comes back")
    class TheWorkingDirectory {

        @Test
        @DisplayName("a script runs in its own directory, so it can name a sibling")
        void ascriptRunsInItsOwnDirectory(@TempDir Path root) throws IOException {
            Files.createDirectories(root.resolve("nested"));
            script(root, "nested/beside.txt", "the sibling");
            script(root, "nested/reads-sibling.r3", """
                    Rebol [title: "reads a sibling by bare name"]
                    to-string read %beside.txt""");

            assertThat(answerTo(root, "do %nested/reads-sibling.r3"))
                    .as("sys/do* changes to the file's directory before evaluating")
                    .isEqualTo("\"the sibling\"");
        }

        @Test
        @DisplayName("and it is put back afterwards, which the suite asserts four times")
        void itIsPutBackAfterwards(@TempDir Path root) throws IOException {
            Files.createDirectories(root.resolve("nested"));
            script(root, "nested/quiet.r3", """
                    Rebol [title: "does nothing"]
                    1""");

            assertThat(answerTo(root, """
                    dir: what-dir
                    do %nested/quiet.r3
                    dir = what-dir""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("even when the script raises")
        void evenWhenTheScriptRaises(@TempDir Path root) throws IOException {
            Files.createDirectories(root.resolve("nested"));
            script(root, "nested/breaks.r3", """
                    Rebol [title: "raises"]
                    1 / 0""");

            assertThat(answerTo(root, """
                    dir: what-dir
                    error? try [do %nested/breaks.r3]
                    dir = what-dir""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("the other things DO takes, which must not have changed")
    class TheOtherThings {

        @Test
        @DisplayName("a string is evaluated as source and keeps no header")
        void astringIsStillSource(@TempDir Path root) {
            assertThat(answerTo(root, """
                    do "1 + 2\"""")).isEqualTo("3");
        }

        @Test
        @DisplayName("a block is evaluated")
        void ablockIsStillEvaluated(@TempDir Path root) {
            assertThat(answerTo(root, "do [1 + 2]")).isEqualTo("3");
        }

        @Test
        @DisplayName("and a word still answers what it holds")
        void awordStillAnswersWhatItHolds(@TempDir Path root) {
            assertThat(answerTo(root, """
                    held: 7
                    do 'held""")).isEqualTo("7");
        }
    }
}
