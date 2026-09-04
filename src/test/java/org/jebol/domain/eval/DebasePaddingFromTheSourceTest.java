package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEBASE, and what each base does with an input that does not divide evenly.
 *
 * <p>The three decoders in {@code f-enbase.c} do not agree, and JEBOL had them
 * agreeing. Base sixteen and base two both pad at the front: an odd number of
 * hex digits gains a leading zero nibble, and a run of bits that is not a
 * whole number of bytes gains leading zero bits. Base sixty-four refuses,
 * because four of its digits are three bytes and there is no shorter group.
 *
 * <p>The padding is decided from the length of the whole input, spaces
 * included, and the spaces are then stepped over without being counted. That
 * is why {@code debase "12 34" 16} fails while {@code debase "1234" 16} does
 * not: five characters is an odd length, so the decoder primes itself for a
 * leading nibble that never arrives.
 */
class DebasePaddingFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("failure: try [" + source + "] failure/id");
    }

    @Nested
    @DisplayName("base sixteen, where an odd length is a leading zero nibble")
    class BaseSixteen {

        @Test
        @DisplayName("an odd number of digits gains one at the front")
        void anOddNumberGainsOne() {
            assertThat(answerTo("""
                    reduce [debase "1" 16 debase "123" 16 debase "10400" 16]"""))
                    .isEqualTo("[#{01} #{0123} #{010400}]");
        }

        @Test
        @DisplayName("an even number is read as it stands, and nothing is nothing")
        void anEvenNumberIsReadAsItStands() {
            assertThat(answerTo("""
                    reduce [debase "1234" 16 debase "" 16]""")).isEqualTo("[#{1234} #{}]");
        }

        @Test
        @DisplayName("a space is stepped over but still counted in the length")
        void aSpaceIsSteppedOverButCounted() {
            assertThat(errorIdFrom("""
                    debase "12 34" 16""")).isEqualTo("invalid-data");
            assertThat(answerTo("""
                    debase "12  34" 16""")).isEqualTo("#{1234}");
        }

        @Test
        @DisplayName("and so is a line break")
        void andSoIsALineBreak() {
            assertThat(errorIdFrom("""
                    debase "12^/34" 16""")).isEqualTo("invalid-data");
            assertThat(answerTo("""
                    debase "12^/^/34" 16""")).isEqualTo("#{1234}");
        }

        @Test
        @DisplayName("anything that is not a digit or a space stops it")
        void anythingElseStopsIt() {
            assertThat(errorIdFrom("""
                    debase "zz" 16""")).isEqualTo("invalid-data");
            assertThat(errorIdFrom("""
                    debase "12,34" 16""")).isEqualTo("invalid-data");
        }
    }

    @Nested
    @DisplayName("base two, where a short run is leading zero bits")
    class BaseTwo {

        @Test
        @DisplayName("fewer than eight bits fill one byte")
        void fewerThanEightFillOne() {
            assertThat(answerTo("""
                    reduce [debase "0" 2 debase "01" 2 debase "00001" 2]"""))
                    .isEqualTo("[#{00} #{01} #{01}]");
        }

        @Test
        @DisplayName("exactly eight is one byte and nine is two")
        void exactlyEightAndNine() {
            assertThat(answerTo("""
                    reduce [debase "00000001" 2 debase "000000010" 2]"""))
                    .isEqualTo("[#{01} #{0002}]");
        }

        @Test
        @DisplayName("and a digit that is not a bit stops it")
        void aDigitThatIsNotABit() {
            assertThat(errorIdFrom("""
                    debase "012" 2""")).isEqualTo("invalid-data");
        }
    }

    @Nested
    @DisplayName("base sixty-four, which refuses a group it has only part of")
    class BaseSixtyFour {

        @Test
        @DisplayName("four digits are three bytes")
        void fourDigitsAreThreeBytes() {
            assertThat(answerTo("""
                    debase "YWJj" 64""")).isEqualTo("#{616263}");
        }

        @Test
        @DisplayName("one, two or three on their own are refused")
        void aPartialGroupIsRefused() {
            assertThat(errorIdFrom("""
                    debase "Y" 64""")).isEqualTo("invalid-data");
            assertThat(errorIdFrom("""
                    debase "YW" 64""")).isEqualTo("invalid-data");
            assertThat(errorIdFrom("""
                    debase "YWJ" 64""")).isEqualTo("invalid-data");
            assertThat(errorIdFrom("""
                    debase "abc" 64""")).isEqualTo("invalid-data");
        }

        @Test
        @DisplayName("the equals signs are how a short last group is written down")
        void theEqualsSignsAreThePadding() {
            assertThat(answerTo("""
                    reduce [debase "YWI=" 64 debase "YQ==" 64]"""))
                    .isEqualTo("[#{6162} #{61}]");
        }

        @Test
        @DisplayName("but one equals after two digits is not, the second being looked for")
        void oneEqualsAfterTwoDigits() {
            assertThat(errorIdFrom("""
                    debase "YQ=" 64""")).isEqualTo("invalid-data");
        }

        @Test
        @DisplayName("the URL-safe alphabet is allowed to end short")
        void urlSafeMayEndShort() {
            assertThat(answerTo("""
                    reduce [debase/url "YWJj" 64 debase/url "YWI" 64 debase/url "YQ" 64]"""))
                    .isEqualTo("[#{616263} #{6162} #{61}]");
        }
    }

    @Nested
    @DisplayName("what a string of any kind hands over")
    class TheSeriesContents {

        @Test
        @DisplayName("a tag gives its letters, not the brackets it is written in")
        void aTagGivesItsLetters() {
            assertThat(answerTo("""
                    reduce [debase <1234> 16 enbase <ab> 16]"""))
                    .isEqualTo("[#{1234} \"6162\"]");
        }

        @Test
        @DisplayName("and a file gives its name without the percent sign")
        void aFileGivesItsName() {
            assertThat(answerTo("""
                    reduce [debase %1234 16 enbase %ab 16]"""))
                    .isEqualTo("[#{1234} \"6162\"]");
        }

        @Test
        @DisplayName("a binary is its own bytes read as the letters they spell")
        void aBinaryIsItsOwnBytes() {
            assertThat(answerTo("""
                    debase #{31323334} 16""")).isEqualTo("#{1234}");
        }
    }
}
