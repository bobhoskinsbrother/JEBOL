package org.jebol.domain.eval.compression.brotli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ExactFusedMultiplyAddTest {

    @Test
    @DisplayName("agrees with Math.fma on two hundred thousand random triples")
    void randomTriples() {
        Random random = new Random(20261012);
        for (int sample = 0; sample < 200_000; sample++) {
            double first = Math.scalb(random.nextDouble() - 0.5, random.nextInt(-600, 600));
            double second = Math.scalb(random.nextDouble() - 0.5, random.nextInt(-600, 600));
            double third = Math.scalb(random.nextDouble() - 0.5, random.nextInt(-600, 600));
            assertThat(ExactFusedMultiplyAdd.of(first, second, third))
                    .isEqualTo(Math.fma(first, second, third));
        }
    }

    @Test
    @DisplayName("agrees where rounding a product twice would go wrong")
    void roundingCases() {
        double[][] cases = {
                {0x1.0000_0000_0000_1p0, 0x1.0000_0000_0000_1p0, -1.0},
                {0x1.ffff_ffff_ffff_fp0, 0x1.0000_0000_0000_1p0, -0x1.ffff_ffff_ffff_fp0},
                {1.0, 0x1.0000_0000_0000_1p-53, 0.0},
                {0x1p-500, 0x1p-500, 0x1p1000},
                {1.0, 1.0, -1.0},
                {0.0, 0.0, 0.0}
        };
        for (double[] one : cases) {
            assertThat(ExactFusedMultiplyAdd.of(one[0], one[1], one[2]))
                    .as("%s * %s + %s", one[0], one[1], one[2])
                    .isEqualTo(Math.fma(one[0], one[1], one[2]));
        }
    }

    @Test
    @DisplayName("and follows the JDK through NaN, infinities and overflow")
    void specials() {
        double[][] cases = {
                {Double.NaN, 1.0, 1.0},
                {1.0, Double.NaN, 1.0},
                {1.0, 1.0, Double.NaN},
                {Double.POSITIVE_INFINITY, 1.0, 1.0},
                {Double.NEGATIVE_INFINITY, 1.0, Double.POSITIVE_INFINITY},
                {Double.POSITIVE_INFINITY, 0.0, 1.0},
                {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE},
                {Double.MIN_VALUE, 0.5, 0.0}
        };
        for (double[] one : cases) {
            assertThat(Double.valueOf(ExactFusedMultiplyAdd.of(one[0], one[1], one[2])))
                    .as("%s * %s + %s", one[0], one[1], one[2])
                    .isEqualTo(Double.valueOf(Math.fma(one[0], one[1], one[2])));
        }
    }
}
