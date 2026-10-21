package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppendOnlyAndDynamicRefinementFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("/ONLY adds the series rather than its items")
    class TheOnlyRefinement {

        @Test
        @DisplayName("a block appended whole is one item")
        void addsABlockAsOneItem() {
            assertThat(answerTo("""
                    (append/only copy [] [9 8]) = [[9 8]]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("where without it the items are spread")
        void spreadsABlockWithoutIt() {
            assertThat(answerTo("""
                    (append copy [] [9 8]) = [9 8]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a paren goes in whole without being asked")
        void addsAParenWholeAnyway() {
            assertThat(answerTo("""
                    (append copy [] quote (9 8)) = [(9 8)]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and /ONLY leaves it exactly as it was")
        void leavesAParenAsItWas() {
            assertThat(answerTo("""
                    (append/only copy [] quote (9 8)) = [(9 8)]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a string is one item with or without it")
        void addsAStringAsOneItemEitherWay() {
            assertThat(answerTo("""
                    all [
                        (append copy [] "ab") = ["ab"]
                        (append/only copy [] "ab") = ["ab"]
                    ]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an empty block spreads to nothing")
        void spreadsAnEmptyBlockToNothing() {
            assertThat(answerTo("""
                    (append copy [1] []) = [1]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and under /ONLY is one empty item")
        void addsAnEmptyBlockAsOneItem() {
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
        void isDeclinedByAFalseWord() {
            assertThat(answerTo("""
                    only: no gathered: []
                    repend/:only gathered [4 + 5 6 * 7]
                    gathered == [9 42]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a declined refinement leaves its argument unread")
        void leavesItsArgumentUnreadWhenDeclined() {
            assertThat(answerTo("""
                    part: no length: 10
                    found: find/:part "abcdef" "d" length
                    found == {def}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("where a granted one reads it and bounds the search")
        void readsItsArgumentWhenGranted() {
            assertThat(answerTo("""
                    part: yes length: 2
                    none? find/:part "abcdef" "d" length""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a granted refinement the function has not got is refused")
        void isRefusedWhenGrantedButAbsent() {
            assertThat(answerTo("""
                    made-up: yes
                    e: try [append/:made-up copy [] 1] e/id = 'no-refine"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a declined one the function has not got is refused as well, naming it as written")
        void isRefusedWhenDeclinedButAbsent() {
            assertThat(answerTo("""
                    made-up: no
                    e: try [append/:made-up copy [] 1] reduce [e/id e/arg1 e/arg2]"""))
                    .isEqualTo("[no-refine append :made-up]");
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
