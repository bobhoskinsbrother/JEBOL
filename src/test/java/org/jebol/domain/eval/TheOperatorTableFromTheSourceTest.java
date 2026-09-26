package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TheOperatorTableFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("every symbol in the table stands for its prefix twin")
    class EverySymbolStandsForItsTwin {

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({
                "'1 + 2',        'add 1 2',                   3",
                "'5 - 2',        'subtract 5 2',              3",
                "'3 * 4',        'multiply 3 4',              12",
                "'10 / 4',       'divide 10 4',               2.5",
                "'10 // 3',      'integer-divide 10 3',       3",
                "'10 % 3',       'remainder 10 3',            1",
                "'-10 %% 3',     'modulo -10 3',              2",
                "'2 ** 8',       'power 2 8',                 256.0",
                "'1 = 1.0',      'equal? 1 1.0',              '#(true)'",
                "'1 =? 1.0',     'same? 1 1.0',               '#(false)'",
                "'1 == 1.0',     'strict-equal? 1 1.0',       '#(false)'",
                "'1 != 2',       'not-equal? 1 2',            '#(true)'",
                "'1 <> 2',       'not-equal? 1 2',            '#(true)'",
                "'1 !== 1.0',    'strict-not-equal? 1 1.0',   '#(true)'",
                "'1 < 2',        'lesser? 1 2',               '#(true)'",
                "'2 <= 2',       'lesser-or-equal? 2 2',      '#(true)'",
                "'3 > 2',        'greater? 3 2',              '#(true)'",
                "'2 >= 3',       'greater-or-equal? 2 3',     '#(false)'",
                "'12 & 10',      'and~ 12 10',                8",
                "'12 | 10',      'or~ 12 10',                 14",
                "'12 and 10',    'and~ 12 10',                8",
                "'12 or 10',     'or~ 12 10',                 14",
                "'12 xor 10',    'xor~ 12 10',                6",
                "'1 << 4',       'shift-left 1 4',            16",
                "'16 >> 2',      'shift-right 16 2',          4",
        })
        void theSymbolAndTheTwinAgree(String infix, String prefix, String expected) {
            assertThat(answerTo(infix)).isEqualTo(expected);
            assertThat(answerTo(prefix)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("what the table makes an operator, and what it leaves alone")
    class WhatIsAnOperator {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "+", "-", "*", "/", "//", "%", "%%", "**",
                "=", "=?", "==", "!=", "<>", "!==",
                "<", "<=", ">", ">=",
                "&", "|", "and", "or", "xor", "<<", ">>",
        })
        void eachListedSymbolTakesItsLeftHandArgument(String symbol) {
            assertThat(answerTo("op? get/any (quote " + symbol + ")"))
                    .isEqualTo("#(true)");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "add", "subtract", "multiply", "divide", "integer-divide",
                "remainder", "modulo", "power", "equal?", "same?",
                "strict-equal?", "not-equal?", "strict-not-equal?",
                "lesser?", "lesser-or-equal?", "greater?", "greater-or-equal?",
                "and~", "or~", "xor~", "shift-left", "shift-right",
        })
        void aPrefixTwinIsStillCalledInFront(String twin) {
            assertThat(answerTo("op? get/any (quote " + twin + ")"))
                    .isEqualTo("#(false)");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"mod", "shift", "not", "min", "max"})
        void aWordTheTableDoesNotListIsNotAnOperator(String word) {
            assertThat(answerTo("op? get/any (quote " + word + ")"))
                    .isEqualTo("#(false)");
        }
    }
}
