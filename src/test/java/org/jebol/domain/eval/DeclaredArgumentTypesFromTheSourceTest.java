package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Seven arguments whose declared datatypes were wider here than in R3.
 *
 * <p>A declaration is behaviour: a datatype the spec block does not list is
 * turned away as {@code expect-arg} before the function's body ever runs, so
 * an argument declared too widely accepts a value a real Rebol refuses, and
 * then answers something rather than failing. Every refusal below was checked
 * against the 3.22.1 binary before it was written down, and the binary agreed
 * with the C in all seven.
 *
 * <p>Two of the seven read their declaration out of a comment in the C rather
 * than out of {@code boot/natives.reb}: {@code to-degrees} and {@code
 * to-radians} in {@code n-math.c}. Three more do the same and were reported
 * as differences only because the collector attached a {@code return:} block
 * to the argument in front of it, which is why {@code factorial} and {@code
 * grayscale} are not in this class.
 */
class DeclaredArgumentTypesFromTheSourceTest {

    private static final String REFUSED = "expect-arg";
    private static final String ACCEPTED = "accepted";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String whatHappensTo(String call) {
        return answerTo("either error? e: try [" + call + "] [e/id] ['accepted]");
    }

    @Nested
    @DisplayName("FRACTION takes a decimal and nothing else")
    class TheFractionalPart {

        @Test
        @DisplayName("a decimal is what it is for")
        void adecimalIsAccepted() {
            assertThat(answerTo("fraction 1.5")).isEqualTo("0.5");
        }

        @ParameterizedTest
        @ValueSource(strings = {"2", "1%", "$1", "1x1", "first {a}", "{a}"})
        @DisplayName("and a whole number is turned away with everything else")
        void everythingElseIsRefused(String written) {
            assertThat(whatHappensTo("fraction " + written))
                    .as("n-math.c declares number [decimal!], which excludes "
                            + "integer! and percent! that number! would have let in")
                    .isEqualTo(REFUSED);
        }
    }

    @Nested
    @DisplayName("TO-DEGREES and TO-RADIANS take a whole number or a decimal")
    class TheAngles {

        @Test
        @DisplayName("both forms of number are accepted")
        void numbersAreAccepted() {
            assertThat(whatHappensTo("to-degrees 1")).isEqualTo(ACCEPTED);
            assertThat(whatHappensTo("to-degrees 1.0")).isEqualTo(ACCEPTED);
            assertThat(whatHappensTo("to-radians 180")).isEqualTo(ACCEPTED);
            assertThat(answerTo("to-radians 0")).isEqualTo("0.0");
        }

        @ParameterizedTest
        @ValueSource(strings = {"1%", "$1", "1x1", "1.1.1", "1:00", "1-Jan-2000", "first {a}"})
        @DisplayName("and the seven other scalars are turned away")
        void theOtherScalarsAreRefused(String written) {
            assertThat(whatHappensTo("to-degrees " + written)).isEqualTo(REFUSED);
            assertThat(whatHappensTo("to-radians " + written)).isEqualTo(REFUSED);
        }
    }

    @Nested
    @DisplayName("CHECKSUM and COMPRESS take some strings and not others")
    class TheStringlikeArguments {

        @Test
        @DisplayName("a string and a binary are what both are for")
        void stringsAndBinariesAreAccepted() {
            assertThat(answerTo("checksum {a} 'md5"))
                    .isEqualTo("#{0CC175B9C0F1B6A831C399E269772661}");
            assertThat(answerTo("binary? compress {a} 'zlib")).isEqualTo("#(true)");
            assertThat(answerTo("to string! decompress compress {hello} 'zlib 'zlib"))
                    .as("and the pair of them still round-trips")
                    .isEqualTo("\"hello\"");
        }

        @ParameterizedTest
        @ValueSource(strings = {"http://a", "<a>", "a@b.c", "@a"})
        @DisplayName("but a url, a tag, an email and a ref are turned away by both")
        void theOtherStringsAreRefused(String written) {
            assertThat(whatHappensTo("checksum " + written + " 'md5"))
                    .as("n-strings.c declares data [binary! string! file!], which is "
                            + "narrower than any-string!")
                    .isEqualTo(REFUSED);
            assertThat(whatHappensTo("compress " + written + " 'zlib"))
                    .isEqualTo(REFUSED);
        }

        @Test
        @DisplayName("and COMPRESS turns a file away where CHECKSUM does not")
        void afileTellsThemApart() {
            assertThat(whatHappensTo("compress %a 'zlib"))
                    .as("COMPRESS declares [binary! string!] and CHECKSUM adds file!, "
                            + "which it hands to FILE-CHECKSUM")
                    .isEqualTo(REFUSED);
            assertThat(whatHappensTo("checksum %a 'md5"))
                    .as("a file! reaches the body, and whatever the body then says "
                            + "about a file that is not there, it is not the "
                            + "declaration turning it away")
                    .isNotEqualTo(REFUSED);
        }
    }

    @Nested
    @DisplayName("a /part limit of number! series! does not include a pair")
    class ThePartLimits {

        @Test
        @DisplayName("a whole number is a length, on both of them")
        void awholeNumberIsALength() {
            assertThat(answerTo("swap-endian/part #{0102} 2")).isEqualTo("#{0201}");
            assertThat(whatHappensTo("decompress/part compress {hello} 'zlib 'zlib 20"))
                    .isNotEqualTo(REFUSED);
        }

        @Test
        @DisplayName("and a pair is turned away, where REMOVE and COPY accept one")
        void apairIsRefused() {
            assertThat(whatHappensTo("swap-endian/part #{0102} 1x1"))
                    .as("f-series.c declares range [number! series!] and says nothing "
                            + "about pair!, so the /part limit shared with REMOVE is "
                            + "the wrong set to reach for")
                    .isEqualTo(REFUSED);
            assertThat(whatHappensTo("decompress/part #{} 'zlib 1x1"))
                    .isEqualTo(REFUSED);
        }

        @Test
        @DisplayName("and a series is a position rather than a count")
        void aseriesIsAPosition() {
            assertThat(whatHappensTo("swap-endian/part #{0102} tail #{0102}"))
                    .isNotEqualTo(REFUSED);
        }
    }
}
