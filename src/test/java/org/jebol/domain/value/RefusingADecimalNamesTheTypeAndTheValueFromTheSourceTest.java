package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RefusingADecimalNamesTheTypeAndTheValueFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String refusalOf(String source) {
        return answerTo("e: try [" + source + "] "
                + "reduce [e/id mold e/arg1 e/arg2]");
    }

    @ParameterizedTest(name = "to decimal! {0}")
    @CsvSource(delimiter = '|', value = {
        "'\"abc\"'    | \"abc\"",
        "'\"1.2.3\"'  | \"1.2.3\"",
        "'[1 2 3]'    | [1 2 3]",
        "'[]'         | []",
        "'1x1'        | 1x1"})
    @DisplayName("arg1 is the datatype and arg2 is what was offered")
    void bothAreNamed(String written, String expectedArgTwo) {
        assertThat(refusalOf("to decimal! " + written))
                .isEqualTo("[bad-make-arg \"#(decimal!)\" " + expectedArgTwo + "]");
    }

    @Test
    @DisplayName("arg1 is a datatype a script can compare, not prose")
    void argOneIsADatatype() {
        assertThat(answerTo("e: try [to decimal! \"abc\"] "
                + "reduce [datatype? e/arg1  e/arg1 = decimal!]"))
                .isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("arg2 is the value itself, so a script can look at it")
    void argTwoIsTheValue() {
        assertThat(answerTo("e: try [to decimal! \"abc\"] "
                + "reduce [string? e/arg2  e/arg2]"))
                .isEqualTo("[#(true) \"abc\"]");
    }
}
