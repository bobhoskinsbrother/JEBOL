package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two ways the binary dialect writes a number in as few bytes as it needs.
 *
 * <p>{@code u-bincode.c}. ENCODEDU32 and ENCODEDU64 are seven bits a byte,
 * least significant first, with the top bit set on every byte but the last --
 * {@code cp[n] = u & 0x7F; u >>= 7; if (u) cp[n] |= 0x80;}. VINT counts the
 * leading noughts of its first byte to say how many follow, most significant
 * first, and is what EBML and Matroska carry.
 *
 * <p>They are not interchangeable and the difference shows on the smallest
 * numbers: a hundred and twenty-eight is two bytes either way, {@code 80 01}
 * in the first and {@code 40 80} in the second. The narrower of the first two
 * refuses a number past thirty-two bits, which is the only thing separating
 * it from the wider one.
 *
 * <p>SKIPBITS is here because it belongs to the same family of things a
 * reader needs when a format does not align to bytes: it steps the position by
 * a count of bits and produces nothing.
 *
 * <p>Every byte string here was read off a real 3.22.5 before it was written.
 */
class BincodeVariableWidthFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static String errorArgumentFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/arg1] ['no-error]");
    }

    @Test
    @DisplayName("seven bits a byte, least significant first")
    void sevenBitsAByteLeastSignificantFirst() {
        assertThat(answerTo("""
                b: binary/init none 16
                binary/write b [
                    EncodedU32 0 EncodedU32 1 EncodedU32 128
                    EncodedU32 129 EncodedU32 130 EncodedU32 2214768806
                ]
                enbase/flat b/buffer 16"""))
                .isEqualTo("\"0001800181018201A6E18AA008\"");
    }

    @Test
    @DisplayName("and they read back as the numbers they were")
    void theyReadBackAsThemselves() {
        assertThat(answerTo("""
                b: binary/init none 16
                binary/write b [
                    EncodedU32 0 EncodedU32 1 EncodedU32 128
                    EncodedU32 129 EncodedU32 130 EncodedU32 2214768806
                ]
                binary/read b [
                    EncodedU32 EncodedU32 EncodedU32
                    EncodedU32 EncodedU32 EncodedU32
                ]""")).isEqualTo("[0 1 128 129 130 2214768806]");
    }

    @Test
    @DisplayName("the widest number thirty-two bits hold, and one past it")
    void theWidestItTakesAndOnePastIt() {
        assertThat(answerTo("""
                b: binary 16
                binary/write b [EncodedU32 4294967295]
                enbase/flat b/buffer 16""")).isEqualTo("\"FFFFFFFF0F\"");
        assertThat(errorIdFrom("""
                b: binary 16
                binary/write b [EncodedU32 4294967296]""")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("the wider one takes the numbers the narrower will not")
    void theWiderOneTakesMore() {
        assertThat(answerTo("""
                b: binary/init none 16
                binary/write b [
                    EncodedU64 0
                    EncodedU64 0#0102030405
                    EncodedU64 0#7FFFFFFFFFFF
                    EncodedU64 0#7FFFFFFFFFFFFF
                    EncodedU64 0#7FFFFFFFFFFFFFFF
                ]
                enbase/flat b/buffer 16"""))
                .isEqualTo("{0085888C9010FFFFFFFFFFFF1FFFFFFFFFFFFFFF3FFFFFFFFFFFFFFFFF7F}");
    }

    @Test
    @DisplayName("VINT counts its leading noughts, most significant first")
    void vintCountsItsLeadingNoughts() {
        assertThat(answerTo("""
                b: binary/init none 16
                binary/write b [VINT 0 VINT 1 VINT 128 VINT 129 VINT 130 VINT 2214768806]
                enbase/flat b/buffer 16"""))
                .isEqualTo("\"8081408040814082088402B0A6\"");
    }

    @Test
    @DisplayName("and VINT reads back as the numbers it was")
    void vintReadsBackAsItself() {
        assertThat(answerTo("""
                b: binary/init none 16
                binary/write b [VINT 0 VINT 1 VINT 128 VINT 129 VINT 130 VINT 2214768806]
                binary/read b [VINT VINT VINT VINT VINT VINT]"""))
                .isEqualTo("[0 1 128 129 130 2214768806]");
    }

    /**
     * Either side of where a VINT needs a second byte. Seven bits hold up to a
     * hundred and twenty-seven, and fourteen up to sixteen thousand three
     * hundred and eighty-three.
     */
    @Test
    @DisplayName("a VINT at the widest each length holds")
    void aVintAtTheWidestEachLengthHolds() {
        assertThat(answerTo("""
                one: binary 16  binary/write one [VINT 127]
                two: binary 16  binary/write two [VINT 16383]
                reduce [enbase/flat one/buffer 16 enbase/flat two/buffer 16]"""))
                .isEqualTo("[\"FF\" \"7FFF\"]");
    }

    /**
     * The narrower code's range is symmetric about nought rather than being a
     * 32-bit word's: {@code (i64)VAL_UNT64(v) > 0xFFFFFFFF ||
     * (i64)VAL_UNT64(v) < (i64)0xFFFFFFFF00000001}, whose second half is
     * -4294967295. A number inside it is then written as
     * {@code (u64)VAL_UNT32(next)}, which is its lowest thirty-two bits, so -1
     * is the largest number the code can carry and -4294967295 is one.
     */
    @Test
    @DisplayName("the narrower one takes a negative as its lowest thirty-two bits")
    void theNarrowerOneTakesANegativeAsItsUnsignedSelf() {
        assertThat(answerTo("""
                b: binary 16
                binary/write b [EncodedU32 -1]
                binary/read b 'EncodedU32""")).isEqualTo("4294967295");
        assertThat(answerTo("""
                b: binary 16
                binary/write b [EncodedU32 -4294967295]
                binary/read b 'EncodedU32""")).isEqualTo("1");
        assertThat(errorIdFrom("""
                b: binary 16
                binary/write b [EncodedU32 -4294967296]""")).isEqualTo("out-of-range");
    }

    /** The wider code has no range check at all, so every bit of -1 is written. */
    @Test
    @DisplayName("the wider one has no floor either")
    void theWiderOneTakesANegativeWhole() {
        assertThat(answerTo("""
                b: binary 16
                binary/write b [EncodedU64 -1]
                enbase/flat b/buffer 16""")).isEqualTo("\"FFFFFFFFFFFFFFFFFF01\"");
    }

    /**
     * Eight bytes is as far as a VINT reaches, because the marker is a bit
     * within the first byte and the eighth spelling uses the last one it has.
     * So fifty-six bits is the widest number with a form, and the widest one
     * writes a lone {@code 01} marker byte followed by every bit set.
     */
    @Test
    @DisplayName("the widest number a VINT can carry, and one step inside it")
    void theWidestAVintCanCarry() {
        assertThat(answerTo("""
                b: binary 16
                binary/write b [VINT 72057594037927935]
                enbase/flat b/buffer 16""")).isEqualTo("\"01FFFFFFFFFFFFFF\"");
        assertThat(answerTo("""
                b: binary 16
                binary/write b [VINT 72057594037927935]
                binary/read b 'VINT""")).isEqualTo("72057594037927935");
    }

    @Test
    @DisplayName("and one step past where two bytes stop being enough")
    void aVintJustPastWhereTwoBytesStop() {
        assertThat(answerTo("""
                b: binary 16
                binary/write b [VINT 16384]
                enbase/flat b/buffer 16""")).isEqualTo("\"204000\"");
    }

    /**
     * A negative number has no VINT form and JEBOL says so, where the C does
     * not: {@code while (value >= (1ULL << (7 * count))) count++} shifts past
     * sixty-four once the count reaches ten, which is undefined, and in
     * practice never stops. Refusing is the only answer that terminates.
     */
    @Test
    @DisplayName("a VINT refuses a number it has no form for")
    void aVintRefusesANumberItCannotCarry() {
        assertThat(errorIdFrom("""
                b: binary 16
                binary/write b [VINT -1]""")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("both refuse a fraction where a whole number belongs")
    void bothRefuseAFraction() {
        assertThat(errorIdFrom("""
                b: binary 16
                binary/write b [EncodedU32 1.5]""")).isEqualTo("dialect");
        assertThat(errorIdFrom("""
                b: binary 16
                binary/write b [VINT 1.5]""")).isEqualTo("dialect");
    }

    @Test
    @DisplayName("reading either from nothing, or from a stream that stops mid-number")
    void readingPastTheEnd() {
        assertThat(errorIdFrom("binary/read #{} [EncodedU32]")).isEqualTo("out-of-range");
        assertThat(errorIdFrom("binary/read #{80} [EncodedU32]")).isEqualTo("out-of-range");
        assertThat(errorIdFrom("binary/read #{} [VINT]")).isEqualTo("out-of-range");
        assertThat(errorIdFrom("binary/read #{40} [VINT]")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("SKIPBITS steps by bits and produces nothing")
    void skipBitsStepsByBits() {
        assertThat(answerTo("""
                binary/read 2#{00000000 11000011} [SKIPBITS 10 UB 2 UB 2]"""))
                .isEqualTo("[0 0]");
    }

    @Test
    @DisplayName("and skipping none at all leaves the position where it was")
    void skippingNoneLeavesThePosition() {
        assertThat(answerTo("""
                binary/read 2#{11000011} [SKIPBITS 0 UB 2]""")).isEqualTo("[3]");
    }

    /**
     * Either side of the whole byte, since the C handles whole bytes and left
     * over bits by different routes: {@code if (i >= 8) { i /= 8; cp += i; ... }}
     * and then a loop over what is left.
     */
    @Test
    @DisplayName("a skip of exactly one byte, and of one bit less")
    void aSkipOfExactlyOneByte() {
        assertThat(answerTo("binary/read #{FF} [SKIPBITS 8]")).isEqualTo("[]");
        assertThat(answerTo("""
                binary/read 2#{11000011} [SKIPBITS 7 UB 1]""")).isEqualTo("[1]");
    }

    /**
     * The count is read as an unsigned quantity, so a negative one is not a
     * step backwards -- it is a step of four thousand million bytes, and it
     * runs off the end. The error names the count that was given.
     */
    @Test
    @DisplayName("a negative skip runs off the end rather than backwards")
    void aNegativeSkipRunsOffTheEnd() {
        assertThat(errorIdFrom("binary/read #{FF} [SKIPBITS -1]"))
                .isEqualTo("out-of-range");
        assertThat(errorArgumentFrom("binary/read #{FF} [SKIPBITS -1]"))
                .isEqualTo("-1");
        assertThat(errorIdFrom("binary/read #{FF} [SKIPBITS -8]"))
                .isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("a skip past the end names the count, not the code")
    void aSkipPastTheEndNamesTheCount() {
        assertThat(errorIdFrom("binary/read #{FF} [SKIPBITS 16]"))
                .isEqualTo("out-of-range");
        assertThat(errorArgumentFrom("binary/read #{FF} [SKIPBITS 16]"))
                .isEqualTo("16");
    }

    @Test
    @DisplayName("and a skip needs a whole number to count by")
    void aSkipNeedsAWholeNumber() {
        assertThat(errorIdFrom("""
                binary/read #{FF} [SKIPBITS {x}]""")).isEqualTo("invalid-spec");
    }
}
