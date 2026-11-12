package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AnOperatorsUncheckedLeftOperandFromTheSourceTest {

    private static final String ASKING = """
            set/any (quote e) try [%s]
            print mold/flat either error? :e [reduce [e/id e/arg1 type? e/arg1 e/arg2]] [reduce [(quote value) :e]]
            """;

    private static String answerTo(String source) {
        StringBuilder printed = new StringBuilder();
        OutputPort output = printed::append;
        AnInterpreter.createWithOutput(output).run(ASKING.formatted(source));
        return printed.toString().strip();
    }

    @Nested
    @DisplayName("integer-divide reads its left operand's cell as a decimal")
    class IntegerDivide {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                none // 1   | [value 0]
                true // 1   | [value 0]
                {a} // 1    | [value 0]
                [1] // 1    | [value 0]
                #"a" // 1   | [value 0]
                1:00 // 1   | [value 0]
                1.2.3 // 1  | [value 0]
                1x2 // 1    | [value 2]
                1x2 // 2    | [value 1]
                3x9 // 1    | [value 262144]
                $5 // 1     | [value 0]
                none // 0   | [zero-divide _ #(none!) _]
                """)
        void dividesWhatTheCellHolds(String source, String expected) {
            assertThat(answerTo(source)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("modulo answers a left operand it cannot divide, and reads a time in seconds")
    class Modulo {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                none %% 1        | [value _]
                true %% 1        | [value #(true)]
                {a} %% 1         | [value "a"]
                none %% 0        | [zero-divide _ #(none!) _]
                #"a" %% 2        | [value #"^A"]
                $1 %% 2          | [value $1]
                1:00 %% 7        | [value 0:00:02]
                1:00 %% 0:00:07  | [value 0:00:02]
                -1:00 %% 7       | [value 0:00:05]
                1:00:01.5 %% 2   | [value 0:00:01.5]
                """)
        void answersAsModulusDoes(String source, String expected) {
            assertThat(answerTo(source)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("shift-left and shift-right want an integer on the left")
    class Shifting {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                none << 1  | [expect-val integer!: #(set-word!) _]
                true << 1  | [expect-val integer!: #(set-word!) #(true)]
                {a} << 1   | [expect-val integer!: #(set-word!) "a"]
                1.5 << 1   | [expect-val integer!: #(set-word!) 1.5]
                none >> 1  | [expect-val integer!: #(set-word!) _]
                """)
        void refusesWithExpectVal(String source, String expected) {
            assertThat(answerTo(source)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("an action dispatches on its left operand's datatype")
    class ActionDispatch {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                none ** 1   | [cannot-use power: #(set-word!) #(none!)]
                {a} ** 1    | [cannot-use power: #(set-word!) #(string!)]
                $2 ** 2     | [cannot-use power: #(set-word!) #(money!)]
                1:00 ** 2   | [not-related power: #(set-word!) #(time!)]
                1.2.3 ** 2  | [cannot-use power: #(set-word!) #(tuple!)]
                #"a" ** 2   | [cannot-use power: #(set-word!) #(char!)]
                [1] ** 2    | [cannot-use power: #(set-word!) #(block!)]
                1x2 ** 2    | [not-related power: #(set-word!) #(pair!)]
                true ** 1   | [expect-val #(logic!) #(datatype!) #(integer!)]
                none and 1  | [cannot-use and~: #(set-word!) #(none!)]
                $1 and 1    | [cannot-use and~: #(set-word!) #(money!)]
                1:00 and 1  | [not-related and~: #(set-word!) #(time!)]
                1:00 or 1   | [not-related or~: #(set-word!) #(time!)]
                1:00 xor 1  | [not-related xor~: #(set-word!) #(time!)]
                [1] and 1   | [cannot-use and~: #(set-word!) #(block!)]
                1.5 and 1   | [not-related and~: #(set-word!) #(decimal!)]
                1% and 1    | [not-related and~: #(set-word!) #(percent!)]
                {ab} and 1  | [invalid-arg 1 #(integer!) _]
                true and 1  | [expect-val #(logic!) #(datatype!) #(integer!)]
                true + 1    | [expect-val #(logic!) #(datatype!) #(integer!)]
                true * 1.5  | [expect-val #(logic!) #(datatype!) #(decimal!)]
                """)
        void refusesWhatItsDatatypeHasNoHandlerFor(String source, String expected) {
            assertThat(answerTo(source)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("a string combines its encoded bytes with a binary, as a binary does")
    class StringBesideBinary {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                {ab} and #{01}       | [value "^A^@"]
                {ab} or #{0101}      | [value "ac"]
                {é} and #{ff}        | [value "é"]
                {€} and #{ffff}      | [value "€"]
                {a€} and #{ff}       | [value "a€"]
                next {abc} and #{01} | [value "^@^A"]
                {abc} and #{}        | [value "^@^@^@"]
                {} and #{01}         | [value "^@"]
                %ab and #{01}        | [value %%01%00]
                {ab} and {cd}        | [expect-arg and #(word!) value2]
                #{0f} and {ab}       | [expect-arg and #(word!) value2]
                """)
        void answersTheSameKindOfString(String source, String expected) {
            assertThat(answerTo(source)).isEqualTo(expected);
        }
    }
}
