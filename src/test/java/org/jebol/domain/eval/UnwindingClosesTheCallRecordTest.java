package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnwindingClosesTheCallRecordTest {

    private static final String AT_REST = "1";

    private static Interpreter fresh() {
        return Interpreter.create();
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String printedBy(String source) {
        StringBuilder captured = new StringBuilder();
        Interpreter interpreter = Interpreter.writingTo(captured::append);
        interpreter.defineFreshWordsIn(source);
        interpreter.run(source);
        return captured.toString();
    }

    private static int framesPrintedBy(String source) {
        return printedBy(source).split("STACK\\[", -1).length - 1;
    }

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
            assertThat(framesPrintedBy("f: func [] [1 / 0]  loop 20 [try [f]]  ds"))
                    .as("caught errors must not add frames to what DS prints")
                    .isEqualTo(framesPrintedBy("ds"));
        }

        @Test
        @DisplayName("and still names the call that really is open")
        void theStackDumpStillShowsARealFrame() {
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
