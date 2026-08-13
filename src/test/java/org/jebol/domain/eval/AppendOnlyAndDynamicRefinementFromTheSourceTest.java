package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * /ONLY is the difference between adding a series and adding its items, and a
 * paren is added whole either way. A refinement written {@code /:word} is granted
 * or declined by what the word holds when the call is made -- Rebol's own
 * "Dynamic refinements" group writes {@code repend/:only s [1 + 2 3 * 4]} twice
 * with the word set differently -- and a granted one naming no declared
 * refinement is refused with no-refine.
 */
class AppendOnlyAndDynamicRefinementFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("/ONLY adds the series rather than its items")
    class TheOnlyRefinement {

        @Test
        @DisplayName("a block appended whole is one item")
        void aBlockGoesInWhole() {
            assertThat(answerTo("""
                    (append/only copy [] [9 8]) = [[9 8]]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("where without it the items are spread")
        void aBlockSpreadsWithoutIt() {
            assertThat(answerTo("""
                    (append copy [] [9 8]) = [9 8]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a paren goes in whole without being asked")
        void aParenGoesInWholeAnyway() {
            assertThat(answerTo("""
                    (append copy [] quote (9 8)) = [(9 8)]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and /ONLY leaves it exactly as it was")
        void aParenIsUnchangedByOnly() {
            assertThat(answerTo("""
                    (append/only copy [] quote (9 8)) = [(9 8)]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a string is one item with or without it")
        void aStringIsOneItemEitherWay() {
            assertThat(answerTo("""
                    all [
                        (append copy [] "ab") = ["ab"]
                        (append/only copy [] "ab") = ["ab"]
                    ]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an empty block spreads to nothing")
        void anEmptyBlockAddsNothing() {
            assertThat(answerTo("""
                    (append copy [1] []) = [1]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and under /ONLY is one empty item")
        void anEmptyBlockUnderOnlyIsAnItem() {
            assertThat(answerTo("""
                    (append/only copy [1] []) = [1 []]""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a refinement granted by a word")
    class TheDynamicRefinement {

        @Test
        @DisplayName("a truthy word grants it, so REPEND keeps the block whole")
        void atruthyWordGrantsIt() {
            assertThat(answerTo("""
                    only: yes gathered: []
                    repend/:only gathered [1 + 2 3 * 4]
                    gathered == [[3 12]]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a false one declines it, so the values are spread")
        void aFalseWordDeclinesIt() {
            assertThat(answerTo("""
                    only: no gathered: []
                    repend/:only gathered [4 + 5 6 * 7]
                    gathered == [9 42]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a declined refinement leaves its argument unread")
        void aDeclinedRefinementLeavesItsArgument() {
            assertThat(answerTo("""
                    part: no length: 10
                    found: find/:part "abcdef" "d" length
                    found == {def}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("where a granted one reads it and bounds the search")
        void agrantedRefinementReadsItsArgument() {
            assertThat(answerTo("""
                    part: yes length: 2
                    none? find/:part "abcdef" "d" length""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a granted refinement the function has not got is refused")
        void agrantedRefinementThatIsNotThere() {
            assertThat(answerTo("""
                    made-up: yes
                    e: try [append/:made-up copy [] 1] e/id = 'no-refine"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("while a declined one asks for nothing and so is never refused")
        void adeclinedRefinementThatIsNotThere() {
            assertThat(answerTo("""
                    made-up: no
                    (append/:made-up copy [] 1) = [1]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a refinement written out that the function has not got is refused too")
        void aliteralRefinementThatIsNotThere() {
            assertThat(answerTo("""
                    e: try [append/nosuchthing copy [] 1] e/id = 'no-refine"""))
                    .isEqualTo("#(true)");
        }
    }
}
