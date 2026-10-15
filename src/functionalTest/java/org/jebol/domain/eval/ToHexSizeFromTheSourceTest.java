package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToHexSizeFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorFrom(String source) {
        return answerTo("e: try [" + source
                + "] either error? e [reduce [e/id e/arg1]] ['no-error]");
    }

    @Nested
    @DisplayName("the size, which is checked before the subject is looked at")
    class TheSize {

        @Test
        @DisplayName("nought or less is refused, and names the size")
        void noughtOrLessIsRefused() {
            assertThat(errorFrom("to-hex/size 255 0")).isEqualTo("[invalid-arg 0]");
            assertThat(errorFrom("to-hex/size 255 -1")).isEqualTo("[invalid-arg -1]");
        }

        @Test
        @DisplayName("and for a char and a tuple as much as a number")
        void forEverySubject() {
            assertThat(errorFrom("to-hex/size #\"a\" 0")).isEqualTo("[invalid-arg 0]");
            assertThat(errorFrom("to-hex/size 1.2.3 0")).isEqualTo("[invalid-arg 0]");
            assertThat(errorFrom("to-hex/size 1.2.3 -1")).isEqualTo("[invalid-arg -1]");
        }

        @Test
        @DisplayName("one is the smallest that works")
        void oneIsTheSmallestThatWorks() {
            assertThat(answerTo("to-hex/size 255 1")).isEqualTo("#F");
        }

        @Test
        @DisplayName("and the largest is what fits in thirty-two bits unsigned")
        void theLargestIsWhatFitsInThirtyTwoBits() {
            assertThat(answerTo("to-hex/size 255 4294967295"))
                    .isEqualTo("#00000000000000FF");
            assertThat(errorFrom("to-hex/size 255 4294967296"))
                    .isEqualTo("[invalid-arg 4294967296]");
        }

        @Test
        @DisplayName("and a size that is not a whole number is refused")
        void aSizeThatIsNotAWholeNumberIsRefused() {
            assertThat(errorFrom("to-hex/size 1 \"2\"")).isEqualTo("[expect-arg to-hex]");
            assertThat(errorFrom("to-hex/size 1 2.0")).isEqualTo("[expect-arg to-hex]");
            assertThat(errorFrom("to-hex/size 1 none")).isEqualTo("[expect-arg to-hex]");
        }
    }

    @Nested
    @DisplayName("a number or a char, right aligned in that many digits")
    class ANumber {

        @Test
        @DisplayName("padded on the left when the size is wider than the value")
        void paddedOnTheLeft() {
            assertThat(answerTo("to-hex/size 255 2")).isEqualTo("#FF");
            assertThat(answerTo("to-hex/size 255 15")).isEqualTo("#0000000000000FF");
        }

        @Test
        @DisplayName("and keeping its low digits when the size is narrower")
        void keepingItsLowDigits() {
            assertThat(answerTo("to-hex/size 255 1")).isEqualTo("#F");
            assertThat(answerTo("to-hex/size 4660 2")).isEqualTo("#34");
        }

        @Test
        @DisplayName("sixteen digits is the widest it will write")
        void sixteenIsTheWidest() {
            assertThat(answerTo("to-hex/size 255 16")).isEqualTo("#00000000000000FF");
            assertThat(answerTo("to-hex/size 255 17")).isEqualTo("#00000000000000FF");
            assertThat(answerTo("to-hex/size 255 99")).isEqualTo("#00000000000000FF");
        }

        @Test
        @DisplayName("and a char is sized the same way")
        void aCharIsSizedTheSameWay() {
            assertThat(answerTo("to-hex/size #\"a\" 4")).isEqualTo("#0061");
            assertThat(answerTo("to-hex/size #\"a\" 1")).isEqualTo("#1");
        }

        @Test
        @DisplayName("and without a size the width follows what it is")
        void withoutASizeTheWidthFollowsWhatItIs() {
            assertThat(answerTo("to-hex 255")).isEqualTo("#00000000000000FF");
            assertThat(answerTo("to-hex #\"a\"")).isEqualTo("#61");
        }
    }

    @Nested
    @DisplayName("a tuple, cut from the right")
    class ATuple {

        @Test
        @DisplayName("truncated to the size asked for")
        void truncatedToTheSizeAskedFor() {
            assertThat(answerTo("to-hex/size 1.2.3.4.5.6 8")).isEqualTo("#01020304");
            assertThat(answerTo("to-hex/size 1.2.3 4")).isEqualTo("#0102");
        }

        @Test
        @DisplayName("including an odd one, which stops mid-byte")
        void includingAnOddOne() {
            assertThat(answerTo("to-hex/size 1.2.3 3")).isEqualTo("#010");
            assertThat(answerTo("to-hex/size 1.2.3 1")).isEqualTo("#0");
            assertThat(answerTo("to-hex/size 1.2.3.4.5.6 11")).isEqualTo("#01020304050");
        }

        @Test
        @DisplayName("and a size wider than the tuple does not pad it")
        void aSizeWiderThanTheTupleDoesNotPadIt() {
            assertThat(answerTo("to-hex/size 1.2.3 6")).isEqualTo("#010203");
            assertThat(answerTo("to-hex/size 1.2.3 7")).isEqualTo("#010203");
            assertThat(answerTo("to-hex/size 1.2.3 8")).isEqualTo("#010203");
            assertThat(answerTo("to-hex/size 1.2.3 25")).isEqualTo("#010203");
            assertThat(answerTo("to-hex/size 1.2.3.4.5.6 12")).isEqualTo("#010203040506");
            assertThat(answerTo("to-hex/size 1.2.3.4.5.6 13")).isEqualTo("#010203040506");
        }

        @Test
        @DisplayName("and without a size it is two digits for every segment")
        void withoutASizeItIsTwoDigitsPerSegment() {
            assertThat(answerTo("to-hex 1.2.3")).isEqualTo("#010203");
            assertThat(answerTo("to-hex 1.2.3.4.5.6.7.8.9.10"))
                    .isEqualTo("#0102030405060708090A");
        }
    }

    @Test
    @DisplayName("and what is not a number, a char or a tuple is refused")
    void whatIsNotANumberACharOrATupleIsRefused() {
        assertThat(errorFrom("to-hex \"ff\"")).isEqualTo("[expect-arg to-hex]");
        assertThat(errorFrom("to-hex $0")).isEqualTo("[expect-arg to-hex]");
        assertThat(errorFrom("to-hex none")).isEqualTo("[expect-arg to-hex]");
        assertThat(errorFrom("to-hex 1.5")).isEqualTo("[expect-arg to-hex]");
    }
}
