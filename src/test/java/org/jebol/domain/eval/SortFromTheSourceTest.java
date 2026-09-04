package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SORT, read out of {@code Sort_Block} in {@code src/core/t-block.c}.
 *
 * <p>Two things in there are not guessable and decide most of the
 * behaviour.
 *
 * <p>The comparator is called with its arguments the other way round:
 * {@code Compare_Call} sets {@code v1 = p2} and {@code v2 = p1} before
 * applying the function. And its answer is read by a rule that starts at
 * -1 and only leaves it for a true logic or a number at or above zero. So
 * a comparator written as a strict predicate, which is how nearly all of
 * them are written, answers false for two equal items and that false
 * means "the second one first" rather than "leave them alone".
 *
 * <p>Together those two make {@code sort/compare b func [a b] [a &lt; b]}
 * stable, and reading either of them the obvious way makes it unstable
 * while still sorting correctly. The stability only shows up when
 * something else depends on the order of equal keys.
 */
class SortFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Nested
    @DisplayName("Compare_Call: what a comparator is asked and what its answer means")
    class TheComparator {

        @Test
        @DisplayName("a predicate that puts the greater first counts down")
        void aGreaterThanPredicateReverses() {
            assertThat(answerTo("(sort/compare [1 2 3] func [a b] [a > b]) = [3 2 1]"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("(sort/compare [1 2 3 4] :greater?) = [4 3 2 1]"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a predicate that puts the lesser first counts up")
        void aLessThanPredicateSorts() {
            assertThat(answerTo("(sort/compare [3 1 2] func [a b] [a < b]) = [1 2 3]"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a comparator answering a number reads its sign")
        void aNumberIsReadBySign() {
            assertThat(answerTo(
                    "(sort/compare [1 10 3] func [x y] "
                            + "[case [x > y [1] x < y [-1] true [0]]]) = [10 3 1]"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a comparator answering zero leaves the two where they were")
        void zeroIsATie() {
            assertThat(answerTo("(sort/compare [3 1 2] func [a b] [0]) = [3 1 2]"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("equal keys keep the order they arrived in")
        void theSortIsStable() {
            assertThat(answerTo("""
                    blk: copy [] repeat i 32 [repend blk [i 0]]
                    (sort/skip/all/compare copy blk 2 func [a b] [a/2 < b/2]) = blk
                    """)).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("ties keep their order while the rest are sorted")
        void stabilityWithinGroups() {
            assertThat(answerTo("""
                    blk: [3 1  1 2  2 3  1 4  3 5  2 6]
                    (sort/skip/all/compare copy blk 2 func [a b] [a/1 < b/1])
                        = [1 2  1 4  2 3  2 6  3 1  3 5]
                    """)).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a sort inside a comparator does not disturb the outer one")
        void comparatorsNest() {
            assertThat(answerTo("""
                    s1: sort/compare "abcd" func [a b] [
                        s2: sort/compare/reverse "1234" func [a b] [a < b]
                        a < b
                    ]
                    all [s1 == "abcd" s2 == "4321"]
                    """)).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a comparator works on a string, a file and a binary alike")
        void everySeriesTakesAComparator() {
            assertThat(answerTo("(sort/compare \"abczyx\" func [a b] [a > b]) == \"zyxcba\""))
                    .isEqualTo("#(true)");
            assertThat(answerTo("(sort/compare %21543 func [a b] [a > b]) == %54321"))
                    .isEqualTo("#(true)");
            assertThat(answerTo(
                    "(sort/compare #{000102030405} func [a b] [a > b]) == #{050403020100}"))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("/compare with a column number")
    class ByColumn {

        @Test
        @DisplayName("a number names a column of each record")
        void aColumnOrdersTheRecords() {
            assertThat(answerTo("(sort/skip/compare [3 4 1 2] 2 2) = [1 2 3 4]"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a column runs from one to the record width")
        void theBoundariesOnAColumn() {
            assertThat(errorIdOf("sort/skip/compare [3 4 1 2] 2 1"))
                    .as("the first column is the on point")
                    .isEqualTo("no-error");
            assertThat(errorIdOf("sort/skip/compare [3 4 1 2] 2 2"))
                    .as("and the last one is the other on point")
                    .isEqualTo("no-error");
            assertThat(errorIdOf("sort/skip/compare [3 4 1 2] 2 0")).isEqualTo("invalid-arg");
            assertThat(errorIdOf("sort/skip/compare [3 4 1 2] 2 3")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("a column needs records to be a column of")
        void aColumnNeedsSkip() {
            assertThat(errorIdOf("sort/compare [3 4 1 2] 1")).isEqualTo("invalid-arg");
            assertThat(errorIdOf("sort/compare \"abcd\" 0")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("a block names several columns, tried in turn")
        void severalColumns() {
            assertThat(answerTo("""
                    (sort/skip/compare ["A2" "B3" "C1" "A1" "B2" "C3" "A3" "B1" "C2"] 3 [3 2 1])
                        == ["A2" "B3" "C1" "A3" "B1" "C2" "A1" "B2" "C3"]
                    """)).isEqualTo("#(true)");
            assertThat(answerTo("""
                    (sort/skip/compare ["A2" "B3" "C1" "A1" "B2" "C3" "A3" "B1" "C2"] 3 [1 2 3])
                        == ["A1" "B2" "C3" "A2" "B3" "C1" "A3" "B1" "C2"]
                    """)).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("every column in the block is checked before any sorting")
        void theBlockIsValidatedWhole() {
            assertThat(errorIdOf("sort/skip/compare [3 B 1 B] 2 [2 -1]")).isEqualTo("invalid-arg");
            assertThat(errorIdOf("sort/skip/compare [3 B 1 B] 2 [2 0]")).isEqualTo("invalid-arg");
            assertThat(errorIdOf("sort/skip/compare [3 B 1 B] 2 [2 x]")).isEqualTo("invalid-arg");
            assertThat(errorIdOf("sort/skip/compare [3 B 1 B] 2 [2 3]")).isEqualTo("invalid-arg");
        }
    }

    @Nested
    @DisplayName("/all")
    class WholeRecords {

        @Test
        @DisplayName("without a comparator, /all orders by every field in turn")
        void everyFieldDecides() {
            assertThat(answerTo("(sort/skip/all [1 9 1 2] 2) = [1 2 1 9]")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("without /all, the first field of a record decides alone")
        void theFirstFieldDecides() {
            assertThat(answerTo("(sort/skip [1 9 1 2] 2) = [1 9 1 2]")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/all with a comparator hands it whole records")
        void aRecordIsAnArgument() {
            assertThat(answerTo("""
                    db: ["A3" 41 "B2" 8 "C4" 6]
                    (sort/skip/compare/all db 2 func [a b] [a/2 < b/2])
                        == ["C4" 6 "B2" 8 "A3" 41]
                    """)).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/reverse turns the whole-record order round too")
        void reversedWholeRecords() {
            assertThat(answerTo("""
                    db: ["A3" 41 "B2" 8 "C4" 6]
                    (sort/reverse/skip/compare/all db 2 func [a b] [a/2 < b/2])
                        == ["A3" 41 "B2" 8 "C4" 6]
                    """)).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the record a comparator is handed cannot be changed")
        void theRecordsAreProtected() {
            assertThat(errorIdOf("""
                    db: ["A3" 41 "B2" 8 "C4" 6]
                    sort/skip/compare/all db 2 func [a b] [append a 'x  a/2 < b/2]
                    """)).isEqualTo("protected");
            assertThat(errorIdOf("""
                    db: ["A3" 41 "B2" 8 "C4" 6]
                    sort/skip/compare/all db 2 func [a b] [reverse b  a/2 < b/2]
                    """)).isEqualTo("protected");
        }

        @Test
        @DisplayName("/all with a column number is refused")
        void aColumnLeavesAllNothingToSay() {
            assertThat(errorIdOf("sort/skip/compare/all [3 4 1 2] 2 1"))
                    .isEqualTo("bad-refines");
            assertThat(errorIdOf("sort/compare/skip/all \"ba ab aa \" 1 3"))
                    .isNotEqualTo("no-error");
        }
    }

    @Nested
    @DisplayName("/skip")
    class RecordWidth {

        @Test
        @DisplayName("a record width must divide the length")
        void theWidthMustFit() {
            assertThat(errorIdOf("sort/skip [1 2 3 4] 2"))
                    .as("two into four is the on point")
                    .isEqualTo("no-error");
            assertThat(errorIdOf("sort/skip [1 2 3 4 5] 2")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("a width of none or below is refused")
        void theFloor() {
            assertThat(errorIdOf("sort/skip [1 2 3 4] 0")).isEqualTo("out-of-range");
            assertThat(errorIdOf("sort/skip [1 2 3 4] -1")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("a width wider than the series is refused")
        void theCeiling() {
            assertThat(errorIdOf("sort/skip [1 2 3 4] 4"))
                    .as("one record of everything is the on point")
                    .isEqualTo("no-error");
            assertThat(errorIdOf("sort/skip [1 2 3] 4")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("a series of one or none is left alone whatever was asked")
        void theDegenerateSeries() {
            assertThat(answerTo("(sort/skip [] 3) = []")).isEqualTo("#(true)");
            assertThat(answerTo("(sort/skip [1] 3) = [1]")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("records keep their fields together")
        void aRecordMovesWhole() {
            assertThat(answerTo("""
                    (sort/skip ["A2" "B3" "C1" "A1" "B2" "C3" "A3" "B1" "C2"] 3)
                        == ["A1" "B2" "C3" "A2" "B3" "C1" "A3" "B1" "C2"]
                    """)).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("the plain sort")
    class WithoutRefinements {

        @Test
        @DisplayName("SORT folds case and /case does not")
        void caseFolding() {
            assertThat(answerTo("(sort [A b C a B c]) == [A a b B C c]")).isEqualTo("#(true)");
            assertThat(answerTo("(sort/case [A b C a B c]) == [A B C a b c]"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("sorting a string sorts its characters")
        void aStringSorts() {
            assertThat(answerTo("(sort \"ABCabcdefDEF\") == \"AaBbCcdDeEfF\""))
                    .isEqualTo("#(true)");
            assertThat(answerTo("(sort/case \"ABCabcdefDEF\") == \"ABCDEFabcdef\""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a pair sorts on both halves, the first deciding")
        void pairsSort() {
            assertThat(answerTo("(sort [1x2 1x1 2x1 2x2]) == [1x1 1x2 2x1 2x2]"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/reverse turns the order round")
        void reversing() {
            assertThat(answerTo("(sort/reverse [1 3 2]) = [3 2 1]")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/part sorts the front and leaves the rest alone")
        void partial() {
            assertThat(answerTo("(sort/part [3 2 1 9 8 7] 3) = [1 2 3 9 8 7]"))
                    .isEqualTo("#(true)");
        }
    }
}
