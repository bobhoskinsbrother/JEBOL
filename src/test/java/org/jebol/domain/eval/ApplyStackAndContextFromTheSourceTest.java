package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * APPLY adjusts the block to the whole frame in two lines --
 * {@code if (len &lt; n) n = len;} then {@code for (; n &lt; len; n++) DS_PUSH_NONE;} --
 * and jumps rather than calling DO when handed the DO native. STACK counts its own
 * frame as zero and answers none for an offset naming nothing,
 * {@code sp = Stack_Frame(index); if (!sp) return R_NONE;}. CONTEXT? tells the
 * three kinds of frame apart, {@code if (IS_INT_SERIES(VAL_WORD_FRAME(word)))
 * return R_NONE;} for a loop's.
 */
class ApplyStackAndContextFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("APPLY makes the block as long as the frame")
    class TheWholeFrame {

        @Test
        @DisplayName("values past the frame's end are dropped")
        void theExtraValuesAreDropped() {
            assertThat(answerTo("""
                    takes-one: func [a] [a]
                    (apply :takes-one [1 2 3]) = 1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a short block is padded out with none")
        void aShortBlockIsPadded() {
            assertThat(answerTo("""
                    takes-two: func [a b] [reduce [a b]]
                    (apply :takes-two [1]) = [1 #(none)]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a function taking nothing accepts an empty block")
        void anEmptyFrameTakesAnEmptyBlock() {
            assertThat(answerTo("""
                    takes-nothing: func [] [7]
                    (apply :takes-nothing []) = 7""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and drops everything a longer block offers it")
        void anEmptyFrameDropsWhatItIsOffered() {
            assertThat(answerTo("""
                    takes-nothing: func [] [7]
                    (apply :takes-nothing [1 2 3]) = 7""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the refinements and their arguments are frame slots too")
        void theRefinementsAreInTheFrame() {
            assertThat(answerTo("""
                    with-a-refinement: func [a /b c] [reduce [a b c]]
                    (apply :with-a-refinement [1 2 3]) = [1 #(true) 3]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the block is reduced first")
        void theBlockIsReduced() {
            assertThat(answerTo("""
                    takes-one: func [a] [a]
                    (apply :takes-one [1 + 1]) = 2""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and /ONLY takes the values exactly as written")
        void onlyTakesTheValuesAsWritten() {
            assertThat(answerTo("""
                    takes-one: func [a] [a]
                    (apply/only :takes-one [1 + 1]) = 1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a path is not a function value and is refused")
        void aPathIsRefused() {
            assertThat(answerTo("""
                    holder: object [f: func [/a] []]
                    error? try [apply 'holder/f [true]]""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("APPLY of DO applies what the block starts with")
    class TheReapplication {

        @Test
        @DisplayName("the function at the head is applied to the rest")
        void doReappliesTheHeadFunction() {
            assertThat(answerTo("""
                    (apply :do [:add 1 1]) = 2""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a chain of DOs collapses")
        void aChainOfDosCollapses() {
            assertThat(answerTo("""
                    (apply :do [:do :add 1 1]) = 2""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("APPLY knows an operator's arity is two")
    class TheOperatorFrame {

        @Test
        @DisplayName("an operator takes both values from the block")
        void anOperatorTakesTwo() {
            assertThat(answerTo("""
                    (apply :+ [1 2]) = 3""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the action behind it counts the same")
        void theActionCountsTheSame() {
            assertThat(answerTo("""
                    (apply :add [1 2]) = 3""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a one-value block pads the second slot, which the argument check refuses")
        void aShortOperatorBlockIsPaddedAndRefused() {
            assertThat(answerTo("""
                    e: try [apply :+ [1]] e/id = 'expect-arg""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("STACK counts its own call as frame zero")
    class TheStackOffsets {

        @Test
        @DisplayName("offset zero answers a block")
        void offsetZeroIsABlock() {
            assertThat(answerTo("""
                    block? stack 0""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("whose first word is STACK itself")
        void theBacktraceBeginsWithStack() {
            assertThat(answerTo("""
                    'stack = first stack 0""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and /WORD of zero names STACK as well")
        void theWordOfZeroIsStack() {
            assertThat(answerTo("""
                    'stack = stack/word 0""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("offset one names the call STACK was written inside")
        void offsetOneNamesTheEnclosingCall() {
            assertThat(answerTo("""
                    asks-where-it-is: func [] [stack/word 1]
                    'asks-where-it-is = asks-where-it-is""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an offset naming no frame answers none")
        void anOffsetPastTheEndIsNone() {
            assertThat(answerTo("""
                    none? stack 999""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("whatever refinement was asked for")
        void anOffsetPastTheEndIsNoneUnderARefinement() {
            assertThat(answerTo("""
                    none? stack/word 999""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a negative offset answers none too")
        void aNegativeOffsetIsNone() {
            assertThat(answerTo("""
                    none? stack -1""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("CONTEXT? tells the three kinds of frame apart")
    class TheKindsOfFrame {

        @Test
        @DisplayName("a FOREACH word lives in a loop frame, which is none")
        void aForeachWordHasNoContext() {
            assertThat(answerTo("""
                    none? foreach x [1] [context? 'x]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a FOR word does as well")
        void aForWordHasNoContext() {
            assertThat(answerTo("""
                    none? for counter 1 1 1 [context? 'counter]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a REPEAT word")
        void aRepeatWordHasNoContext() {
            assertThat(answerTo("""
                    none? repeat counter 1 [context? 'counter]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a function's argument answers the function itself")
        void aFunctionsArgumentAnswersTheFunction() {
            assertThat(answerTo("""
                    asks-its-own-frame: func [arg1 arg2] [context? 'arg1]
                    same? :asks-its-own-frame asks-its-own-frame 1 2"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("which is how a body reaches its own spec")
        void aFunctionReachesItsOwnSpec() {
            assertThat(answerTo("""
                    asks-its-own-spec: func [arg1 arg2] [spec-of context? 'arg1]
                    (asks-its-own-spec 1 2) = [arg1 arg2]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an inner function answers its own frame, not the outer one")
        void anInnerFunctionAnswersItsOwn() {
            assertThat(answerTo("""
                    outer: func [arg1 arg2 /local inner] [
                        inner: func [a] [spec-of context? 'a]
                        inner arg1
                    ]
                    (outer 1 2) = [a]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a word kept past the end of its call still names a frame")
        void aWordOutlivingItsCallStillNamesAFrame() {
            assertThat(answerTo("""
                    keeps-its-word: func [arg] [kept: 'arg]
                    keeps-its-word 1
                    same? :do context? kept""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a closure's argument answers a real object holding it")
        void aClosuresArgumentAnswersAnObject() {
            assertThat(answerTo("""
                    closes-over-it: closure [a] [context? 'a]
                    frame: closes-over-it 1
                    all [object? frame frame/a = 1]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and USE's scope is a closure frame, so it is an object")
        void useBuildsAnObject() {
            assertThat(answerTo("""
                    object? context? use [x] ['x]""")).isEqualTo("#(true)");
        }
    }
}
