package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What a syntax error carries besides its id, from {@code Scan_Error} in
 * {@code rebol3-source/src/core/l-scan.c}.
 *
 * <p>Three fields, filled from three different places, and a script reads each for
 * something different:
 *
 * <pre>
 * Set_String(&amp;error-&gt;nearest, "(line " + line_count + ") " + the whole line);
 * Set_String(&amp;error-&gt;arg1, the token's name);
 * Set_String(&amp;error-&gt;arg2, Copy_Bytes(arg, size));   // the token's own text
 * </pre>
 *
 * <p>So ARG1 says what the reader was building, ARG2 says what it was reading, and
 * NEAR says where to look. Rebol's suite asserts on all three, in different groups:
 * the money group compares ARG2, the word and path cases compare ARG1, and the
 * TRANSCODE group compares NEAR.
 *
 * <p>ARG2 used to be given the whole line here, which put the same text in two
 * fields and left the one scripts compare with the wrong thing in it.
 */
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
