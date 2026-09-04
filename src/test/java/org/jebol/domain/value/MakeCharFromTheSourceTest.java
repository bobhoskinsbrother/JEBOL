package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The six things {@code make char!} takes, from {@code t-char.c}.
 *
 * <p>The C's {@code A_MAKE} arm switches on six datatypes and JEBOL answered
 * only three of them, so {@code make char! #"a"} was a bad make argument and
 * so were both {@code make char! #{3132}} and {@code make char! #61}.
 * make-test.r3 asserts the binary form four times.
 *
 * <p>The boundary worth naming is that the C writes {@code *bp > 0x80} rather
 * than {@code >= 0x80}. A binary opening with the byte 128 is therefore code
 * point 128 taken literally, while a binary opening with 129 is refused for
 * being a continuation byte with no lead in front of it.
 *
 * <p>Everything is asserted as a code point rather than as a molded character
 * so that no expectation here needs a quote in it. Every one was run against a
 * Rebol built by {@code scripts/build-r3.sh} before it was written down.
 */
class MakeCharFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String codePointOfMaking(String from) {
        return answerTo("either error? e: try [make char! " + from + "] "
                + "[e/id] [to integer! e]");
    }

    @Nested
    @DisplayName("a binary gives the character it opens with")
    class FromABinary {

        @ParameterizedTest(name = "make char! {0} is code point {1}")
        @CsvSource({
            "#{3132},   49",
            "#{00},     0",
            "#{C5A1},   353",
            "#{E282AC}, 8364",
        })
        @DisplayName("decoding UTF-8 where the first byte says to")
        void decodingUtf8WhereTheFirstByteSaysTo(String from, String expected) {
            assertThat(codePointOfMaking(from)).isEqualTo(expected);
        }

        @Test
        @DisplayName("bytes after the first character are ignored")
        void bytesAfterTheFirstCharacterAreIgnored() {
            assertThat(codePointOfMaking("to-binary {ša}")).isEqualTo("353");
            assertThat(codePointOfMaking("#{3132333435}")).isEqualTo("49");
        }

        @Test
        @DisplayName("the boundary is above 0x80, not at it")
        void theBoundaryIsAboveNotAt() {
            assertThat(codePointOfMaking("#{7F}")).isEqualTo("127");
            assertThat(codePointOfMaking("#{80}"))
                    .as("the C tests *bp > 0x80, so 128 alone is a code point "
                            + "rather than a malformed lead byte")
                    .isEqualTo("128");
            assertThat(codePointOfMaking("#{81}")).isEqualTo("bad-make-arg");
        }

        @ParameterizedTest(name = "make char! {0} is refused")
        @ValueSource(strings = {"#{}", "#{81}", "#{C5}", "#{C541}", "#{FF}"})
        @DisplayName("and an empty or malformed binary is a bad make argument")
        void anEmptyOrMalformedBinary(String from) {
            assertThat(codePointOfMaking(from)).isEqualTo("bad-make-arg");
        }
    }

    @Nested
    @DisplayName("an issue spells the code point in hexadecimal")
    class FromAnIssue {

        @ParameterizedTest(name = "make char! {0} is code point {1}")
        @CsvSource({
            "#61,   97",
            "#0061, 97",
            "#41,   65",
            "#161,  353",
        })
        @DisplayName("with leading zeroes making no difference")
        void withLeadingZeroesMakingNoDifference(String from, String expected) {
            assertThat(codePointOfMaking(from)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "make char! {0} is refused")
        @ValueSource(strings = {"#zz", "#6g", "#FFFFFFFFFFFFFFFFFF"})
        @DisplayName("and anything not hexadecimal throughout is refused")
        void anythingNotHexadecimalThroughout(String from) {
            assertThat(codePointOfMaking(from)).isEqualTo("bad-make-arg");
        }
    }

    @Nested
    @DisplayName("the four that were already there")
    class TheOnesAlreadyThere {

        @Test
        @DisplayName("a character makes itself, which is the identity case")
        void acharacterMakesItself() {
            assertThat(answerTo("""
                    lower-case-a: #"a"
                    to integer! make char! lower-case-a""")).isEqualTo("97");
        }

        @ParameterizedTest(name = "make char! {0} is code point {1}")
        @CsvSource({
            "97,    97",
            "97.9,  97",
            "{abc}, 97",
        })
        @DisplayName("an integer, a decimal and a string")
        void anIntegerADecimalAndAString(String from, String expected) {
            assertThat(codePointOfMaking(from)).isEqualTo(expected);
        }

        @Test
        @DisplayName("an empty string is refused, and a code point Unicode has not")
        void anEmptyStringAndACodePointUnicodeHasNot() {
            assertThat(codePointOfMaking("{}")).isEqualTo("bad-make-arg");
            assertThat(codePointOfMaking("-1")).isEqualTo("invalid-char");
            assertThat(codePointOfMaking("1114112")).isEqualTo("invalid-char");
        }
    }

    @Nested
    @DisplayName("TO answers the same as MAKE, because the C shares the arm")
    class ToAnswersTheSame {

        @Test
        @DisplayName("case A_MAKE falls into case A_TO with no branch between them")
        void caseMakeFallsIntoCaseTo() {
            assertThat(answerTo("to integer! to char! #{3132}")).isEqualTo("49");
            assertThat(answerTo("to integer! to char! #61")).isEqualTo("97");
        }
    }
}
