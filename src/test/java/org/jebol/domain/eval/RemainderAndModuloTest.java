package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * REMAINDER and MODULO are different functions, and % is the first one.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3
 * at every combination of signs.
 *
 * <p>They agree whenever both operands are positive, which is most code,
 * so the boundaries that matter are the sign combinations: a negative
 * dividend, a negative divisor, and both. REMAINDER takes its sign from
 * the dividend; MODULO is never negative at all.
 */
class RemainderAndModuloTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("% is REMAINDER, so a negative dividend gives a negative answer")
    void percentTakesItsSignFromTheDividend() {
        assertThat(answerTo("-7 % 3")).isEqualTo("-1");
    }

    @Test
    @DisplayName("% at every combination of signs")
    void percentAcrossTheSignCombinations() {
        assertThat(answerTo("mold reduce [-7 % 3   7 % -3   -7 % -3   7 % 3]"))
                .isEqualTo("\"[-1 1 -1 1]\"");
    }

    @Test
    @DisplayName("REMAINDER answers exactly what % answers")
    void remainderMatchesTheOperator() {
        assertThat(answerTo(
                "mold reduce [remainder -7 3  remainder 7 -3  "
                        + "remainder -7 -3  remainder 7 3]"))
                .isEqualTo("\"[-1 1 -1 1]\"");
    }

    @Test
    @DisplayName("MOD is REMAINDER too, despite the name")
    void modIsInTheRemainderGroup() {
        assertThat(answerTo("mold reduce [mod -7 3  mod 7 -3  mod -7 -3  mod 7 3]"))
                .isEqualTo("\"[-1 1 -1 1]\"");
    }

    @Test
    @DisplayName("%% is MODULO, so the two operators are not variants of one thing")
    void doublePercentIsInTheModuloGroup() {
        assertThat(answerTo("mold reduce [-7 %% 3  7 %% -3  -7 %% -3  7 %% 3]"))
                .isEqualTo("\"[2 1 2 1]\"");
    }

    @Test
    @DisplayName("MODULO is never negative, whatever the signs")
    void moduloIsNeverNegative() {
        assertThat(answerTo(
                "mold reduce [modulo -7 3  modulo 7 -3  modulo -7 -3  modulo 7 3]"))
                .isEqualTo("\"[2 1 2 1]\"");
    }

    @Test
    @DisplayName("the two agree when both operands are positive")
    void theyAgreeOnPositives() {
        assertThat(answerTo(
                "mold reduce [7 % 3  7 %% 3  mod 7 3  remainder 7 3  modulo 7 3]"))
                .isEqualTo("\"[1 1 1 1 1]\"");
    }

    @Test
    @DisplayName("an exact division leaves nothing over, either way")
    void anExactDivisionIsTheDegenerateCase() {
        assertThat(answerTo("mold reduce [-6 % 3  remainder -6 3  modulo -6 3]"))
                .isEqualTo("\"[0 0 0]\"");
    }

    @Test
    @DisplayName("% walks the whole range the way R3 does")
    void percentAcrossARange() {
        assertThat(answerTo("b: copy [] for i -7 7 1 [append b i % -3] mold b"))
                .isEqualTo("\"[-1 0 -2 -1 0 -2 -1 0 1 2 0 1 2 0 1]\"");
    }

    @Test
    @DisplayName("decimals follow the same split")
    void decimalsSplitTheSameWay() {
        assertThat(answerTo("mold reduce [remainder -5.5 2  modulo -5.5 2]"))
                .isEqualTo("\"[-1.5 0.5]\"");
    }

    @Test
    @DisplayName("dividing by zero raises through either name")
    void dividingByZeroRaises() {
        String probe = "e: try [%s] either error? e [e/id] ['no-error]";
        assertThat(answerTo(probe.formatted("5 % 0"))).isEqualTo("zero-divide");
        assertThat(answerTo(probe.formatted("remainder 5 0"))).isEqualTo("zero-divide");
        assertThat(answerTo(probe.formatted("modulo 5 0"))).isEqualTo("zero-divide");
    }
}
