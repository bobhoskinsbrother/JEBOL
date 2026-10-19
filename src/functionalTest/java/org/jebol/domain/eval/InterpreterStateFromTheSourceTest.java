package org.jebol.domain.eval;

import org.jebol.application.Bounds;
import org.jebol.application.FileSystemPort;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InterpreterStateFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static String runWithFilesUnder(Path directory, String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().grant(HostService.FILES));
        interpreter.useFileSystem(FileSystemPort.rootFileSystem(directory));
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("VERSION")
    class TheVersion {

        @Test
        @DisplayName("answers text")
        void answersText() {
            assertThat(answerTo("string? version")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/DATA answers text too, which Rebol's own start-up loads into fifteen fields")
        void loadsTheDataFormIntoFifteenFields() {
            assertThat(answerTo("string? version/data")).isEqualTo(TRUE);
            assertThat(answerTo("length? load/as version/data 'unbound")).isEqualTo("15");
        }

        @Test
        @DisplayName("and its third field is the same version the text names")
        void agreesInBothForms() {
            assertThat(answerTo("find version form third load/as version/data 'unbound"))
                    .isNotEqualTo("_");
        }
    }

    @Nested
    @DisplayName("POKEZ counts from zero")
    class ZeroBasedPoke {

        @Test
        @DisplayName("index zero is the first element")
        void zeroIsTheFirst() {
            assertThat(answerTo("b: [1 2 3] pokez b 0 9 mold b")).isEqualTo("\"[9 2 3]\"");
        }

        @Test
        @DisplayName("and it answers the value, as POKE does")
        void answersTheValue() {
            assertThat(answerTo("pokez [1 2 3] 0 9")).isEqualTo("9");
        }

        @Test
        @DisplayName("the last element is one less than the length")
        void putsTheLastAtLengthLessOne() {
            assertThat(answerTo("b: [1 2 3] pokez b 2 9 mold b")).isEqualTo("\"[1 2 9]\"");
        }

        @Test
        @DisplayName("a negative index is not shifted, because it counts from the tail")
        void leavesANegativeIndexUnshifted() {
            assertThat(answerTo("b: [1 2 3] e: try [pokez b -1 9] error? e")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a bitset is not shifted either, because its index is a code")
        void leavesABitsetUnshifted() {
            assertThat(answerTo("b: charset \"a\" pokez b 122 true b/(#\"z\")"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("b: charset \"a\" pokez b 122 true b/(#\"a\")"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("past the end is refused, as POKE refuses it")
        void refusesPastTheEnd() {
            assertThat(readTheErrorIdFrom("pokez [1 2 3] 3 9")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("a string and a binary count from zero too")
        void countsEverySeriesTheSameWay() {
            assertThat(answerTo("s: \"abc\" pokez s 0 #\"z\" s")).isEqualTo("\"zbc\"");
            assertThat(answerTo("b: #{010203} pokez b 0 255 b")).isEqualTo("#{FF0203}");
        }
    }

    @Nested
    @DisplayName("TO-REAL-FILE")
    class TheRealFile {

        @Test
        @DisplayName("answers the absolute path of a file that is there")
        void answersAnAbsolutePath(@TempDir Path directory) throws Exception {
            java.nio.file.Files.writeString(directory.resolve("here.txt"), "x");
            assertThat(runWithFilesUnder(directory,
                    "file? to-real-file %here.txt")).isEqualTo(TRUE);
            assertThat(runWithFilesUnder(directory,
                    "true? find to-real-file %here.txt \"here.txt\"")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and none for a file that is not")
        void answersNoneForAMissingFile(@TempDir Path directory) {
            assertThat(runWithFilesUnder(directory,
                    "none? to-real-file %nowhere.txt")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("it needs the files service, like every other host call")
        void needsTheService() {
            assertThat(readTheErrorIdFrom("to-real-file %x")).isEqualTo("no-service");
        }

        @Test
        @DisplayName("a string is taken as well as a file")
        void takesAStringToo(@TempDir Path directory) throws Exception {
            java.nio.file.Files.writeString(directory.resolve("here.txt"), "x");
            assertThat(runWithFilesUnder(directory,
                    "file? to-real-file \"here.txt\"")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("RECYCLE and STATS")
    class Housekeeping {

        @Test
        @DisplayName("RECYCLE answers the bytes the collection released")
        void recycleAnswersWhatItReleased() {
            assertThat(answerTo("integer? recycle")).isEqualTo(TRUE);
            assertThat(answerTo("0 <= recycle")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/OFF answers nothing at all, and is the one form that does not collect")
        void offAnswersUnset() {
            assertThat(answerTo("unset? recycle/off")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/ON collects and answers a count, as the plain form does")
        void answersACountForOn() {
            assertThat(answerTo("integer? recycle/on")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("STATS answers a number")
        void statsAnswersANumber() {
            assertThat(answerTo("integer? stats")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/EVALS counts values the evaluator walked, and only rises")
        void onlyRaisesTheEvaluationCount() {
            assertThat(answerTo(
                    "before: stats/evals loop 20 [1 + 1] before <= stats/evals"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/TIMER answers a time")
        void answersATimeFromTheTimer() {
            assertThat(answerTo("time? stats/timer")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/DUMP-SERIES takes a pool number and answers none")
        void answersNoneFromTheSeriesDump() {
            assertThat(answerTo("none? stats/dump-series 1")).isEqualTo(TRUE);
            assertThat(answerTo("none? stats/dump-series -1")).isEqualTo(TRUE);
            assertThat(readTheErrorIdFrom("stats/dump-series")).isEqualTo("no-arg");
            assertThat(readTheErrorIdFrom("stats/dump-series \"1\"")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("STACK, which only a heap-framed evaluator can answer")
    class TheStack {

        @Test
        @DisplayName("an offset naming no frame answers none, whatever was asked for")
        void answersNoneWithoutAFrame() {
            assertThat(answerTo("none? stack/depth 99")).isEqualTo(TRUE);
            assertThat(answerTo("none? stack/size 99")).isEqualTo(TRUE);
            assertThat(answerTo("none? stack/word 99")).isEqualTo(TRUE);
            assertThat(answerTo("none? stack/word 1")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/DEPTH counts the frames that are open, inside a call")
        void depthCountsFrames() {
            assertThat(answerTo("f: func [] [integer? stack/depth 0] f"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("1 = stack/depth 0")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it is deeper the further in the call went")
        void deepensFurtherIn() {
            assertThat(answerTo(
                    "shallow: func [] [stack/depth 0] "
                    + "deep: func [] [shallow] shallow < deep")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/SIZE answers a number of values, inside a call")
        void answersACountForSize() {
            assertThat(answerTo("f: func [] [integer? stack/size 0] f"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/WORD at offset one names the function being run")
        void namesTheFunctionForWord() {
            assertThat(answerTo("f: func [] [stack/word 1] 'f = f")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and offset zero names STACK itself, even at the top")
        void namesStackItselfForWordAtZero() {
            assertThat(answerTo("'stack = stack/word 0")).isEqualTo(TRUE);
            assertThat(answerTo("'stack = first stack 0")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and -unnamed- for a call no word made, rather than the word before it")
        void namesNothingForANamelessCall() {
            assertThat(answerTo(
                    "f: func [] [stack/word 1] g: func [] [do reduce [:f]] form g"))
                    .isEqualTo("\"-unnamed-\"");
        }
    }

    @Nested
    @DisplayName("ECHO copies output to a file")
    class Echoing {

        @Test
        @DisplayName("what is printed reaches the file as well as the output")
        void copiesRatherThanRedirects(@TempDir Path directory) throws Exception {
            StringBuilder printed = new StringBuilder();
            Interpreter interpreter = Interpreter.createWithOutput(
                    printed::append, Bounds.standard().grant(HostService.FILES));
            interpreter.useFileSystem(FileSystemPort.rootFileSystem(directory));

            interpreter.run("echo %log.txt print \"hello\"");

            assertThat(printed.toString()).contains("hello");
            assertThat(java.nio.file.Files.readString(directory.resolve("log.txt")))
                    .contains("hello");
        }

        @Test
        @DisplayName("ECHO of none stops it, and what follows is not in the file")
        void stopsForNone(@TempDir Path directory) throws Exception {
            Interpreter interpreter = Interpreter.withBounds(
                    Bounds.standard().grant(HostService.FILES));
            interpreter.useFileSystem(FileSystemPort.rootFileSystem(directory));

            interpreter.run("echo %log.txt print \"kept\" echo none print \"dropped\"");

            String written = java.nio.file.Files.readString(directory.resolve("log.txt"));
            assertThat(written).contains("kept");
            assertThat(written).doesNotContain("dropped");
        }

        @Test
        @DisplayName("echoing again replaces rather than stacking")
        void replacesWhenEchoingAgain(@TempDir Path directory) throws Exception {
            Interpreter interpreter = Interpreter.withBounds(
                    Bounds.standard().grant(HostService.FILES));
            interpreter.useFileSystem(FileSystemPort.rootFileSystem(directory));

            interpreter.run(
                    "echo %one.txt print \"first\" echo %two.txt print \"second\"");

            assertThat(java.nio.file.Files.readString(directory.resolve("one.txt")))
                    .contains("first").doesNotContain("second");
            assertThat(java.nio.file.Files.readString(directory.resolve("two.txt")))
                    .contains("second").doesNotContain("first");
        }

        @Test
        @DisplayName("it needs the files service, but turning it off does not")
        void needsOnlyAGrantToWrite() {
            assertThat(readTheErrorIdFrom("echo %x.txt")).isEqualTo("no-service");
            assertThat(answerTo("unset? echo none")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("TTY? answers whether the input is a terminal")
        void ttyAnswersALogic() {
            assertThat(answerTo("logic? tty?")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("HALT stops the script and keeps the host")
    class Halting {

        @Test
        @DisplayName("a halted run reports that it halted, not that it failed")
        void saysTheRunHalted() {
            Interpreter interpreter = Interpreter.create();
            assertThat(interpreter.run("1 halt 2").conclusion().toString().toLowerCase())
                    .contains("halt");
        }

        @Test
        @DisplayName("and the interpreter still works after one")
        void leavesTheInterpreterAlive() {
            Interpreter interpreter = Interpreter.create();
            interpreter.run("halt");
            assertThat(interpreter.display(interpreter.run("1 + 1"))).isEqualTo("2");
        }

        @Test
        @DisplayName("nothing after the halt runs")
        void runsNothingAfterIt() {
            Interpreter interpreter = Interpreter.create();
            interpreter.defineFreshWordsIn("n: 0 halt n: 9");
            interpreter.run("n: 0 halt n: 9");
            assertThat(interpreter.display(interpreter.run("n"))).isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("SYSTEM/PLATFORM")
    class ThePlatform {

        @Test
        @DisplayName("names the operating system, as a word")
        void namesTheOperatingSystem() {
            assertThat(answerTo("word? system/platform")).isEqualTo(TRUE);
            assertThat(answerTo("find [Windows macOS Linux] system/platform"))
                    .as("the three this build is run on, spelt as Rebol spells them")
                    .isNotEqualTo("_");
        }

        @Test
        @DisplayName("and it agrees with what the JVM was told it is running on")
        void agreesWithTheJvm() {
            String reported = System.getProperty("os.name", "");
            String expected = reported.startsWith("Windows") ? "Windows"
                    : reported.startsWith("Mac") ? "macOS"
                    : reported.startsWith("Linux") ? "Linux"
                    : reported;
            assertThat(answerTo("system/platform")).isEqualTo(expected);
        }

        @Test
        @DisplayName("so the library's Windows branches take the right arm here")
        void thelibraryBranchesTheRightWay() {
            boolean onWindows = System.getProperty("os.name", "").startsWith("Windows");
            assertThat(answerTo("system/platform = 'Windows"))
                    .isEqualTo(onWindows ? TRUE : "#(false)");
            assertThat(answerTo("pick \";:\" system/platform = 'Windows"))
                    .as("sys-start.reb splits PATH on this")
                    .isEqualTo(onWindows ? "#\";\"" : "#\":\"");
        }
    }
}
