package org.jebol.domain.eval.natives;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.IntegerValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToDegreesAndToRadiansFromTheSourceTest {

    private String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String refusalOf(String call) {
        return answerTo("e: try [" + call + "] reduce [e/id e/arg1 e/arg2 e/arg3]");
    }

    @Test
    @DisplayName("each declares one argument of integer! or decimal!, and no percent!, as R3 does")
    void eachDeclaresAnIntegerOrADecimal() {
        for (DefaultNative function : List.<DefaultNative>of(new ToDegreesNative(), new ToRadiansNative())) {
            assertThat(function.parametersAsWritten()).hasSize(1);
            assertThat(function.parametersAsWritten().getFirst().acceptedTypes())
                    .containsExactlyInAnyOrder(IntegerValue.TYPE, DecimalValue.TYPE);
        }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "10%      | percent!",
            "{1}      | string!",
            "none     | none!",
            "1x2      | pair!",
            "true     | logic!",
            "[1]      | block!",
            "$1       | money!"})
    @DisplayName("TO-DEGREES refuses anything but an integer or a decimal with expect-arg naming radians")
    void toDegreesRefuses(String given, String datatype) {
        assertThat(refusalOf("to-degrees " + given))
                .isEqualTo("[expect-arg to-degrees radians #(" + datatype + ")]");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "10%      | percent!",
            "{1}      | string!",
            "none     | none!",
            "1x2      | pair!",
            "true     | logic!",
            "[1]      | block!",
            "$1       | money!"})
    @DisplayName("TO-RADIANS refuses anything but an integer or a decimal with expect-arg naming degrees")
    void toRadiansRefuses(String given, String datatype) {
        assertThat(refusalOf("to-radians " + given))
                .isEqualTo("[expect-arg to-radians degrees #(" + datatype + ")]");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "to-degrees 0                  | 0.0",
            "to-degrees 1                  | 57.2957795130823",
            "to-degrees -1                 | -57.2957795130823",
            "to-degrees 0.0                | 0.0",
            "to-degrees 3.141592653589793  | 180.0",
            "to-radians 0                  | 0.0",
            "to-radians 1                  | 0.0174532925199433",
            "to-radians -1                 | -0.0174532925199433",
            "to-radians 180                | 3.14159265358979"})
    @DisplayName("an integer or a decimal converts, nought, one and minus one included")
    void anIntegerOrADecimalConverts(String call, String answer) {
        assertThat(answerTo(call)).isEqualTo(answer);
    }
}
