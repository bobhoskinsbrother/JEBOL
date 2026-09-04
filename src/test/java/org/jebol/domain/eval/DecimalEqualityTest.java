package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code =} on decimals allows the last few bits to differ; {@code ==} does not.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1, boundary included.
 *
 * <p>The allowance is counted in steps of the floating point
 * representation rather than as a fixed amount, so it scales with the size
 * of the numbers. Ten steps or fewer is equal; more is not. A fixed
 * tolerance would be too coarse for small numbers and far too fine for
 * large ones at the same time.
 */
class DecimalEqualityTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a computed decimal equals the one it was meant to be")
    void aComputedDecimalEqualsItsWrittenForm() {
        assertThat(answerTo("(0.1 + 0.2) = 0.3")).isEqualTo("#(true)");
        assertThat(answerTo("0.5 = cosine 60")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the allowance runs out ten steps away")
    void theAllowanceEndsAtTenSteps() {
        assertThat(answerTo("1.0 = 1.000000000000002"))
                .as("about nine steps apart")
                .isEqualTo("#(true)");
        assertThat(answerTo("1.0 = 1.00000000000002"))
                .as("about ninety steps apart")
                .isEqualTo("#(false)");
    }

    @Test
    @DisplayName("the allowance scales with the size of the numbers")
    void theAllowanceIsRelativeNotFixed() {
        assertThat(answerTo("1000000.0 = 1000000.0000000002")).isEqualTo("#(true)");
        assertThat(answerTo("0.0001 = 0.0001000000000002")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("== is exact and minds the datatype")
    void strictEqualityAllowsNothing() {
        assertThat(answerTo("0.5 == 0.5000000000000001")).isEqualTo("#(false)");
        assertThat(answerTo("1 == 1.0")).isEqualTo("#(false)");
        assertThat(answerTo("1 = 1.0"))
                .as("= does not mind the datatype")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SAME? is exact too")
    void sameIsExact() {
        assertThat(answerTo("same? 0.5 0.5000000000000001")).isEqualTo("#(false)");
        assertThat(answerTo("same? 0.5 0.5")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a zero equals a negative zero and a NaN equals a NaN")
    void theHardwareIsOverruledAtBothEnds() {
        assertThat(answerTo("0.0 = -0.0")).isEqualTo("#(true)");
        assertThat(answerTo("(1.#NaN) = (1.#NaN)")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("numbers that are plainly different are still different")
    void ordinaryInequalityIsUnaffected() {
        assertThat(answerTo("1.0 = 1.1")).isEqualTo("#(false)");
        assertThat(answerTo("0.0 = 0.0000001")).isEqualTo("#(false)");
        assertThat(answerTo("1.0 = -1.0")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("an infinity equals itself and not the other one")
    void theInfinitiesAreToldApart() {
        assertThat(answerTo("(1.#INF) = (1.#INF)")).isEqualTo("#(true)");
        assertThat(answerTo("(1.#INF) = (-1.#INF)")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("EQUIV? is loose about the datatype and exact about the bits")
    void equivalenceSplitsTheDifference() {
        assertThat(answerTo("equiv? 1 1.0")).isEqualTo("#(true)");
        assertThat(answerTo("equiv? 0.5 0.5000000000000001")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("EQUIV? folds case, unlike STRICT-EQUAL?")
    void equivalenceFoldsCase() {
        assertThat(answerTo("equiv? 'a 'A")).isEqualTo("#(true)");
        assertThat(answerTo("strict-equal? \"a\" \"A\"")).isEqualTo("#(false)");
    }
}
