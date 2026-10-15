package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyntaxErrorArgumentsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("a money amount that is not a number")
    class TheMoneyGroup {

        @Test
        @DisplayName("names money as the kind and the whole token as the text")
        void anOperatorRunIntoMoney() {
            assertThat(answerTo("""
                    e: try [load {$1*$2}] all [e/id = 'invalid e/arg2 = "$1*$2"]"""))
                    .isEqualTo(TRUE);
            assertThat(answerTo("""
                    e: try [load {$1+$2}] all [e/id = 'invalid e/arg2 = "$1+$2"]"""))
                    .isEqualTo(TRUE);
            assertThat(answerTo("""
                    e: try [load {$1-$2}] all [e/id = 'invalid e/arg2 = "$1-$2"]"""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the kind is money whatever the amount was")
        void theKindIsMoney() {
            assertThat(answerTo("""
                    e: try [load {$1*$2}] e/arg1""")).isEqualTo("\"money\"");
            assertThat(answerTo("""
                    e: try [load {$x}] e/arg1""")).isEqualTo("\"money\"");
        }

        @Test
        @DisplayName("and a money literal that is a number is still money")
        void whatSurvives() {
            assertThat(answerTo("""
                    money? load {$1}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    money? load {$1.50}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    money? load {-$1}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    length? load {$1 + $2}""")).isEqualTo("3");
        }
    }

    @Nested
    @DisplayName("the three fields say three different things")
    class TheThreeFields {

        @Test
        @DisplayName("ARG1 is the kind, ARG2 the token, NEAR the line")
        void eachFieldItsOwn() {
            assertThat(answerTo("""
                    e: try [load {$1*$2}] e/arg1""")).isEqualTo("\"money\"");
            assertThat(answerTo("""
                    e: try [load {$1*$2}] e/arg2""")).isEqualTo("\"$1*$2\"");
            assertThat(answerTo("""
                    e: try [load {$1*$2}] e/near""")).isEqualTo("\"(line 1) $1*$2\"");
        }

        @Test
        @DisplayName("and NEAR carries the whole line, not the token")
        void nearIsTheLine() {
            assertThat(answerTo("""
                    e: try [load {1 $1*$2}] e/near"""))
                    .isEqualTo("\"(line 1) 1 $1*$2\"");
            assertThat(answerTo("""
                    e: try [load {1 $1*$2}] e/arg2""")).isEqualTo("\"$1*$2\"");
        }

        @Test
        @DisplayName("and the line number counts newlines before it")
        void theLineNumber() {
            assertThat(answerTo("""
                    e: try [load {1^/$1*$2}] e/near"""))
                    .isEqualTo("\"(line 2) $1*$2\"");
        }
    }
}
