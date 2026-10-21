package org.jebol.domain.value;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PairValueTest {

    @Nested
    @DisplayName("what a pair is made of")
    class Halves {

        @Test
        void areDecimalsEvenWhenWrittenAsWholeNumbers() {
            assertThat(PairValue.of(1, 2).x()).isEqualTo(1.0d);
        }

        @Test
        void includeADecimalSecondHalf() {
            assertThat(PairValue.of(1, 2).y()).isEqualTo(2.0d);
        }

        @Test
        void mayBeFractional() {
            assertThat(PairValue.of(1.5, 2).x()).isEqualTo(1.5d);
        }

        @Test
        void mayBeZero() {
            assertThat(PairValue.of(0, 0).x()).isEqualTo(0.0d);
        }

        @Test
        void mayBeNegative() {
            assertThat(PairValue.of(-1, -2).y()).isEqualTo(-2.0d);
        }

        @Test
        void reportThePairAsAPair() {
            assertThat(PairValue.of(1, 2).datatype()).isEqualTo(PairValue.TYPE);
        }
    }

    @Nested
    @DisplayName("halves narrowed to single precision")
    class SinglePrecisionHalves {

        @Test
        @DisplayName("a half too large for a float becomes infinite")
        void overflowWhenLarge() {
            assertThat(PairValue.of(1e300, 1).x()).isEqualTo(Double.POSITIVE_INFINITY);
            assertThat(PairValue.of(1, -1e300).y()).isEqualTo(Double.NEGATIVE_INFINITY);
        }

        @Test
        @DisplayName("an infinite half is kept as it was given")
        void keepInfinity() {
            assertThat(PairValue.of(1, Double.POSITIVE_INFINITY).y())
                    .isEqualTo(Double.POSITIVE_INFINITY);
        }

        @Test
        @DisplayName("a not-a-number half is kept as it was given")
        void keepNotANumber() {
            assertThat(PairValue.of(Double.NaN, 1).x()).isNaN();
        }

        @Test
        @DisplayName("a fraction loses the digits a float cannot carry")
        void narrowAFraction() {
            assertThat(PairValue.of(0.1, 0.2).x()).isEqualTo(0.10000000149011612d);
            assertThat(PairValue.of(0.1, 0.2).x()).isNotEqualTo(0.1d);
        }

        @Test
        @DisplayName("a whole number too large for a float loses its low digits")
        void roundALargeWholeNumber() {
            assertThat(PairValue.of(2147483647, 1).x()).isEqualTo(2147483648.0d);
        }
    }

    @Nested
    @DisplayName("reading a half by name and by position")
    class Reading {

        @Test
        void answersTheFirstHalfByName() {
            assertThat(PairValue.of(1, 2).readTheHalf("x")).contains(DecimalValue.of(1.0d));
        }

        @Test
        void answersTheSecondHalfByName() {
            assertThat(PairValue.of(1, 2).readTheHalf("y")).contains(DecimalValue.of(2.0d));
        }

        @Test
        void answersTheFirstHalfByPosition() {
            assertThat(PairValue.of(1, 2).readHalfAt(1)).contains(DecimalValue.of(1.0d));
        }

        @Test
        void answersTheSecondHalfByPosition() {
            assertThat(PairValue.of(1, 2).readHalfAt(2)).contains(DecimalValue.of(2.0d));
        }

        @Test
        void findsNoHalfBeforeTheFirst() {
            assertThat(PairValue.of(1, 2).readHalfAt(0)).isEmpty();
        }

        @Test
        void findsNoHalfAfterTheSecond() {
            assertThat(PairValue.of(1, 2).readHalfAt(3)).isEmpty();
        }

        @Test
        void findsNoHalfForAnotherName() {
            assertThat(PairValue.of(1, 2).readTheHalf("z")).isEmpty();
        }
    }

    @Nested
    @DisplayName("how a pair prints")
    class Molding {

        @Test
        void printsAWholeHalfWithoutItsPoint() {
            assertThat(Molder.mold(PairValue.of(1, 2))).isEqualTo("1x2");
        }

        @Test
        void keepsTheFractionalHalfsPoint() {
            assertThat(Molder.mold(PairValue.of(1.5, 2))).isEqualTo("1.5x2");
        }

        @Test
        void keepsTheNegativeHalfsSign() {
            assertThat(Molder.mold(PairValue.of(-1, -2))).isEqualTo("-1x-2");
        }

        @Test
        void printsBothHalvesZero() {
            assertThat(Molder.mold(PairValue.of(0, 0))).isEqualTo("0x0");
        }
    }

    @Nested
    @DisplayName("when two pairs are the same")
    class Equality {

        @Test
        void holdsForTheSameHalves() {
            assertThat(PairValue.of(1, 2)).isEqualTo(PairValue.of(1, 2));
        }

        @Test
        void failsForADifferingFirstHalf() {
            assertThat(PairValue.of(1, 2)).isNotEqualTo(PairValue.of(3, 2));
        }

        @Test
        void failsForADifferingSecondHalf() {
            assertThat(PairValue.of(1, 2)).isNotEqualTo(PairValue.of(1, 3));
        }

        @Test
        void holdsForAWholeHalfWrittenFractionally() {
            assertThat(PairValue.of(1, 2)).isEqualTo(PairValue.of(1.0, 2.0));
        }
    }

    @Property
    void comesBackFromReversingTwice(
            @ForAll @DoubleRange(min = -1e6, max = 1e6) double x,
            @ForAll @DoubleRange(min = -1e6, max = 1e6) double y) {

        assertThat(PairValue.of(x, y).reversed().reversed())
                .isEqualTo(PairValue.of(x, y));
    }

    @Property
    void moldsEveryPairToSomethingReadable(
            @ForAll @DoubleRange(min = -1e6, max = 1e6) double x,
            @ForAll @DoubleRange(min = -1e6, max = 1e6) double y) {

        PairValue pair = PairValue.of(x, y);
        assertThat(Molder.mold(pair)).contains("x");
    }
}
