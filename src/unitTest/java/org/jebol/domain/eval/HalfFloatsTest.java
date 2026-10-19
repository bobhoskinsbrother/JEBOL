package org.jebol.domain.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class HalfFloatsTest {

    @Nested
    @DisplayName("a half-precision value widened to a float")
    class Widening {

        @Test
        @DisplayName("agrees with the JDK on all 65536 half values")
        void widensEveryHalf() {
            for (int bits = 0; bits < 0x1_0000; bits++) {
                short half = (short) bits;
                assertThat(Float.floatToRawIntBits(HalfFloats.asFloat(half)))
                        .isEqualTo(Float.floatToRawIntBits(Float.float16ToFloat(half)));
            }
        }
    }

    @Nested
    @DisplayName("a float narrowed to half precision")
    class Narrowing {

        @Test
        @DisplayName("gives back the half it came from")
        void roundTripsEveryHalf() {
            for (int bits = 0; bits < 0x1_0000; bits++) {
                short half = (short) bits;
                if (Float.isNaN(HalfFloats.asFloat(half))) {
                    continue;
                }
                assertThat(HalfFloats.of(HalfFloats.asFloat(half))).isEqualTo(half);
            }
        }

        @Test
        @DisplayName("agrees with the JDK on every half value and the four floats around it")
        void narrowsEveryHalfAndItsNeighbours() {
            for (int bits = 0; bits < 0x1_0000; bits++) {
                float widened = Float.float16ToFloat((short) bits);
                for (float nearby : new float[] {
                        widened,
                        Math.nextDown(widened),
                        Math.nextUp(widened),
                        Math.nextDown(Math.nextDown(widened)),
                        Math.nextUp(Math.nextUp(widened)) }) {
                    assertThat(HalfFloats.of(nearby)).isEqualTo(Float.floatToFloat16(nearby));
                }
            }
        }

        @Test
        @DisplayName("agrees with the JDK on a quarter of a million random bit patterns")
        void randomBitPatterns() {
            Random random = new Random(20261012);
            for (int sample = 0; sample < 250_000; sample++) {
                float value = Float.intBitsToFloat(random.nextInt());
                assertThat(HalfFloats.of(value)).isEqualTo(Float.floatToFloat16(value));
            }
        }

        @Test
        @DisplayName("at the overflow threshold, where a half becomes infinite")
        void overflowsAtTheThreshold() {
            for (float value : new float[] {
                    0x1.ffcp15f,
                    Math.nextDown(0x1.ffcp15f + 0x0.002p15f),
                    0x1.ffcp15f + 0x0.002p15f,
                    Math.nextUp(0x1.ffcp15f + 0x0.002p15f),
                    Float.MAX_VALUE,
                    Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY }) {
                assertThat(HalfFloats.of(value)).isEqualTo(Float.floatToFloat16(value));
                assertThat(HalfFloats.of(-value)).isEqualTo(Float.floatToFloat16(-value));
            }
        }

        @Test
        @DisplayName("at the smallest subnormal, and the midpoint below which even that goes")
        void underflowsAtTheThreshold() {
            for (float value : new float[] {
                    Float.MIN_VALUE,
                    0x1.0p-25f,
                    Math.nextDown(0x1.0p-25f),
                    Math.nextUp(0x1.0p-25f),
                    0x1.0p-24f,
                    Math.nextUp(0x1.0p-24f),
                    0.0f,
                    -0.0f }) {
                assertThat(HalfFloats.of(value)).isEqualTo(Float.floatToFloat16(value));
                assertThat(HalfFloats.of(-value)).isEqualTo(Float.floatToFloat16(-value));
            }
        }

        @Test
        @DisplayName("and on NaN payloads, which the JDK folds into the half")
        void nanPayloads() {
            for (int bits : new int[] {
                    0x7FC0_0000, 0x7F80_0001, 0x7FFF_FFFF, 0xFFC0_0001, 0xFFFF_FFFF }) {
                float value = Float.intBitsToFloat(bits);
                assertThat(HalfFloats.of(value)).isEqualTo(Float.floatToFloat16(value));
            }
        }
    }
}
