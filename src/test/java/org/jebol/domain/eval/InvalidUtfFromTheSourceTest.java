package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * INVALID-UTF?, from {@code REBNATIVE(invalid_utfq)} and {@code UTF8_Check}.
 *
 * <p>The whole native is four lines: check the bytes, answer none if they are
 * all right, otherwise answer the binary standing at the trouble. What it
 * answers is a position and not a count, which is what lets a caller carry on
 * reading from there.
 *
 * <p>The position is the start of the sequence that failed, not the byte that
 * gave it away. {@code UTF8_Check} keeps a pointer to the last character it
 * accepted and answers {@code acc + 1}, so a broken two-byte sequence is
 * reported at its lead byte even though the decoder only found out on the
 * second one.
 *
 * <p>Two things it does that a strict decoder would not. An unfinished sequence
 * at the very end is a failure -- the loop ends with the state part way through
 * and the last line answers {@code acc + 1} for it. And a surrogate pair
 * written as two three-byte sequences is accepted, which no well-formed UTF-8
 * has: the decoder rejects the first half, then {@code Decode_Surrogate_Pair}
 * looks at the six bytes together and lets them through.
 *
 * <p>/UTF and its NUM are declared and never read. {@code data} is the only
 * argument the C touches, so asking for another encoding gets the UTF-8 answer.
 * They are here so that a script written for Rebol can make the call at all.
 */
class InvalidUtfFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("bytes it accepts")
    class TheGoodBytes {

        @Test
        @DisplayName("no bytes at all")
        void theEmptyBinary() {
            // `if (len == 0) return 0;` is the first line.
            assertThat(answerTo("none? invalid-utf? #{}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a sequence of each length")
        void oneOfEachLength() {
            assertThat(answerTo("none? invalid-utf? #{41}")).isEqualTo(TRUE);
            assertThat(answerTo("none? invalid-utf? #{C3A9}")).isEqualTo(TRUE);
            assertThat(answerTo("none? invalid-utf? #{E282AC}")).isEqualTo(TRUE);
            assertThat(answerTo("none? invalid-utf? #{F09F9880}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the two halves of a surrogate pair, which UTF-8 has no business holding")
        void aSurrogatePairIsLetThrough() {
            // ED A0 80 is U+D800 and ED B0 80 is U+DC00. The decoder rejects
            // the first, and then `Decode_Surrogate_Pair` reads all six bytes
            // and finds a high half followed by a low one, so both are skipped
            // and the check carries on.
            assertThat(answerTo("none? invalid-utf? #{EDA080EDB080}")).isEqualTo(TRUE);
            assertThat(answerTo("none? invalid-utf? #{41EDA080EDB08042}")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("bytes it refuses, and where it says so")
    class TheBadBytes {

        @Test
        @DisplayName("a continuation byte with nothing leading it")
        void aStrayContinuation() {
            assertThat(answerTo("invalid-utf? #{80}")).isEqualTo("#{80}");
        }

        @Test
        @DisplayName("a sequence that stops before it is finished")
        void anUnfinishedSequence() {
            // The state is left part way through when the bytes run out, and
            // the last line of the check answers the position anyway.
            assertThat(answerTo("invalid-utf? #{C3}")).isEqualTo("#{C3}");
            assertThat(answerTo("invalid-utf? #{E282}")).isEqualTo("#{E282}");
            assertThat(answerTo("invalid-utf? #{F09F98}")).isEqualTo("#{F09F98}");
        }

        @Test
        @DisplayName("a lead byte followed by the wrong thing, reported at the lead")
        void aBrokenContinuation() {
            // The decoder finds out on the second byte and the answer points
            // at the first: `acc + 1` is one past the last whole character.
            assertThat(answerTo("invalid-utf? #{C341}")).isEqualTo("#{C341}");
        }

        @Test
        @DisplayName("and after some good bytes it points past them")
        void theGoodBytesAreSkipped() {
            assertThat(answerTo("invalid-utf? #{41C3}")).isEqualTo("#{C3}");
            assertThat(answerTo("index? invalid-utf? #{41C3}")).isEqualTo("2");
            assertThat(answerTo("index? invalid-utf? #{4142438042}")).isEqualTo("4");
        }

        @Test
        @DisplayName("an overlong encoding, which is a shorter way of writing the same thing")
        void anOverlongEncoding() {
            // C0 and C1 can only ever begin an overlong two-byte form of an
            // ASCII character, so the table refuses them outright rather than
            // waiting for the second byte.
            assertThat(answerTo("invalid-utf? #{C080}")).isEqualTo("#{C080}");
            assertThat(answerTo("invalid-utf? #{E08080}")).isEqualTo("#{E08080}");
        }

        @Test
        @DisplayName("a codepoint past the last one there is")
        void pastTheTopOfUnicode() {
            // F5 and up would decode above U+10FFFF, which Unicode does not
            // reach.
            assertThat(answerTo("invalid-utf? #{F5808080}")).isEqualTo("#{F5808080}");
            assertThat(answerTo("invalid-utf? #{FF}")).isEqualTo("#{FF}");
        }

        @Test
        @DisplayName("half a surrogate pair on its own")
        void halfASurrogatePair() {
            // The allowance is for the pair. One half has nothing to pair with,
            // so the rejection stands.
            assertThat(answerTo("invalid-utf? #{EDA080}")).isEqualTo("#{EDA080}");
            assertThat(answerTo("invalid-utf? #{EDA080EDA080}")).isEqualTo("#{EDA080EDA080}");
        }
    }

    @Nested
    @DisplayName("the position it answers is absolute")
    class WhereItPoints {

        @Test
        @DisplayName("counted from the head, even when the binary was walked into")
        void countedFromTheHead() {
            // `VAL_INDEX(arg) = bp - VAL_BIN_HEAD(arg)` -- from the head, while
            // the check itself started at `VAL_BIN_DATA(arg)`, which is from
            // the position. So a caller that skipped the good bytes still gets
            // an index it can use against the whole binary.
            assertThat(answerTo("index? invalid-utf? skip #{4141C3} 2")).isEqualTo("3");
            assertThat(answerTo("none? invalid-utf? skip #{80414141} 1")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("/UTF is declared and does nothing")
    class TheIdleRefinement {

        @Test
        @DisplayName("so the answer is the UTF-8 one whatever encoding was asked for")
        void theEncodingIsIgnored() {
            // The C reads D_ARG(1) and nothing else. Positive means big-endian
            // and negative little-endian, says the help text, and no line of
            // the function looks at either.
            assertThat(answerTo("none? invalid-utf?/utf #{41} 16")).isEqualTo(TRUE);
            assertThat(answerTo("invalid-utf?/utf #{C3} -16")).isEqualTo("#{C3}");
        }

        @Test
        @DisplayName("and the size has to be a whole number even so")
        void theSizeIsStillTyped() {
            assertThat(errorIdFrom("invalid-utf?/utf #{41} \"16\"")).isEqualTo("expect-arg");
            assertThat(errorIdFrom("invalid-utf?/utf #{41} 16.5")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("and the data must be a binary, not a string")
        void theDataIsTyped() {
            // `data [binary!]`, so text has to be converted first -- which is
            // the point of the function: a string is already decoded.
            assertThat(errorIdFrom("invalid-utf? \"abc\"")).isEqualTo("expect-arg");
        }
    }
}
