package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvalidUtfFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("bytes it accepts")
    class TheGoodBytes {

        @Test
        @DisplayName("no bytes at all")
        void includeTheEmptyBinary() {
            assertThat(answerTo("none? invalid-utf? #{}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a sequence of each length")
        void includeOneOfEachLength() {
            assertThat(answerTo("none? invalid-utf? #{41}")).isEqualTo(TRUE);
            assertThat(answerTo("none? invalid-utf? #{C3A9}")).isEqualTo(TRUE);
            assertThat(answerTo("none? invalid-utf? #{E282AC}")).isEqualTo(TRUE);
            assertThat(answerTo("none? invalid-utf? #{F09F9880}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the two halves of a surrogate pair, which UTF-8 has no business holding")
        void letASurrogatePairThrough() {
            assertThat(answerTo("none? invalid-utf? #{EDA080EDB080}")).isEqualTo(TRUE);
            assertThat(answerTo("none? invalid-utf? #{41EDA080EDB08042}")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("bytes it refuses, and where it says so")
    class TheBadBytes {

        @Test
        @DisplayName("a continuation byte with nothing leading it")
        void includeAStrayContinuation() {
            assertThat(answerTo("invalid-utf? #{80}")).isEqualTo("#{80}");
        }

        @Test
        @DisplayName("a sequence that stops before it is finished")
        void includeAnUnfinishedSequence() {
            assertThat(answerTo("invalid-utf? #{C3}")).isEqualTo("#{C3}");
            assertThat(answerTo("invalid-utf? #{E282}")).isEqualTo("#{E282}");
            assertThat(answerTo("invalid-utf? #{F09F98}")).isEqualTo("#{F09F98}");
        }

        @Test
        @DisplayName("a lead byte followed by the wrong thing, reported at the lead")
        void includeABrokenContinuation() {
            assertThat(answerTo("invalid-utf? #{C341}")).isEqualTo("#{C341}");
        }

        @Test
        @DisplayName("and after some good bytes it points past them")
        void skipPastTheGoodBytes() {
            assertThat(answerTo("invalid-utf? #{41C3}")).isEqualTo("#{C3}");
            assertThat(answerTo("index? invalid-utf? #{41C3}")).isEqualTo("2");
            assertThat(answerTo("index? invalid-utf? #{4142438042}")).isEqualTo("4");
        }

        @Test
        @DisplayName("an overlong encoding, which is a shorter way of writing the same thing")
        void includeAnOverlongEncoding() {
            assertThat(answerTo("invalid-utf? #{C080}")).isEqualTo("#{C080}");
            assertThat(answerTo("invalid-utf? #{E08080}")).isEqualTo("#{E08080}");
        }

        @Test
        @DisplayName("a codepoint past the last one there is")
        void includeBytesPastTheTopOfUnicode() {
            assertThat(answerTo("invalid-utf? #{F5808080}")).isEqualTo("#{F5808080}");
            assertThat(answerTo("invalid-utf? #{FF}")).isEqualTo("#{FF}");
        }

        @Test
        @DisplayName("half a surrogate pair on its own")
        void includeHalfASurrogatePair() {
            assertThat(answerTo("invalid-utf? #{EDA080}")).isEqualTo("#{EDA080}");
            assertThat(answerTo("invalid-utf? #{EDA080EDA080}")).isEqualTo("#{EDA080EDA080}");
        }
    }

    @Nested
    @DisplayName("the position it answers is absolute")
    class WhereItPoints {

        @Test
        @DisplayName("counted from the head, even when the binary was walked into")
        void countsFromTheHead() {
            assertThat(answerTo("index? invalid-utf? skip #{4141C3} 2")).isEqualTo("3");
            assertThat(answerTo("none? invalid-utf? skip #{80414141} 1")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("/UTF is declared and does nothing")
    class TheIdleRefinement {

        @Test
        @DisplayName("so the answer is the UTF-8 one whatever encoding was asked for")
        void ignoresTheEncoding() {
            assertThat(answerTo("none? invalid-utf?/utf #{41} 16")).isEqualTo(TRUE);
            assertThat(answerTo("invalid-utf?/utf #{C3} -16")).isEqualTo("#{C3}");
        }

        @Test
        @DisplayName("and the size has to be a whole number even so")
        void stillTypesTheSize() {
            assertThat(readTheErrorIdFrom("invalid-utf?/utf #{41} \"16\"")).isEqualTo("expect-arg");
            assertThat(readTheErrorIdFrom("invalid-utf?/utf #{41} 16.5")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("and the data must be a binary, not a string")
        void typesTheData() {
            assertThat(readTheErrorIdFrom("invalid-utf? \"abc\"")).isEqualTo("expect-arg");
        }
    }
}
