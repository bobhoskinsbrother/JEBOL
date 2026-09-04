package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BYTES, and the count that follows it.
 *
 * <p>{@code next = ++value; if (IS_END(next)) { n = tail - index; value--; }
 * else { if (!IS_INTEGER(next)) Trap1(RE_INVALID_SPEC); n = VAL_INT32(next);
 * \}} -- so BYTES takes an optional count, and anything after it that is not
 * a number is a bad spec rather than the next code.
 *
 * <p>JEBOL had BYTES always take everything left, so {@code BYTES 2} read the
 * whole buffer and then tried to read the two as a code. The out-of-range
 * that followed stopped the block it stood in, and that block was the rest of
 * codecs-test.r3: one misread argument in front of 187 assertions.
 */
class BincodeByteRunsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Nested
    @DisplayName("BYTES with and without a count")
    class TheCount {

        @Test
        @DisplayName("a count takes that many, and no count takes the rest")
        void aCountTakesThatMany() {
            assertThat(answerTo("""
                    b: binary #{0102030405}
                    reduce [binary/read b [BYTES 2] binary/read b [BYTES]]"""))
                    .isEqualTo("[[#{0102}] [#{030405}]]");
        }

        @Test
        @DisplayName("a get-word carries the count, which is how a decoder writes it")
        void aGetWordCarriesTheCount() {
            assertThat(answerTo("""
                    b: binary #{0102030405}
                    size: 3
                    binary/read b [BYTES :size]""")).isEqualTo("[#{010203}]");
        }

        @Test
        @DisplayName("a count of nothing takes nothing")
        void aCountOfNothingTakesNothing() {
            assertThat(answerTo("""
                    b: binary #{0102030405}
                    binary/read b [BYTES 0]""")).isEqualTo("[#{}]");
        }

        @Test
        @DisplayName("and a count past the end is out of range")
        void aCountPastTheEnd() {
            assertThat(errorIdFrom("""
                    b: binary #{0102030405} binary/read b [BYTES 99]"""))
                    .isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("codes after the run carry on from where it stopped")
        void codesAfterTheRunCarryOn() {
            assertThat(answerTo("""
                    b: binary #{0102030405}
                    binary/read b [BYTES 2 UI8 BYTES 2]"""))
                    .isEqualTo("[#{0102} 3 #{0405}]");
        }
    }

    @Nested
    @DisplayName("STRING-BYTES and OCTAL-BYTES, which take the same run")
    class TheOtherTwo {

        @Test
        @DisplayName("a string stops at the first nought inside the run")
        void aStringStopsAtTheFirstNought() {
            assertThat(answerTo("""
                    b: binary #{616263000405}
                    binary/read b [STRING-BYTES 5]""")).isEqualTo("[\"abc\"]");
        }

        @Test
        @DisplayName("and takes the whole run when there is no nought in it")
        void aStringWithNoNought() {
            assertThat(answerTo("""
                    b: binary #{61626364}
                    binary/read b [STRING-BYTES 4]""")).isEqualTo("[\"abcd\"]");
        }

        @Test
        @DisplayName("the digits of an octal number are read in base eight")
        void octalDigitsAreBaseEight() {
            assertThat(answerTo("""
                    b: binary #{3132330005}
                    binary/read b [OCTAL-BYTES 3]""")).isEqualTo("[83]");
        }

        @Test
        @DisplayName("and both move the cursor by the whole run, nought or not")
        void bothMoveByTheWholeRun() {
            assertThat(answerTo("""
                    b: binary #{616263000405}
                    binary/read b [STRING-BYTES 4 UI8]""")).isEqualTo("[\"abc\" 4]");
        }
    }

    @Nested
    @DisplayName("a number where a code was expected")
    class ABareNumber {

        @Test
        @DisplayName("read as the whole code, it is a count of bytes")
        void asTheWholeCodeItIsACount() {
            assertThat(answerTo("""
                    b: binary #{01020304}
                    reduce [binary/read b 2 binary/read b 2]"""))
                    .isEqualTo("[#{0102} #{0304}]");
        }

        @Test
        @DisplayName("but inside a block it is a bad spec")
        void insideABlockItIsABadSpec() {
            assertThat(errorIdFrom("b: binary #{01020304} binary/read b [2]"))
                    .isEqualTo("invalid-spec");
            assertThat(errorIdFrom("b: binary #{01020304} binary/read b [UI8 2]"))
                    .isEqualTo("invalid-spec");
        }

        @Test
        @DisplayName("and reading a count into a block is refused, not ignored")
        void readingACountIntoABlockIsRefused() {
            assertThat(errorIdFrom("""
                    b: binary #{01020304} d: copy [] binary/read/into b 2 tail d"""))
                    .isEqualTo("feature-na");
        }
    }

    @Nested
    @DisplayName("/INTO, which puts what it read where it was told")
    class Into {

        @Test
        @DisplayName("the values land at the position given, in order")
        void theValuesLandAtThePosition() {
            assertThat(answerTo("""
                    b: binary #{01020304}
                    d: copy [x]
                    binary/read/into b [UI8 UI8] tail d
                    d""")).isEqualTo("[x 1 2]");
        }

        @Test
        @DisplayName("and the answer is the block standing after them")
        void theAnswerIsTheBlockAfterThem() {
            assertThat(answerTo("""
                    b: binary #{01020304}
                    d: copy []
                    binary/read/into b [UI8 UI8] tail d""")).isEqualTo("[]");
        }

        @Test
        @DisplayName("inserting in the middle keeps what was after it")
        void insertingInTheMiddle() {
            assertThat(answerTo("""
                    b: binary #{0102}
                    d: copy [x y]
                    binary/read/into b [UI8] next d
                    d""")).isEqualTo("[x 1 y]");
        }
    }

    @Nested
    @DisplayName("which error a bad dialect gives, and it depends on the side")
    class TheTwoErrors {

        @Test
        @DisplayName("a read names the value alone")
        void aReadNamesTheValue() {
            assertThat(errorIdFrom("b: binary #{0102} binary/read b [FOO]"))
                    .isEqualTo("invalid-spec");
            assertThat(errorIdFrom("b: binary #{0102} binary/read b [AT \"x\"]"))
                    .isEqualTo("invalid-spec");
        }

        @Test
        @DisplayName("a write names the dialect")
        void aWriteNamesTheDialect() {
            assertThat(errorIdFrom("b: binary #{} binary/write b [FOO 1]"))
                    .isEqualTo("dialect");
            assertThat(errorIdFrom("b: binary #{} binary/write b [UI8 \"x\"]"))
                    .isEqualTo("dialect");
        }

        @Test
        @DisplayName("and a read past the end names the code it stopped on")
        void aReadPastTheEndNamesTheCode() {
            assertThat(answerTo("""
                    b: binary #{01}
                    e: try [binary/read b [UI8 UI8]]
                    e/arg1""")).isEqualTo("UI8");
        }
    }
}
