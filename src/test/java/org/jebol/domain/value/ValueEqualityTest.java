package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What {@code equals} means on a value.
 *
 * <p>Java's {@code equals} is REBOL's {@code ==}, the strict one: same
 * datatype, same contents, case sensitive. REBOL's looser {@code =} is
 * offered separately where the two differ, and identity, REBOL's
 * {@code same?}, is {@code sharesStorageWith}.
 *
 * <p>Three questions, three methods. Collapsing them would make one of them
 * silently wrong.
 */
class ValueEqualityTest {

    @Nested
    @DisplayName("series equality compares contents, not storage")
    class SeriesEquality {

        @Test
        void separatelyBuiltStringsWithTheSameTextAreEqual() {
            assertThat(StringValue.of("hello")).isEqualTo(StringValue.of("hello"));
        }

        @Test
        void separatelyBuiltStringsAreNotTheSameSeries() {
            assertThat(StringValue.of("hello").sharesStorageWith(StringValue.of("hello")))
                    .isFalse();
        }

        @Test
        @DisplayName("equality is from the position, not from the head")
        void equalityStartsAtThePosition() {
            StringValue fromThird = StringValue.of("xxhello").atIndex(3);
            assertThat(fromThird).isEqualTo(StringValue.of("hello"));
        }

        @Test
        void blocksWithEqualItemsAreEqual() {
            assertThat(BlockValue.block(IntegerValue.of(1), IntegerValue.of(2)))
                    .isEqualTo(BlockValue.block(IntegerValue.of(1), IntegerValue.of(2)));
        }

        @Test
        void blocksWithDifferentItemsAreNotEqual() {
            assertThat(BlockValue.block(IntegerValue.of(1)))
                    .isNotEqualTo(BlockValue.block(IntegerValue.of(2)));
        }

        @Test
        @DisplayName("nested blocks compare all the way down")
        void nestedBlocksCompareDeeply() {
            assertThat(BlockValue.block(BlockValue.block(IntegerValue.of(1))))
                    .isEqualTo(BlockValue.block(BlockValue.block(IntegerValue.of(1))));
            assertThat(BlockValue.block(BlockValue.block(IntegerValue.of(1))))
                    .isNotEqualTo(BlockValue.block(BlockValue.block(IntegerValue.of(2))));
        }

        @Test
        void binariesWithTheSameOctetsAreEqual() {
            assertThat(BinaryValue.of(0xDE, 0xAD)).isEqualTo(BinaryValue.of(0xDE, 0xAD));
            assertThat(BinaryValue.of(0xDE, 0xAD)).isNotEqualTo(BinaryValue.of(0xDE, 0xAE));
        }

        @Test
        @DisplayName("equal values hash alike")
        void equalValuesShareAHashCode() {
            assertThat(StringValue.of("hello")).hasSameHashCodeAs(StringValue.of("hello"));
            assertThat(BlockValue.block(IntegerValue.of(1)))
                    .hasSameHashCodeAs(BlockValue.block(IntegerValue.of(1)));
        }
    }

    @Nested
    @DisplayName("the datatype is part of equality, which is what makes == strict")
    class DatatypeIsPartOfEquality {

        @Test
        @DisplayName("a file! and a string! with the same text are not ==")
        void sameTextDifferentDatatypeIsNotEqual() {
            assertThat(StringValue.of("readme", Datatype.FILE))
                    .isNotEqualTo(StringValue.of("readme", Datatype.STRING));
        }

        @Test
        @DisplayName("a block! and a paren! with the same items are not ==")
        void blockAndParenAreDifferent() {
            BlockValue asBlock = BlockValue.block(IntegerValue.of(1));
            assertThat(asBlock.as(Datatype.PAREN)).isNotEqualTo(asBlock);
        }

        @Test
        @DisplayName("an integer and a decimal of the same magnitude are not ==")
        void integerAndDecimalAreDifferent() {
            assertThat((Value) IntegerValue.of(1)).isNotEqualTo(DecimalValue.of(1.0));
        }
    }

    @Nested
    @DisplayName("case: = ignores it, == does not")
    class CaseSensitivity {

        @Test
        void strictEqualityIsCaseSensitive() {
            assertThat(StringValue.of("REBOL")).isNotEqualTo(StringValue.of("rebol"));
        }

        @Test
        void looseEqualityIgnoresCase() {
            assertThat(StringValue.of("REBOL").equalsIgnoringCase(StringValue.of("rebol")))
                    .isTrue();
        }

        @Test
        @DisplayName("loose equality still respects the datatype")
        void looseEqualityStillChecksDatatype() {
            assertThat(StringValue.of("REBOL")
                    .equalsIgnoringCase(StringValue.of("rebol", Datatype.FILE)))
                    .isFalse();
        }

        @Test
        @DisplayName("words compare without regard to case, and print as written")
        void wordsCompareIgnoringCase() {
            WordValue asWritten = WordValue.of("Print");
            assertThat(asWritten.spelling()).isEqualTo("Print");
            assertThat(asWritten.canonical()).isEqualTo("print");
            assertThat(asWritten.namesSameAs(WordValue.of("PRINT"))).isTrue();
        }

        @Test
        @DisplayName("a word and a set-word name the same thing in different shapes")
        void shapeDoesNotChangeWhatAWordNames() {
            WordValue plain = WordValue.of("total");
            WordValue assigning = WordValue.of("total", Datatype.SET_WORD);

            assertThat(plain.namesSameAs(assigning)).isTrue();
            assertThat((Value) plain).isNotEqualTo(assigning);
            assertThat(assigning.toString()).isEqualTo("total:");
        }
    }

    @Nested
    @DisplayName("money keeps its scale for printing and ignores it for comparing")
    class MoneyEquality {

        @Test
        @DisplayName("$1.50 and $1.5 hold different scales")
        void scaleIsPreserved() {
            assertThat(MoneyValue.of(new BigDecimal("1.50")).scale()).isEqualTo(2);
            assertThat(MoneyValue.of(new BigDecimal("1.5")).scale()).isEqualTo(1);
        }

        @Test
        @DisplayName("and are nonetheless equal, which a real R3 confirms")
        void equalityIgnoresScale() {
            assertThat(MoneyValue.of(new BigDecimal("1.50")))
                    .as("$1.50 = $1.5 and $1.50 == $1.5 are both true in R3")
                    .isEqualTo(MoneyValue.of(new BigDecimal("1.5")));
        }

        @Test
        @DisplayName("equal amounts hash alike, so they can share a key")
        void equalAmountsHashAlike() {
            assertThat(MoneyValue.of(new BigDecimal("1.50")))
                    .hasSameHashCodeAs(MoneyValue.of(new BigDecimal("1.5")));
        }

        @Test
        @DisplayName("the scale still survives to be printed")
        void scaleSurvivesForPrinting() {
            assertThat(Molder.mold(MoneyValue.of(new BigDecimal("1.50"))))
                    .isEqualTo("$1.50");
        }

        @Test
        @DisplayName("a different currency is a different amount")
        void currencyIsPartOfEquality() {
            assertThat(MoneyValue.of(new BigDecimal("1.50"), "GBP"))
                    .isNotEqualTo(MoneyValue.of(new BigDecimal("1.50"), "USD"));
        }
    }
}
