package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What TO-HEX/SIZE does, and what it refuses.
 *
 * <p>{@code REBNATIVE(to_hex)} in {@code n-strings.c} checks the size before it
 * looks at the subject at all:
 *
 * <pre>
 * if (VAL_INT64(D_ARG(3)) &lt;= 0 || VAL_UNT64(D_ARG(3)) &gt; MAX_U32)
 *     Trap_Arg(D_ARG(3));
 * </pre>
 *
 * <p>None of that was here, and the three sizes it refuses each reached the
 * host as a Java exception rather than a REBOL error --
 * {@code IllegalArgumentException: a word needs a spelling} for nought, because
 * an issue with no spelling is not a value REBOL has, and
 * {@code StringIndexOutOfBoundsException} for a negative one.
 * {@code spec/embed.allium} says nothing a script does may reach the host as a
 * throwable, so these were a defect of a different kind from a wrong answer.
 *
 * <p>The third crash was not on any list: {@code to-hex/size 255 4294967295} is
 * the largest size the C accepts, and the width arrived here as a Java
 * {@code int} that had already wrapped to -1.
 *
 * <p>/SIZE then means two different things by datatype, and the difference is
 * not arbitrary. An integer is a number, so it is right aligned and its low
 * digits are the ones kept. A tuple is a run of bytes in the order they were
 * written, so it is cut from the right. Every expectation was read off
 * {@code ./r3-head} first.
 */
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

        /**
         * Nought is the one worth stating rather than assuming: a width of
         * nought leaves an issue with no spelling, and there is no such value.
         * The refusal names the size because the size is what the caller got
         * wrong.
         */
        @Test
        @DisplayName("nought or less is refused, and names the size")
        void noughtOrLessIsRefused() {
            assertThat(errorFrom("to-hex/size 255 0")).isEqualTo("[invalid-arg 0]");
            assertThat(errorFrom("to-hex/size 255 -1")).isEqualTo("[invalid-arg -1]");
        }

        /** For every subject, because the check comes before the type branch. */
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

        /**
         * The ceiling is what fits in thirty-two bits unsigned, and it is a
         * ceiling on the argument rather than on the answer -- the answer is
         * sixteen digits long either way.
         */
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

        /** The low digits are the ones that matter, so it cuts from the left. */
        @Test
        @DisplayName("and keeping its low digits when the size is narrower")
        void keepingItsLowDigits() {
            assertThat(answerTo("to-hex/size 255 1")).isEqualTo("#F");
            assertThat(answerTo("to-hex/size 4660 2")).isEqualTo("#34");
        }

        /**
         * Sixteen is the ceiling on the answer: {@code if (len == NO_LIMIT ||
         * len > MAX_HEX_LEN) len = MAX_HEX_LEN;}. Asking for more answers
         * sixteen rather than a wider number.
         */
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

        /** Without a size, the width follows the character rather than being fixed. */
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

        /**
         * The other way about from a number. A tuple is a run of bytes in the
         * order they were written, so the size says how many digits to keep
         * from the left and the rest are dropped.
         */
        @Test
        @DisplayName("truncated to the size asked for")
        void truncatedToTheSizeAskedFor() {
            assertThat(answerTo("to-hex/size 1.2.3.4.5.6 8")).isEqualTo("#01020304");
            assertThat(answerTo("to-hex/size 1.2.3 4")).isEqualTo("#0102");
        }

        /** Including an odd size, which cuts a byte in half. */
        @Test
        @DisplayName("including an odd one, which stops mid-byte")
        void includingAnOddOne() {
            assertThat(answerTo("to-hex/size 1.2.3 3")).isEqualTo("#010");
            assertThat(answerTo("to-hex/size 1.2.3 1")).isEqualTo("#0");
            assertThat(answerTo("to-hex/size 1.2.3.4.5.6 11")).isEqualTo("#01020304050");
        }

        /**
         * The ceiling is twice the tuple's own length rather than sixteen, and
         * it is the tuple that decides it. A size wider than the tuple does not
         * pad it out -- there is nothing to pad with that would not be a
         * different colour.
         */
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
