package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PairsAndTuplesOrderByTheirNumbersFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("comparing one tuple with another")
    class ComparingTuples {

        @Test
        @DisplayName("the first part that differs settles it, as a number")
        void theFirstPartThatDiffersSettlesIt() {
            assertThat(answerTo("reduce [1.3.1 < 1.20.1  1.20.1 < 1.3.1]"))
                    .isEqualTo("[#(true) #(false)]");
        }

        @Test
        @DisplayName("and every part is looked at in turn")
        void everyPartIsLookedAtInTurn() {
            assertThat(answerTo("""
                    reduce [
                        0.0.0 < 0.0.1
                        1.0.0 > 0.255.255
                        255.255.255 > 16.16.16
                        2.0.0 < 16.0.0
                        16.0.0 < 255.0.0
                    ]""")).isEqualTo("[#(true) #(true) #(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("a shorter tuple is the lesser when the longer one carries more")
        void aShorterTupleIsTheLesserWhenTheLongerCarriesMore() {
            assertThat(answerTo("reduce [1.2.3 < 1.2.3.4  1.2.3.4 < 1.2.3]"))
                    .isEqualTo("[#(true) #(false)]");
        }

        @Test
        @DisplayName("and a trailing zero counts as the nothing it is")
        void aTrailingZeroCountsAsNothing() {
            assertThat(answerTo("""
                    reduce [1.2.3 = 1.2.3.0  1.2.3 == 1.2.3.0  1.2.3 > 1.2.3.0]"""))
                    .isEqualTo("[#(true) #(false) #(false)]");
        }

        @Test
        @DisplayName("the longest tuple orders against a short one part by part")
        void theLongestTupleOrdersAgainstAShortOne() {
            assertThat(answerTo("""
                    reduce [
                        1.2.3 < 1.2.3.4.5.6.7.8.9.10.11.12
                        2.0.0 > 1.2.3.4.5.6.7.8.9.10.11.12
                    ]""")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("MINIMUM and MAXIMUM pick by the same order")
        void minimumAndMaximumPickByTheSameOrder() {
            assertThat(answerTo("reduce [minimum 1.20.1 1.3.1  maximum 1.20.1 1.3.1]"))
                    .isEqualTo("[1.3.1 1.20.1]");
        }
    }

    @Nested
    @DisplayName("sorting without a comparison of the caller's own")
    class Sorting {

        @Test
        @DisplayName("pairs go by their numbers and not by how they are written")
        void pairsGoByTheirNumbers() {
            assertThat(answerTo("sort [3x0 20x0 100x0]"))
                    .isEqualTo("[3x0 20x0 100x0]");
            assertThat(answerTo("sort [10x10 9x9 100x100]"))
                    .isEqualTo("[9x9 10x10 100x100]");
        }

        @Test
        @DisplayName("and tuples do too")
        void tuplesGoByTheirNumbers() {
            assertThat(answerTo("sort [255.0.0 16.0.0 2.0.0]"))
                    .isEqualTo("[2.0.0 16.0.0 255.0.0]");
            assertThat(answerTo("sort [1.20.1 1.3.1 1.100.1]"))
                    .isEqualTo("[1.3.1 1.20.1 1.100.1]");
        }

        @Test
        @DisplayName("pairs with the same first half are settled by the second")
        void pairsWithTheSameFirstHalfAreSettledBySecond() {
            assertThat(answerTo("sort [3x20 3x100 3x3]"))
                    .isEqualTo("[3x3 3x20 3x100]");
        }

        @Test
        @DisplayName("and a pair below zero sorts below one above it")
        void aPairBelowZeroSortsBelowOneAboveIt() {
            assertThat(answerTo("sort [3x0 -20x0 0x0 -100x0]"))
                    .isEqualTo("[-100x0 -20x0 0x0 3x0]");
        }

        @Test
        @DisplayName("/REVERSE turns the same order round")
        void reverseTurnsTheSameOrderRound() {
            assertThat(answerTo("sort/reverse [3x0 20x0 100x0]"))
                    .isEqualTo("[100x0 20x0 3x0]");
        }

        @Test
        @DisplayName("/SKIP orders records by the pair each one begins with")
        void skipOrdersRecordsByThePairEachBeginsWith() {
            assertThat(answerTo("""
                    sort/skip [7x0 seven 12x0 twelve 1x0 one 100x0 hundred] 2"""))
                    .isEqualTo("[1x0 one 7x0 seven 12x0 twelve 100x0 hundred]");
        }

        @Test
        @DisplayName("but a comparison the caller gave still decides")
        void aComparisonTheCallerGaveStillDecides() {
            assertThat(answerTo("""
                    sort/compare [3x0 20x0 100x0] func [a b] [a/1 > b/1]"""))
                    .isEqualTo("[100x0 20x0 3x0]");
        }

        @Test
        @DisplayName("and the datatypes that were already right stay right")
        void theDatatypesThatWereAlreadyRightStayRight() {
            assertThat(answerTo("""
                    reduce [
                        sort [3 20 100]
                        sort [3.0 20.0 100.0]
                        sort [$3 $20 $100]
                        sort [3:00 20:00 100:00]
                        sort [3% 20% 100%]
                        sort [#"c" #"a" #"b"]
                        sort [%3 %20 %100]
                    ]""")).isEqualTo("""
                    [[3 20 100] [3.0 20.0 100.0] [$3 $20 $100] \
                    [3:00 20:00 100:00] [3% 20% 100%] [#"a" #"b" #"c"] \
                    [%100 %20 %3]]""");
        }
    }
}
