package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MakeCharFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheCodePointOfMaking(String from) {
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
        void decodesUtf8WhereTheFirstByteSaysTo(String from, String expected) {
            assertThat(readTheCodePointOfMaking(from)).isEqualTo(expected);
        }

        @Test
        @DisplayName("bytes after the first character are ignored")
        void ignoresBytesAfterTheFirstCharacter() {
            assertThat(readTheCodePointOfMaking("to-binary {ša}")).isEqualTo("353");
            assertThat(readTheCodePointOfMaking("#{3132333435}")).isEqualTo("49");
        }

        @Test
        @DisplayName("the boundary is above 0x80, not at it")
        void setsTheBoundaryAboveHex80() {
            assertThat(readTheCodePointOfMaking("#{7F}")).isEqualTo("127");
            assertThat(readTheCodePointOfMaking("#{80}"))
                    .as("the C tests *bp > 0x80, so 128 alone is a code point "
                            + "rather than a malformed lead byte")
                    .isEqualTo("128");
            assertThat(readTheCodePointOfMaking("#{81}")).isEqualTo("bad-make-arg");
        }

        @ParameterizedTest(name = "make char! {0} is refused")
        @ValueSource(strings = {"#{}", "#{81}", "#{C5}", "#{C541}", "#{FF}"})
        @DisplayName("and an empty or malformed binary is a bad make argument")
        void refusesAnEmptyOrMalformedBinary(String from) {
            assertThat(readTheCodePointOfMaking(from)).isEqualTo("bad-make-arg");
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
        void ignoresLeadingZeroes(String from, String expected) {
            assertThat(readTheCodePointOfMaking(from)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "make char! {0} is refused")
        @ValueSource(strings = {"#zz", "#6g", "#FFFFFFFFFFFFFFFFFF"})
        @DisplayName("and anything not hexadecimal throughout is refused")
        void refusesAnythingNotHexadecimal(String from) {
            assertThat(readTheCodePointOfMaking(from)).isEqualTo("bad-make-arg");
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
        void includeAnIntegerADecimalAndAString(String from, String expected) {
            assertThat(readTheCodePointOfMaking(from)).isEqualTo(expected);
        }

        @Test
        @DisplayName("an empty string is refused, and a code point Unicode has not")
        void refuseAnEmptyStringAndAMissingCodePoint() {
            assertThat(readTheCodePointOfMaking("{}")).isEqualTo("bad-make-arg");
            assertThat(readTheCodePointOfMaking("-1")).isEqualTo("invalid-char");
            assertThat(readTheCodePointOfMaking("1114112")).isEqualTo("invalid-char");
        }
    }

    @Nested
    @DisplayName("TO answers the same as MAKE, because the C shares the arm")
    class ToAnswersTheSame {

        @Test
        @DisplayName("case A_MAKE falls into case A_TO with no branch between them")
        void sharesTheArmWithMake() {
            assertThat(answerTo("to integer! to char! #{3132}")).isEqualTo("49");
            assertThat(answerTo("to integer! to char! #61")).isEqualTo("97");
        }
    }
}
