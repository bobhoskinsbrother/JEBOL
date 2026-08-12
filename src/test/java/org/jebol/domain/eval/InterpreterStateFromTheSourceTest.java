package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.jebol.application.Bounds;
import org.jebol.application.FileSystemPort;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The functions that ask the interpreter about itself: VERSION, POKEZ,
 * TO-REAL-FILE, RECYCLE, STATS, HALT and STACK.
 *
 * <p>Read out of {@code b-init.c} (version), {@code f-series.c} (pokez),
 * {@code n-io.c} (to-real-file) and {@code n-system.c}. Each spec is the one
 * declared there, verbatim.
 *
 * <p>Grouped because they share one property: each is a window onto something
 * the host owns, so each needs the host's answer rather than a rule from the
 * C. STACK is the exception and the interesting one -- it is answerable at all
 * only because evaluation state lives in frames on the heap rather than in JVM
 * stack frames. See decision 1.
 *
 * <p>Specified in {@code spec/natives.allium} under "The interpreter's own
 * state".
 */
class InterpreterStateFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static String withFilesUnder(Path directory, String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.FILES));
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("VERSION")
    class TheVersion {

        @Test
        @DisplayName("answers text")
        void itAnswersText() {
            assertThat(answerTo("string? version")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/DATA answers a tuple, which is what a header can compare")
        void theDataFormIsATuple() {
            // A module header writes `Needs: 3.5.0` and compares it against
            // this. A string will not compare with a tuple, which is why the
            // C has two forms rather than one.
            assertThat(answerTo("tuple? version/data")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the tuple is the same version the text names")
        void theTwoFormsAgree() {
            assertThat(answerTo("find version form version/data")).isNotEqualTo("_");
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
        void itAnswersTheValue() {
            assertThat(answerTo("pokez [1 2 3] 0 9")).isEqualTo("9");
        }

        @Test
        @DisplayName("the last element is one less than the length")
        void theLastIsLengthLessOne() {
            assertThat(answerTo("b: [1 2 3] pokez b 2 9 mold b")).isEqualTo("\"[1 2 9]\"");
        }

        @Test
        @DisplayName("a negative index is not shifted, because it counts from the tail")
        void aNegativeIndexIsNotShifted() {
            // `if (VAL_INT64(D_ARG(2)) >= 0 && !IS_BITSET(D_ARG(1))) ... += 1;`
            // -- counting back from the tail means the same in both
            // conventions, so there is nothing to adjust.
            assertThat(answerTo("b: [1 2 3] e: try [pokez b -1 9] error? e")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a bitset is not shifted either, because its index is a code")
        void aBitsetIsNotShifted() {
            // A bitset's index is a character code rather than a position, so
            // adding one to it would name a different character.
            assertThat(answerTo("b: charset \"a\" pokez b 122 true b/(#\"z\")"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("b: charset \"a\" pokez b 122 true b/(#\"a\")"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("past the end is refused, as POKE refuses it")
        void pastTheEndIsRefused() {
            assertThat(errorIdFrom("pokez [1 2 3] 3 9")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("a string and a binary count from zero too")
        void everySeriesCountsTheSameWay() {
            assertThat(answerTo("s: \"abc\" pokez s 0 #\"z\" s")).isEqualTo("\"zbc\"");
            assertThat(answerTo("b: #{010203} pokez b 0 255 b")).isEqualTo("#{FF0203}");
        }
    }

    @Nested
    @DisplayName("TO-REAL-FILE")
    class TheRealFile {

        @Test
        @DisplayName("answers the absolute path of a file that is there")
        void itAnswersAnAbsolutePath(@TempDir Path directory) throws Exception {
            java.nio.file.Files.writeString(directory.resolve("here.txt"), "x");
            assertThat(withFilesUnder(directory,
                    "file? to-real-file %here.txt")).isEqualTo(TRUE);
            assertThat(withFilesUnder(directory,
                    "true? find to-real-file %here.txt \"here.txt\"")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and none for a file that is not")
        void itAnswersNoneForAMissingFile(@TempDir Path directory) {
            // None rather than a failure: the question is "what is this
            // really", and "nothing" is a true answer to it. The C's own
            // summary says so.
            assertThat(withFilesUnder(directory,
                    "none? to-real-file %nowhere.txt")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("it needs the files service, like every other host call")
        void itNeedsTheService() {
            assertThat(errorIdFrom("to-real-file %x")).isEqualTo("no-service");
        }

        @Test
        @DisplayName("a string is taken as well as a file")
        void aStringIsTakenToo(@TempDir Path directory) throws Exception {
            java.nio.file.Files.writeString(directory.resolve("here.txt"), "x");
            assertThat(withFilesUnder(directory,
                    "file? to-real-file \"here.txt\"")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("RECYCLE and STATS")
    class Housekeeping {

        @Test
        @DisplayName("RECYCLE answers the bytes the collection released")
        void recycleAnswersWhatItReleased() {
            // Released, not in use. `released_bytes = Recycle(TRUE, ...);
            // DS_Ret_Int(released_bytes);` -- and the two readings move in
            // opposite directions as a program allocates, so which one it is
            // matters. A collector that freed nothing answers zero.
            assertThat(answerTo("integer? recycle")).isEqualTo(TRUE);
            assertThat(answerTo("0 <= recycle")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/OFF answers nothing at all, and is the one form that does not collect")
        void offAnswersUnset() {
            // `if (D_REF(1)) { GC_Active = FALSE; return R_UNSET; }` -- the
            // first thing the C does, before it collects anything.
            assertThat(answerTo("unset? recycle/off")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/ON collects and answers a count, as the plain form does")
        void onAnswersACount() {
            assertThat(answerTo("integer? recycle/on")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("STATS answers a number")
        void statsAnswersANumber() {
            assertThat(answerTo("integer? stats")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/EVALS counts values the evaluator walked, and only rises")
        void theEvaluationCountOnlyRises() {
            // The one refinement with a rule rather than a reading. It must
            // never go backwards inside one interpreter, or a caller timing a
            // stretch of work would read a negative difference.
            assertThat(answerTo(
                    "before: stats/evals loop 20 [1 + 1] before <= stats/evals"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/TIMER answers a time")
        void theTimerAnswersATime() {
            assertThat(answerTo("time? stats/timer")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/DUMP-SERIES takes a pool number and answers none")
        void theSeriesDumpAnswersNone() {
            // `Dump_Series_In_Pool(VAL_INT32(pool_id)); return R_NONE;` -- the
            // answer is none in Rebol too. What is missing here is the printing,
            // which walks Rebol's own memory pools: a JVM has not got them, so
            // there is nothing to walk and nothing to print. The value a caller
            // gets is the same either way.
            //
            // `-1` means every pool, says the help text, and it is not a
            // special case in the answer.
            assertThat(answerTo("none? stats/dump-series 1")).isEqualTo(TRUE);
            assertThat(answerTo("none? stats/dump-series -1")).isEqualTo(TRUE);
            assertThat(errorIdFrom("stats/dump-series")).isEqualTo("no-arg");
            assertThat(errorIdFrom("stats/dump-series \"1\"")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("STACK, which only a heap-framed evaluator can answer")
    class TheStack {

        @Test
        @DisplayName("an offset naming no frame answers none, whatever was asked for")
        void noFrameAnswersNone() {
            // The C tests this before it reads a single refinement:
            //     sp = Stack_Frame(index);
            //     if (!sp) return R_NONE;
            // At the top level there is no frame at all -- `for (dsf = DSF;
            // dsf > 0; ...)` does not run when DSF is zero -- so every form
            // answers none there, and a caller walking outwards can tell
            // where the stack ends.
            assertThat(answerTo("none? stack/depth 0")).isEqualTo(TRUE);
            assertThat(answerTo("none? stack/size 0")).isEqualTo(TRUE);
            assertThat(answerTo("none? stack/word 99")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/DEPTH counts the frames that are open, inside a call")
        void depthCountsFrames() {
            assertThat(answerTo("f: func [] [integer? stack/depth 0] f"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it is deeper the further in the call went")
        void itIsDeeperFurtherIn() {
            // The property worth testing: the number tracks the real nesting
            // rather than being a constant that happens to be plausible.
            assertThat(answerTo(
                    "shallow: func [] [stack/depth 0] "
                    + "deep: func [] [shallow] shallow < deep")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/SIZE answers a number of values, inside a call")
        void sizeAnswersACount() {
            assertThat(answerTo("f: func [] [integer? stack/size 0] f"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/WORD names the function being run, when there is one")
        void wordNamesTheFunction() {
            assertThat(answerTo("f: func [] [stack/word 0] 'f = f")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and answers none at the top, where no frame is open")
        void wordIsNoneAtTheTop() {
            assertThat(answerTo("none? stack/word 0")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and none for a call no word made, rather than the word before it")
        void wordIsNoneForANamelessCall() {
            // `if (!word) word = ROOT_NONAME;` -- a function value standing in a
            // block is called with no name attached, and Rebol's own ALL-OF
            // builds exactly that with `reduce [:unless ...]`. The name has to
            // be cleared as the call is made or the frame reports whichever
            // word was called before it.
            assertThat(answerTo(
                    "f: func [] [stack/word 0] g: func [] [do reduce [:f]] none? g"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("ECHO copies output to a file")
    class Echoing {

        @Test
        @DisplayName("what is printed reaches the file as well as the output")
        void itCopiesRatherThanRedirects(@TempDir Path directory) throws Exception {
            // A copy, not a redirection: the C's own summary is "Copies
            // console output to a file", so a script that echoes still prints.
            StringBuilder printed = new StringBuilder();
            Interpreter interpreter = Interpreter.writingTo(
                    printed::append, Bounds.standard().granting(HostService.FILES));
            interpreter.useFileSystem(FileSystemPort.rootedAt(directory));

            interpreter.run("echo %log.txt print \"hello\"");

            assertThat(printed.toString()).contains("hello");
            assertThat(java.nio.file.Files.readString(directory.resolve("log.txt")))
                    .contains("hello");
        }

        @Test
        @DisplayName("ECHO of none stops it, and what follows is not in the file")
        void noneStopsIt(@TempDir Path directory) throws Exception {
            Interpreter interpreter = Interpreter.withBounds(
                    Bounds.standard().granting(HostService.FILES));
            interpreter.useFileSystem(FileSystemPort.rootedAt(directory));

            interpreter.run("echo %log.txt print \"kept\" echo none print \"dropped\"");

            String written = java.nio.file.Files.readString(directory.resolve("log.txt"));
            assertThat(written).contains("kept");
            assertThat(written).doesNotContain("dropped");
        }

        @Test
        @DisplayName("echoing again replaces rather than stacking")
        void echoingAgainReplaces(@TempDir Path directory) throws Exception {
            // `Echo_File(0)` runs before the C looks at the argument, so the
            // previous echo is off by the time the new one starts.
            Interpreter interpreter = Interpreter.withBounds(
                    Bounds.standard().granting(HostService.FILES));
            interpreter.useFileSystem(FileSystemPort.rootedAt(directory));

            interpreter.run(
                    "echo %one.txt print \"first\" echo %two.txt print \"second\"");

            assertThat(java.nio.file.Files.readString(directory.resolve("one.txt")))
                    .contains("first").doesNotContain("second");
            assertThat(java.nio.file.Files.readString(directory.resolve("two.txt")))
                    .contains("second").doesNotContain("first");
        }

        @Test
        @DisplayName("it needs the files service, but turning it off does not")
        void theGrantIsForWritingOnly() {
            assertThat(errorIdFrom("echo %x.txt")).isEqualTo("no-service");
            // Turning echoing off writes nothing, so it asks for nothing.
            assertThat(answerTo("unset? echo none")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("TTY? answers whether the input is a terminal")
        void ttyAnswersALogic() {
            // Under a test runner it is not one, which is the answer that
            // matters: a script asking this is deciding whether to prompt.
            assertThat(answerTo("logic? tty?")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("HALT stops the script and keeps the host")
    class Halting {

        @Test
        @DisplayName("a halted run reports that it halted, not that it failed")
        void aHaltedRunSaysSo() {
            // Not QUIT: quitting ends the host's run and halting does not, so
            // a console that halts is a console afterwards. Both are signals
            // rather than answers, which is the only thing they share.
            Interpreter interpreter = Interpreter.create();
            assertThat(interpreter.run("1 halt 2").conclusion().toString().toLowerCase())
                    .contains("halt");
        }

        @Test
        @DisplayName("and the interpreter still works after one")
        void theInterpreterSurvives() {
            Interpreter interpreter = Interpreter.create();
            interpreter.run("halt");
            assertThat(interpreter.display(interpreter.run("1 + 1"))).isEqualTo("2");
        }

        @Test
        @DisplayName("nothing after the halt runs")
        void nothingAfterItRuns() {
            Interpreter interpreter = Interpreter.create();
            interpreter.defineFreshWordsIn("n: 0 halt n: 9");
            interpreter.run("n: 0 halt n: 9");
            assertThat(interpreter.display(interpreter.run("n"))).isEqualTo("0");
        }
    }
}
