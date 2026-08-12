package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The trigonometric and logarithmic family declares {@code number!} in the
 * C - integer!, decimal! and percent! - and nothing else; {@code absolute}
 * declares {@code number! pair! money! time!}. JEBOL accepted six datatypes
 * more, which is the quiet half of a surface gap: calls that should fail
 * and do not.
 */
class MathsFamilyTypesFromTheSourceTest {

    private static final List<String> TAKES_ONLY_NUMBERS = List.of(
            "sine", "cosine", "tangent", "arcsine", "arccosine", "arctangent",
            "square-root", "log-10", "log-2", "log-e", "exp");

    private static final List<String> ONLY_LOOKS_NUMERIC = List.of(
            "#\"a\"", "1-Jan-2000", "$1", "1x2", "0:0:1", "1.2.3");

    private static String errorIdOf(Interpreter interpreter, String source) {
        String wrapped = "e: try [" + source + "] either error? e [e/id] ['no-error]";
        interpreter.defineFreshWordsIn(wrapped);
        return interpreter.display(interpreter.run(wrapped));
    }

    @Test
    @DisplayName("the family refuses everything that only looks numeric")
    void theFamilyRefusesWhatOnlyLooksNumeric() {
        Interpreter interpreter = Interpreter.create();
        for (String function : TAKES_ONLY_NUMBERS) {
            for (String value : ONLY_LOOKS_NUMERIC) {
                assertThat(errorIdOf(interpreter, function + " " + value))
                        .as("%s must refuse %s as a real R3 does", function, value)
                        .isEqualTo("expect-arg");
            }
        }
    }

    @Test
    @DisplayName("every real number is still taken")
    void everyRealNumberIsStillTaken() {
        Interpreter interpreter = Interpreter.create();
        for (String function : TAKES_ONLY_NUMBERS) {
            for (String value : List.of("1", "0.5", "50%")) {
                assertThat(errorIdOf(interpreter, function + " " + value))
                        .as("%s must still take %s", function, value)
                        .isNotEqualTo("expect-arg");
            }
        }
    }

    @Test
    @DisplayName("AS-PAIR takes numbers alone, in both places")
    void asPairTakesNumbersAlone() {
        Interpreter interpreter = Interpreter.create();
        assertThat(errorIdOf(interpreter, "as-pair #\"a\" 1")).isEqualTo("expect-arg");
        assertThat(errorIdOf(interpreter, "as-pair 1 0:0:1")).isEqualTo("expect-arg");
        assertThat(errorIdOf(interpreter, "as-pair 1 2.5")).isEqualTo("no-error");
    }

    @Test
    @DisplayName("ABSOLUTE measures numbers, pairs, money and time - not a char")
    void absoluteRefusesAChar() {
        Interpreter interpreter = Interpreter.create();
        assertThat(errorIdOf(interpreter, "absolute #\"a\"")).isEqualTo("expect-arg");
        assertThat(errorIdOf(interpreter, "abs #\"a\"")).isEqualTo("expect-arg");
        for (String value : List.of("-1", "-1x2", "-$1", "-0:0:1")) {
            assertThat(errorIdOf(interpreter, "absolute " + value))
                    .as("absolute must still take %s", value)
                    .isEqualTo("no-error");
        }
    }
}
