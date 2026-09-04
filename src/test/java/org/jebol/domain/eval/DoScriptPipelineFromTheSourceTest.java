package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DO routes a string and a binary through sys/do*, which loads and then runs
 * {@code do/next body mark}. Three things follow: a string steps like a block, a
 * top-level RETURN lands on that function's frame rather than escaping, and a
 * binary is read as the script it is -- header, {@code length:} bound and
 * {@code needs:} check included. Anything unsteppable answers itself and sets the
 * word to none, {@code Set_Var(D_ARG(5), NONE_VALUE)}.
 */
class DoScriptPipelineFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("DO/NEXT steps a string as it steps a block")
    class SteppingAString {

        @Test
        @DisplayName("the first expression of a string is answered")
        void aStringStepsOneExpression() {
            assertThat(answerTo("""
                    (do/next "1 + 1 5" 'rest) = 2""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the word is moved on to what the load left")
        void aStringLeavesTheRestInTheWord() {
            assertThat(answerTo("""
                    do/next "1 + 1 5" 'rest rest = [5]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an empty block steps to nothing and leaves the word at the tail")
        void anEmptyBlockStepsToNothing() {
            assertThat(answerTo("""
                    do/next [] 'rest all [tail? rest block? rest]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the answer for an empty block is an absence, not none")
        void anEmptyBlockAnswersAnAbsence() {
            assertThat(answerTo("""
                    unset? do/next [] 'rest""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an empty string does the same")
        void anEmptyStringStepsToNothing() {
            assertThat(answerTo("""
                    do/next {} 'rest tail? rest""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("anything unsteppable answers itself and clears the word")
    class TheUnsteppable {

        @Test
        @DisplayName("an integer answers itself")
        void anIntegerAnswersItself() {
            assertThat(answerTo("""
                    (do/next 5 'rest) = 5""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and sets the word to none, which is the signal to stop")
        void anIntegerSetsTheWordToNone() {
            assertThat(answerTo("""
                    do/next 5 'rest none? rest""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a binary is unsteppable too")
        void aBinaryAnswersItself() {
            assertThat(answerTo("""
                    (do/next #{01} 'rest) = #{01}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and none is unsteppable, so the word comes back none as well")
        void noneClearsTheWord() {
            assertThat(answerTo("""
                    do/next none 'rest none? rest""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a top-level RETURN inside a DO'd string is caught by the DO")
    class TheCaughtReturn {

        @Test
        @DisplayName("DO answers what the RETURN was given")
        void doAnswersTheReturnedValue() {
            assertThat(answerTo("""
                    (do "return 5") = 5""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the calling function goes on running")
        void theCallerIsNotUnwound() {
            assertThat(answerTo("""
                    keeps-going: func [] [do "return 5" 9]
                    keeps-going = 9""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a nested DO catches its own string's RETURN")
        void aNestedDoCatchesItsOwn() {
            assertThat(answerTo("""
                    (do {do "return 5"}) = 5""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a string with no RETURN answers its last expression")
        void aStringWithoutAReturn() {
            assertThat(answerTo("""
                    (do "1 + 1") = 2""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("DO of a binary runs it as the script it is")
    class TheBinaryScript {

        @Test
        @DisplayName("the header is read and the body evaluated")
        void aHeaderedBinaryRuns() {
            assertThat(answerTo("""
                    (do to binary! "REBOL [] 1 + 1") = 2""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the header's length bounds the body, so trailing bytes are left")
        void theDeclaredLengthBoundsTheBody() {
            assertThat(answerTo("""
                    (do to binary! "REBOL [length: 6] 1 + 1 9 + 9") = 2"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and without a length the whole of it runs")
        void withoutALengthTheWholeBodyRuns() {
            assertThat(answerTo("""
                    (do to binary! "REBOL [] 1 + 1 9 + 9") = 18""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a needs the interpreter cannot meet refuses with the needs id")
        void anUnmetNeedsIsRefused() {
            assertThat(answerTo("""
                    e: try [do to binary! "REBOL [needs: 99.0.0] 1"] e/id = 'needs"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a needs the interpreter does meet runs the script")
        void aMetNeedsRuns() {
            assertThat(answerTo("""
                    (do to binary! "REBOL [needs: 0.0.0] 42") = 42""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a top-level RETURN is caught here as well")
        void aBinaryScriptsReturnIsCaught() {
            assertThat(answerTo("""
                    (do to binary! "REBOL [] return 7") = 7""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("bytes that are not text at all are refused before any header is looked for")
        void bytesThatAreNotTextAreRefused() {
            assertThat(answerTo("""
                    e: try [do #{00FF}] e/id = 'invalid-chars""")).isEqualTo("#(true)");
        }
    }
}
