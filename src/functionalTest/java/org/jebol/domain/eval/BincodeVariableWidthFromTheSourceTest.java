package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    @DisplayName("a VINT at the widest each length holds")
    void aVintAtTheWidestEachLengthHolds() {
        assertThat(answerTo("""
                one: binary 16  binary/write one [VINT 127]
                two: binary 16  binary/write two [VINT 16383]
                reduce [enbase/flat one/buffer 16 enbase/flat two/buffer 16]"""))
                .isEqualTo("[\"FF\" \"7FFF\"]");
    }

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

    @Test
    @DisplayName("the wider one has no floor either")
    void theWiderOneTakesANegativeWhole() {
        assertThat(answerTo("""
                b: binary 16
                binary/write b [EncodedU64 -1]
                enbase/flat b/buffer 16""")).isEqualTo("\"FFFFFFFFFFFFFFFFFF01\"");
    }

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

    @Test
    @DisplayName("a skip of exactly one byte, and of one bit less")
    void aSkipOfExactlyOneByte() {
        assertThat(answerTo("binary/read #{FF} [SKIPBITS 8]")).isEqualTo("[]");
        assertThat(answerTo("""
                binary/read 2#{11000011} [SKIPBITS 7 UB 1]""")).isEqualTo("[1]");
    }

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
