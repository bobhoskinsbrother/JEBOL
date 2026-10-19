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


class DoFileFromTheSourceTest {

    private static Interpreter makeAnInterpreterGrantedFilesUnder(Path root) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .grant(HostService.FILES)
                        .grant(HostService.WORKING_DIRECTORY));
        interpreter.useFileSystem(FileSystemPort.rootFileSystem(root));
        return interpreter;
    }

    private static String answerTo(Path root, String source) {
        Interpreter interpreter = makeAnInterpreterGrantedFilesUnder(root);
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdFrom(Path root, String source) {
        return answerTo(root, "failure: try [" + source + "] failure/id");
    }

    private static void writeAScript(Path root, String fileName, String body) throws IOException {
        Files.writeString(root.resolve(fileName), body);
    }

    @Nested
    @DisplayName("what the script answers")
    class WhatItAnswers {

        @Test
        @DisplayName("the last value the script evaluated")
        void answersTheLastValue(@TempDir Path root) throws IOException {
            writeAScript(root, "adds.r3", """
                    Rebol [title: "adds"]
                    1 + 2""");

            assertThat(answerTo(root, "do %adds.r3")).isEqualTo("3");
        }

        @Test
        @DisplayName("unset, when the script ends on something that answers nothing")
        void answersUnsetWhenTheScriptAnswersNothing(@TempDir Path root) throws IOException {
            writeAScript(root, "unset.r3", """
                    Rebol [title: "Script which returns UNSET"]
                    print "Hello\"""");

            assertThat(answerTo(root, "unset? do %unset.r3")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("QUIT ends the script and answers unset, and the host lives on")
        void quitEndsTheScript(@TempDir Path root) throws IOException {
            writeAScript(root, "quit.r3", """
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
            writeAScript(root, "quit-return.r3", """
                    Rebol [title: "Script which uses quit/return"]
                    quit/return 42
                    print "Hello\"""");

            assertThat(answerTo(root, "do %quit-return.r3")).isEqualTo("42");
        }

        @Test
        @DisplayName("and the script's own line after QUIT never runs")
        void runsNothingAfterQuit(@TempDir Path root) throws IOException {
            writeAScript(root, "quit.r3", """
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
        void raisesTheScriptsOwnError(@TempDir Path root) throws IOException {
            writeAScript(root, "error.r3", """
                    Rebol [title: "Script with evaluation error"]
                    1 / 0""");

            assertThat(readTheErrorIdFrom(root, "do %error.r3"))
                    .as("this assertion passed while DO was broken, because it asks "
                            + "only that some error came back")
                    .isEqualTo("zero-divide");
        }

        @Test
        @DisplayName("a file that is not there cannot be run")
        void afileThatIsNotThere(@TempDir Path root) {
            assertThat(readTheErrorIdFrom(root, "do %never-written.r3"))
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
            writeAScript(root, "nested/beside.txt", "the sibling");
            writeAScript(root, "nested/reads-sibling.r3", """
                    Rebol [title: "reads a sibling by bare name"]
                    to-string read %beside.txt""");

            assertThat(answerTo(root, "do %nested/reads-sibling.r3"))
                    .as("sys/do* changes to the file's directory before evaluating")
                    .isEqualTo("\"the sibling\"");
        }

        @Test
        @DisplayName("and it is put back afterwards, which the suite asserts four times")
        void isPutBackAfterwards(@TempDir Path root) throws IOException {
            Files.createDirectories(root.resolve("nested"));
            writeAScript(root, "nested/quiet.r3", """
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
            writeAScript(root, "nested/breaks.r3", """
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
        void stillTakesAStringAsSource(@TempDir Path root) {
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
