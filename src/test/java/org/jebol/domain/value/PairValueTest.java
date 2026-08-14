package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A pair holds two decimals, which is not what {@code 1x2} looks like.
 *
 * <p>Each test here asks one question. The behaviour of the natives that
 * take pairs lives in {@code corpus/pairs.corpus}, where it is checked
 * against a real R3 rather than against an opinion.
 */
class PairValueTest {

    @Nested
    @DisplayName("what a pair is made of")
    class Halves {

        @Test
        void bothHalvesAreDecimalsEvenWhenWrittenAsWholeNumbers() {
            assertThat(PairValue.of(1, 2).x()).isEqualTo(1.0d);
        }

        @Test
        void theSecondHalfIsADecimalToo() {
            assertThat(PairValue.of(1, 2).y()).isEqualTo(2.0d);
        }

        @Test
        void aHalfMayBeFractional() {
            assertThat(PairValue.of(1.5, 2).x()).isEqualTo(1.5d);
        }

        @Test
        void aHalfMayBeZero() {
            assertThat(PairValue.of(0, 0).x()).isEqualTo(0.0d);
        }

        @Test
        void aHalfMayBeNegative() {
            assertThat(PairValue.of(-1, -2).y()).isEqualTo(-2.0d);
        }

        @Test
        void aPairReportsItselfAsAPair() {
            assertThat(PairValue.of(1, 2).datatype()).isEqualTo(Datatype.PAIR);
        }
    }

    @Nested
    @DisplayName("halves narrowed to single precision")
    class SinglePrecisionHalves {

        @Test
        @DisplayName("a half too large for a float becomes infinite")
        void aLargeHalfOverflows() {
            assertThat(PairValue.of(1e300, 1).x()).isEqualTo(Double.POSITIVE_INFINITY);
            assertThat(PairValue.of(1, -1e300).y()).isEqualTo(Double.NEGATIVE_INFINITY);
        }

        @Test
        @DisplayName("an infinite half is kept as it was given")
        void anInfiniteHalfIsKept() {
            assertThat(PairValue.of(1, Double.POSITIVE_INFINITY).y())
                    .isEqualTo(Double.POSITIVE_INFINITY);
        }

        @Test
        @DisplayName("a not-a-number half is kept as it was given")
        void aNotANumberHalfIsKept() {
            assertThat(PairValue.of(Double.NaN, 1).x()).isNaN();
        }

        @Test
        @DisplayName("a fraction loses the digits a float cannot carry")
        void aFractionIsNarrowed() {
            assertThat(PairValue.of(0.1, 0.2).x()).isEqualTo(0.10000000149011612d);
            assertThat(PairValue.of(0.1, 0.2).x()).isNotEqualTo(0.1d);
        }

        @Test
        @DisplayName("a whole number too large for a float loses its low digits")
        void aLargeWholeNumberIsRounded() {
            assertThat(PairValue.of(2147483647, 1).x()).isEqualTo(2147483648.0d);
        }
    }

    @Nested
    @DisplayName("reading a half by name and by position")
    class Reading {

        @Test
        void theFirstHalfAnswersToItsName() {
            assertThat(PairValue.of(1, 2).half("x")).contains(DecimalValue.of(1.0d));
        }

        @Test
        void theSecondHalfAnswersToItsName() {
            assertThat(PairValue.of(1, 2).half("y")).contains(DecimalValue.of(2.0d));
        }

        @Test
        void theFirstHalfAnswersToItsPositionAsWell() {
            assertThat(PairValue.of(1, 2).halfAt(1)).contains(DecimalValue.of(1.0d));
        }

        @Test
        void theSecondHalfAnswersToItsPositionAsWell() {
            assertThat(PairValue.of(1, 2).halfAt(2)).contains(DecimalValue.of(2.0d));
        }

        @Test
        void thePositionBeforeTheFirstHalfIsNotAHalf() {
            assertThat(PairValue.of(1, 2).halfAt(0)).isEmpty();
        }

        @Test
        void thePositionAfterTheSecondHalfIsNotAHalf() {
            assertThat(PairValue.of(1, 2).halfAt(3)).isEmpty();
        }

        @Test
        void aNameThatIsNeitherHalfIsNotAHalf() {
            assertThat(PairValue.of(1, 2).half("z")).isEmpty();
        }
    }

    @Nested
    @DisplayName("how a pair prints")
    class Molding {

        @Test
        void aWholeHalfPrintsWithoutItsPoint() {
            assertThat(Molder.mold(PairValue.of(1, 2))).isEqualTo("1x2");
        }

        @Test
        void aFractionalHalfKeepsItsPoint() {
            assertThat(Molder.mold(PairValue.of(1.5, 2))).isEqualTo("1.5x2");
        }

        @Test
        void aNegativeHalfKeepsItsSign() {
            assertThat(Molder.mold(PairValue.of(-1, -2))).isEqualTo("-1x-2");
        }

        @Test
        void bothHalvesZero() {
            assertThat(Molder.mold(PairValue.of(0, 0))).isEqualTo("0x0");
        }
    }

    @Nested
    @DisplayName("when two pairs are the same")
    class Equality {

        @Test
        void twoPairsWithTheSameHalvesAreEqual() {
            assertThat(PairValue.of(1, 2)).isEqualTo(PairValue.of(1, 2));
        }

        @Test
        void aDifferingFirstHalfIsEnoughToDiffer() {
            assertThat(PairValue.of(1, 2)).isNotEqualTo(PairValue.of(3, 2));
        }

        @Test
        void aDifferingSecondHalfIsEnoughToDiffer() {
            assertThat(PairValue.of(1, 2)).isNotEqualTo(PairValue.of(1, 3));
        }

        @Test
        void aWholeHalfEqualsTheSameHalfWrittenFractionally() {
            assertThat(PairValue.of(1, 2)).isEqualTo(PairValue.of(1.0, 2.0));
        }
    }

    @Property
    void reversingTwiceGivesTheSamePair(
            @ForAll @DoubleRange(min = -1e6, max = 1e6) double x,
            @ForAll @DoubleRange(min = -1e6, max = 1e6) double y) {

        assertThat(PairValue.of(x, y).reversed().reversed())
                .isEqualTo(PairValue.of(x, y));
    }

    @Property
    void everyPairMoldsToSomethingTheReaderReadsBack(
            @ForAll @DoubleRange(min = -1e6, max = 1e6) double x,
            @ForAll @DoubleRange(min = -1e6, max = 1e6) double y) {

        PairValue pair = PairValue.of(x, y);
        assertThat(Molder.mold(pair)).contains("x");
    }
}
