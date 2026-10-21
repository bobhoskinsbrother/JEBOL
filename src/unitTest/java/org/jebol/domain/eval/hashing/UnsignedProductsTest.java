package org.jebol.domain.eval.hashing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class UnsignedProductsTest {

    @Test
    @DisplayName("agrees with Math.unsignedMultiplyHigh on a quarter of a million pairs")
    void randomPairs() {
        Random random = new Random(20261012);
        for (int sample = 0; sample < 250_000; sample++) {
            long left = random.nextLong();
            long right = random.nextLong();
            assertThat(UnsignedProducts.multiplyForHighHalf(left, right))
                    .isEqualTo(Math.unsignedMultiplyHigh(left, right));
        }
    }

    @Test
    @DisplayName("agrees on the patterns either side of every 32-bit boundary")
    void boundaryPatterns() {
        long[] patterns = {
                0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE,
                0xFFFF_FFFFL, 0x1_0000_0000L, -0x1_0000_0000L,
                0xFFFF_FFFF_0000_0000L, 0x0000_0000_FFFF_FFFFL,
                0x8000_0000L, -0x8000_0000L, 0x7FFF_FFFF_FFFF_FFFFL,
                -0x7FFF_FFFF_FFFF_FFFFL, 0x4000_0000_0000_0000L, 0xFFFF_FFFF_FFFF_FFFEL
        };
        for (long left : patterns) {
            for (long right : patterns) {
                assertThat(UnsignedProducts.multiplyForHighHalf(left, right))
                        .isEqualTo(Math.unsignedMultiplyHigh(left, right));
            }
        }
    }
}
