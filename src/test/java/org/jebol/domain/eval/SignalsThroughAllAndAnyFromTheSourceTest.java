package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RETURN, BREAK and THROW raised inside an ALL, an ANY or a CASE.
 *
 * <p>Those three natives walk their block an expression at a time rather than
 * handing it to DO, because each has to look at every value the expression
 * produced before deciding whether to go on. JEBOL disarmed the signals at
 * that step: a RETURN became the error "a return outside a function" even
 * though the function it belonged to was still running.
 *
 * <p>What that broke was not a corner. Rebol's own ENCODE opens with
 * {@code unless all [cod: select system/codecs type data: either ...]}, and
 * the TEXT codec returns from inside it, so {@code encode 'text [1 2]} raised
 * instead of answering -- and every assertion after it in the codecs file was
 * never reached.
 *
 * <p>The signals still have to stop somewhere: nothing a script does may reach
 * the host as a throwable. That somewhere is the end of the run, where a
 * stray one becomes the error it should be.
 */
class SignalsThroughAllAndAnyFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("a RETURN, which belongs to the function the ALL is inside")
    class TheReturn {

        @Test
        @DisplayName("straight out of an ALL")
        void straightOutOfAnAll() {
            assertThat(answerTo("""
                    do func [][all [return 1] 2]""")).isEqualTo("1");
        }

        @Test
        @DisplayName("and out of an ANY")
        void outOfAnAny() {
            assertThat(answerTo("""
                    do func [][any [false return 6] 2]""")).isEqualTo("6");
        }

        @Test
        @DisplayName("and out of a CASE, which walks its block the same way")
        void outOfACase() {
            assertThat(answerTo("""
                    do func [][case [all [true] [return 7]] 2]""")).isEqualTo("7");
        }

        @Test
        @DisplayName("from a branch the ALL only reached part way through its block")
        void fromPartWayThrough() {
            assertThat(answerTo("""
                    do func [][unless all [x: 1 y: either true [return 2][3]][4]]"""))
                    .isEqualTo("2");
        }

        @Test
        @DisplayName("and through a loop the ALL is inside, which it leaves as well")
        void throughALoopAsWell() {
            assertThat(answerTo("""
                    do func [][loop 3 [all [return 5]] 9]""")).isEqualTo("5");
        }

        @Test
        @DisplayName("the ALL still answers normally when nothing returns")
        void theAllStillAnswersNormally() {
            assertThat(answerTo("""
                    reduce [all [1 2 3] all [1 false 3] any [false 2] any [false false]]"""))
                    .isEqualTo("[3 _ 2 _]");
        }
    }

    @Nested
    @DisplayName("a BREAK, which belongs to the loop the ALL is inside")
    class TheBreak {

        @Test
        @DisplayName("leaves the loop rather than the ALL")
        void leavesTheLoop() {
            assertThat(answerTo("""
                    loop 2 [all [break]] "after\"""")).isEqualTo("\"after\"");
        }

        @Test
        @DisplayName("and a CONTINUE goes round again")
        void aContinueGoesRoundAgain() {
            assertThat(answerTo("""
                    count: 0
                    loop 3 [all [count: count + 1 continue] count: 100]
                    count""")).isEqualTo("3");
        }
    }

    @Nested
    @DisplayName("a THROW, which the CATCH outside the ALL takes")
    class TheThrow {

        @Test
        @DisplayName("caught by the CATCH the ALL sits inside")
        void caughtOutside() {
            assertThat(answerTo("""
                    catch [all [throw 9]]""")).isEqualTo("9");
        }

        @Test
        @DisplayName("and a named one goes to the CATCH that names it")
        void aNamedOneGoesToItsCatch() {
            assertThat(answerTo("""
                    catch/name [all [throw/name 3 'here]] 'here""")).isEqualTo("3");
        }
    }

    @Nested
    @DisplayName("where the signals do stop: the end of the run")
    class TheBoundary {

        @Test
        @DisplayName("a RETURN with no function left becomes an error")
        void aReturnWithNoFunction() {
            assertThat(Interpreter.create().run("all [return 1]").errorId())
                    .contains("return");
        }

        @Test
        @DisplayName("a BREAK with no loop left becomes one too")
        void aBreakWithNoLoop() {
            assertThat(Interpreter.create().run("all [break]").errorId())
                    .contains("break");
        }

        @Test
        @DisplayName("and a THROW nothing caught")
        void aThrowNothingCaught() {
            assertThat(Interpreter.create().run("all [throw 1]").errorId())
                    .contains("throw");
        }
    }

    @Nested
    @DisplayName("the borrowed ENCODE, which is what found this")
    class TheBorrowedEncode {

        @Test
        @DisplayName("the TEXT codec returns from inside an ALL and answers a string")
        void theTextCodecAnswers() {
            assertThat(answerTo("""
                    encode 'text [1 2]""")).isEqualTo("\"1 2\"");
        }

        @Test
        @DisplayName("and a binary comes back as its own letters rather than molded")
        void aBinaryComesBackAsItsLetters() {
            assertThat(answerTo("""
                    encode 'text #{414243}""")).isEqualTo("\"ABC\"");
        }
    }
}
