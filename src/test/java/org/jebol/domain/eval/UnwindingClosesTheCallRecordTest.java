package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A call that unwinds is no longer being run, however it left.
 *
 * <p>The evaluator keeps two things when it enters a function body: a frame
 * on a deque local to the walk, and an entry in the record of which calls are
 * open. The deque goes when the walk returns, whatever way it returns. The
 * record is a field, and it was closed only where a frame completes normally
 * -- so an error unwinding past the loop left every call it passed through
 * still recorded.
 *
 * <p>What that looked like: {@code stack/depth} climbing by one for every
 * error a script caught and never coming back down. A fresh interpreter
 * answered 2, five caught errors made it 7, five caught throws 12, and five
 * six-deep recursions 52. DS printed all of them. A long-lived interpreter
 * that catches errors -- which is every server -- accumulated phantom frames
 * without limit.
 *
 * <p>Each test below was run against the unfixed evaluator and failed. The
 * numbers in the comments are what it answered, because a regression test
 * that has never been red is a guess.
 *
 * <p>The property under all of them is the one worth stating on its own:
 * <em>an interpreter that has finished doing something is in the state it was
 * in before it started.</em> A test for one caught error would pass on code
 * that leaks, as long as it leaks consistently; the accumulation is what
 * names the defect, so every case here runs its subject repeatedly and
 * compares against the depth before.
 */
class UnwindingClosesTheCallRecordTest {

    /**
     * The depth of a fresh interpreter at the top level.
     *
     * <p>One, not zero: {@code Stack_Depth()} walks every DSF frame including
     * the frame of the STACK call doing the asking, so the question opens the
     * frame that answers it.
     */
    private static final String AT_REST = "1";

    private static Interpreter fresh() {
        return Interpreter.create();
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** What a piece of source wrote, rather than what it answered. */
    private static String printedBy(String source) {
        StringBuilder captured = new StringBuilder();
        Interpreter interpreter = Interpreter.writingTo(captured::append);
        interpreter.defineFreshWordsIn(source);
        interpreter.run(source);
        return captured.toString();
    }

    /** How many frame headings DS wrote while the source ran. */
    private static int framesPrintedBy(String source) {
        return printedBy(source).split("STACK\\[", -1).length - 1;
    }

    /** The depth after running a piece of source in a fresh interpreter. */
    private static String depthAfter(String source) {
        Interpreter interpreter = fresh();
        answerTo(interpreter, source);
        return answerTo(interpreter, "stack/depth 0");
    }

    @Nested
    @DisplayName("the interpreter starts and stays at rest")
    class AtRest {

        @Test
        @DisplayName("a fresh interpreter has one frame open, its own")
        void aFreshInterpreterIsAtRest() {
            assertThat(answerTo(fresh(), "stack/depth 0")).isEqualTo(AT_REST);
        }

        @Test
        @DisplayName("and the whole boot leaves nothing behind")
        void theBootLeavesNothingOpen() {
            assertThat(answerTo(fresh(), "1 = length? stack 0")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a call that returns normally closes its own record")
        void anOrdinaryCallCloses() {
            assertThat(depthAfter("f: func [] [1]  loop 20 [f]")).isEqualTo(AT_REST);
        }

        @Test
        @DisplayName("and so does a call nested twenty deep")
        void aDeepCallCloses() {
            assertThat(depthAfter("deep: func [n] [either n > 0 [deep n - 1] [1]]  deep 20"))
                    .isEqualTo(AT_REST);
        }
    }

    @Nested
    @DisplayName("an error unwinds without leaving the call recorded")
    class WhenAnErrorIsRaised {

        @Test
        @DisplayName("one caught error leaves the depth where it was")
        void oneCaughtErrorLeavesNothing() {
            assertThat(depthAfter("f: func [] [1 / 0]  try [f]")).isEqualTo(AT_REST);
        }

        @Test
        @DisplayName("and twenty do not accumulate, which is the whole defect")
        void manyCaughtErrorsDoNotAccumulate() {
            assertThat(depthAfter("f: func [] [1 / 0]  loop 20 [try [f]]"))
                    .isEqualTo(AT_REST);
        }

        @Test
        @DisplayName("an error raised six deep releases all six")
        void aDeepRaiseReleasesEveryFrame() {
            assertThat(depthAfter("""
                    deep: func [n] [either n > 0 [deep n - 1] [1 / 0]]
                    loop 20 [try [deep 6]]""")).isEqualTo(AT_REST);
        }

        @Test
        @DisplayName("DO of an error value unwinds the same way")
        void doingAnErrorValueUnwinds() {
            assertThat(depthAfter("""
                    m: func [] [do make error! {boom}]
                    loop 20 [try [m]]""")).isEqualTo(AT_REST);
        }

        @Test
        @DisplayName("and an error nobody catches leaves nothing open either")
        void anUncaughtErrorLeavesNothing() {
            Interpreter interpreter = fresh();
            answerTo(interpreter, "f: func [] [1 / 0]");
            answerTo(interpreter, "f");

            assertThat(answerTo(interpreter, "stack/depth 0")).isEqualTo(AT_REST);
        }
    }

    @Nested
    @DisplayName("every other way out of a call closes its record too")
    class TheOtherWaysOut {

        @Test
        @DisplayName("THROW caught by CATCH")
        void throwUnwindsCleanly() {
            assertThat(depthAfter("g: func [] [throw 1]  loop 20 [catch [g]]"))
                    .isEqualTo(AT_REST);
        }

        @Test
        @DisplayName("BREAK out of a loop")
        void breakUnwindsCleanly() {
            assertThat(depthAfter("k: func [] [break]  loop 20 [loop 1 [k]]"))
                    .isEqualTo(AT_REST);
        }

        @Test
        @DisplayName("RETURN, which was already right and stays that way")
        void returnUnwindsCleanly() {
            assertThat(depthAfter("h: func [] [return 1]  loop 20 [h]"))
                    .isEqualTo(AT_REST);
        }

        @Test
        @DisplayName("and the ways out mixed together, many times over")
        void theWaysOutMixed() {
            assertThat(depthAfter("""
                    f: func [] [1 / 0]
                    g: func [] [throw 1]
                    h: func [] [return 1]
                    k: func [] [break]
                    loop 20 [try [f] catch [g] h loop 1 [k]]""")).isEqualTo(AT_REST);
        }
    }

    @Nested
    @DisplayName("what the record is read for agrees with the depth")
    class TheReadersAgree {

        @Test
        @DisplayName("the backtrace holds one entry per open call")
        void theBacktraceMatchesTheDepth() {
            assertThat(depthAfter("f: func [] [1 / 0]  loop 20 [try [f]]"))
                    .isEqualTo(AT_REST);
            assertThat(answerTo(fresh(), """
                    f: func [] [1 / 0]  loop 20 [try [f]]
                    (length? stack 0) = stack/depth 0""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the backtrace names STACK alone once the errors are past")
        void theBacktraceIsJustStack() {
            assertThat(answerTo(fresh(), """
                    f: func [] [1 / 0]  loop 20 [try [f]]
                    mold stack 0""")).isEqualTo("\"[stack]\"");
        }

        @Test
        @DisplayName("DS prints the same frames after twenty caught errors as after none")
        void theStackDumpDoesNotGrow() {
            // DS prints its own frame, which a real 3.22.1 does too --
            // `STACK[33] ds[0] native!` at its top level. So the property is
            // that the print does not grow, not that it is empty.
            assertThat(framesPrintedBy("f: func [] [1 / 0]  loop 20 [try [f]]  ds"))
                    .as("caught errors must not add frames to what DS prints")
                    .isEqualTo(framesPrintedBy("ds"));
        }

        @Test
        @DisplayName("and still names the call that really is open")
        void theStackDumpStillShowsARealFrame() {
            // Not a count: JEBOL's DS prints the innermost frame where a real
            // 3.22.1 prints the whole chain down through DO -- a divergence
            // of its own and nothing to do with this defect. What matters
            // here is that a live call is still named after twenty dead ones.
            assertThat(printedBy("f: func [] [1 / 0]  loop 20 [try [f]]  g: func [] [ds]  g"))
                    .contains("g[0]");
        }
    }

    @Nested
    @DisplayName("the depth still rises for calls that really are open")
    class TheCountIsNotSimplyPinned {

        @Test
        @DisplayName("inside one call it is one more than at rest")
        void insideACallItRises() {
            assertThat(answerTo(fresh(), "f: func [] [stack/depth 0]  f")).isEqualTo("2");
        }

        @Test
        @DisplayName("and deeper in, deeper still")
        void deeperInItRisesFurther() {
            assertThat(answerTo(fresh(), """
                    shallow: func [] [stack/depth 0]
                    deeper: func [] [shallow]
                    deepest: func [] [deeper]
                    all [shallow < deeper  deeper < deepest]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("which is what makes the fix a fix rather than a constant")
        void theFixIsNotAConstant() {
            assertThat(answerTo(fresh(), """
                    f: func [] [1 / 0]
                    before: stack/depth 0
                    try [f]
                    inside: 0
                    g: func [] [inside: stack/depth 0]
                    g
                    all [before = 1  inside = 2  1 = stack/depth 0]"""))
                    .isEqualTo("#(true)");
        }
    }
}
