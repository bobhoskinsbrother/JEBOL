package org.jebol.domain.eval.blend2d;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class FusedMultiplyAddTest {

    private static boolean agree(float multiplier, float multiplicand, float addend) {
        float fused = Math.fma(multiplier, multiplicand, addend);
        float ported = FusedMultiplyAdd.multiplyThenAdd(multiplier, multiplicand, addend);
        return Float.floatToIntBits(fused) == Float.floatToIntBits(ported) || Float.isNaN(fused) && Float.isNaN(ported);
    }

    @Test
    @DisplayName("rounds once, as the JVM's own fma does, over random bit patterns, ordinary values and soft-light's own shape")
    void roundsOnce() {
        SplittableRandom random = new SplittableRandom(7);
        List<String> differing = new ArrayList<>();
        for (int at = 0; at < 300_000; at++) {
            float[] three = switch (at % 3) {
                case 0 -> new float[] {Float.intBitsToFloat(random.nextInt()), Float.intBitsToFloat(random.nextInt()),
                        Float.intBitsToFloat(random.nextInt())};
                case 1 -> new float[] {(float) random.nextDouble(-4, 4), (float) random.nextDouble(-4, 4),
                        (float) random.nextDouble(-4, 4)};
                default -> {
                    float dc = (float) random.nextDouble(0, 1);
                    yield new float[] {dc * 4.0f, dc, dc};
                }
            };
            if (!agree(three[0], three[1], three[2])) {
                differing.add(three[0] + " " + three[1] + " " + three[2]);
            }
        }
        assertThat(differing).isEmpty();
    }

    @Test
    @DisplayName("corrects a sum that lands exactly halfway between two floats toward the error a double hid")
    void correctsTheHalfwayCase() {
        float one = 1.0f;
        float justAbove = Math.nextUp(1.0f);
        float tiny = Math.scalb(1.0f, -48);
        assertThat(agree(justAbove, justAbove, -one)).isTrue();
        assertThat(agree(one, Math.scalb(1.0f, -24), one)).isTrue();
        assertThat(agree(one, Math.scalb(1.0f, -24), one + tiny)).isTrue();
        assertThat(agree(-one, Math.scalb(1.0f, -24), -one - tiny)).isTrue();
        assertThat(FusedMultiplyAdd.multiplyThenAdd(one, Math.scalb(1.0f, -24), one)).isEqualTo(1.0f);
    }
}
