package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The binary dialect's codes for things that are not whole numbers: floats,
 * runs of bits, the clock MS-DOS packed into sixteen of them, and CROP.
 *
 * <p>{@code u-bincode.c}. Between them they are what lets a format like SWF or
 * ZIP be read at all -- a scale stored as a fixed-point run of nineteen bits,
 * a timestamp stored as five bits of hour and a seconds field that only counts
 * in twos.
 */
class BincodeWiderCodesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the float codes default to little-endian, unlike every integer one")
    void floatsDefaultToLittleEndian() {
        assertThat(answerTo("""
                d: binary/write #{} [float16 0.5 float 0.5 double 0.5]
                d/buffer""")).isEqualTo("#{00380000003F000000000000E03F}");
    }

    @Test
    @DisplayName("and the BE forms write the same numbers the other way round")
    void bigEndianFloatsReverseThem() {
        assertThat(answerTo("""
                e: binary/write #{} [f16be 0.5 f32be 0.5 f64be 0.5]
                e/buffer""")).isEqualTo("#{38003F0000003FE0000000000000}");
    }

    @Test
    @DisplayName("every float code reads back what it wrote")
    void everyFloatCodeRoundTrips() {
        assertThat(answerTo("""
                collect [
                    foreach code [
                        float16 f16 f16be f16le
                        float f32 f32be f32le
                        double f64 f64be f64le
                    ][
                        b: binary 16
                        binary/write b reduce [code 32.5]
                        keep 32.5 = binary/read b code
                    ]
                ]""")).isEqualTo(
                "[#(true) #(true) #(true) #(true) #(true) #(true)"
                        + " #(true) #(true) #(true) #(true) #(true) #(true)]");
    }

    @Test
    @DisplayName("SB, UB and a run across bytes, then ALIGN back to a byte")
    void theBitCodesReadAcrossBytes() {
        assertThat(answerTo("""
                binary/read 2#{01011011 10110011 11111111}
                    [SB 3 SB 3 UB 2 SB 4 ALIGN UI8]""")).isEqualTo("[2 -2 3 -5 255]");
    }

    @Test
    @DisplayName("a signed run takes its sign from the top of the run, not the byte")
    void aSignedRunIsAsWideAsItSays() {
        assertThat(answerTo("""
                binary/read 2#{1110 0110} [SB 4 SB 4]""")).isEqualTo("[-2 6]");
    }

    @Test
    @DisplayName("FB is the same bits over sixty-five thousand five hundred and thirty-six")
    void fixedPointDividesByTheUnit() {
        assertThat(answerTo("""
                binary/read #{500000} [FB 19]""")).isEqualTo("[2.5]");
    }

    @Test
    @DisplayName("BIT answers one bit, and NOT-BIT the other way about")
    void bitAnswersOneBit() {
        assertThat(answerTo("""
                binary/read 2#{11110000} [BIT NOT-BIT BIT NOT-BIT]"""))
                .isEqualTo("[#(true) #(false) #(true) #(false)]");
    }

    @Test
    @DisplayName("the bit position survives between calls, so reads carry on mid-byte")
    void theBitPositionSurvivesBetweenCalls() {
        assertThat(answerTo("""
                bs: binary #{438E9438}
                reduce [
                    binary/read/with bs 'SB 12
                    binary/read bs 'BIT
                    binary/read bs 'BIT
                    binary/read/with bs 'UB 4
                ]""")).isEqualTo("[1080 #(true) #(true) 10]");
    }

    @Test
    @DisplayName("/WITH gives a lone code its count, and answers one value not a block")
    void withGivesALoneCodeItsCount() {
        assertThat(answerTo("""
                binary/read/with 2#{1110 0000} 'UB 4""")).isEqualTo("14");
    }

    @Test
    @DisplayName("ALIGN on a byte boundary does nothing rather than skipping one")
    void alignOnABoundaryDoesNothing() {
        assertThat(answerTo("""
                binary/read #{0102} [UI8 ALIGN ALIGN UI8]""")).isEqualTo("[1 2]");
    }

    @Test
    @DisplayName("MSDOS-DATETIME is the clock and then the date, sixteen bits each")
    void msdosDateTimeReadsBothHalves() {
        assertThat(answerTo("""
                binary/read #{BC96844C} 'MSDOS-DATETIME"""))
                .isEqualTo("4-Apr-2018/18:53:56");
    }

    @Test
    @DisplayName("and the two halves can be read apart")
    void theTwoHalvesReadApart() {
        assertThat(answerTo("""
                binary/read #{BC96844C} [MSDOS-TIME MSDOS-DATE]"""))
                .isEqualTo("[18:53:56 4-Apr-2018]");
    }

    @Test
    @DisplayName("the seconds only count in twos, so an odd one is lost")
    void theSecondsHaveTwoSecondResolution() {
        assertThat(answerTo("""
                b: binary 64
                binary/write b [msdos-time 21:23:55]
                binary/read b 'MSDOS-TIME""")).isEqualTo("21:23:54");
    }

    @Test
    @DisplayName("a date with no clock writes a clock of nothing")
    void aDateWithNoClockWritesZero() {
        assertThat(answerTo("""
                c: binary 64
                binary/write c [msdos-time 14-Mar-2019]
                head c/buffer""")).isEqualTo("#{0000}");
    }

    @Test
    @DisplayName("CROP drops what has been read and moves both cursors back")
    void cropDropsWhatHasBeenRead() {
        assertThat(answerTo("""
                cr: binary #{010203}
                taken: binary/read cr [UI8 CROP]
                reduce [taken cr/buffer cr/buffer-write]"""))
                .isEqualTo("[[1] #{0203} #{0203}]");
    }

    @Test
    @DisplayName("and a write after it lands where the write cursor now points")
    void aWriteAfterCropLandsRight() {
        assertThat(answerTo("""
                cr: binary #{010203}
                binary/read cr [UI8 CROP]
                binary/write cr [UI8 4]
                reduce [cr/buffer cr/buffer-write]"""))
                .isEqualTo("[#{0403} #{03}]");
    }

    @Test
    @DisplayName("UNIXTIME-NOW writes four bytes, and the LE form four more")
    void unixtimeNowWritesFourBytes() {
        assertThat(answerTo("""
                d: binary 4
                binary/write d [UNIXTIME-NOW UNIXTIME-NOW-LE]
                length? head d/buffer""")).isEqualTo("8");
    }

    @Test
    @DisplayName("and the two orders are the same moment written both ways")
    void theTwoOrdersAgree() {
        assertThat(answerTo("""
                d: binary 4
                binary/write d [UNIXTIME-NOW UNIXTIME-NOW-LE]
                both: binary/read d [UI32 UI32LE]
                (first both) = (second both)""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a bare number reads that many bytes")
    void aBareNumberReadsThatManyBytes() {
        assertThat(answerTo("""
                b: binary #{01020304}
                reduce [binary/read b 2 binary/read b 2]"""))
                .isEqualTo("[#{0102} #{0304}]");
    }

    @Test
    @DisplayName("and reading more than there is left is out of range")
    void readingMoreThanThereIsRaises() {
        assertThat(answerTo("""
                b: binary #{01020304}
                binary/read b 2
                binary/read b 2
                error? try [binary/read b 2]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a binary in a read dialect is a test, and only a match advances")
    void aBinaryInAReadDialectIsATest() {
        assertThat(answerTo("""
                m: binary #{0badCafe}
                binary/read m [ATz 0 #{0bad} #{F00D} #{Cafe}]"""))
                .isEqualTo("[#(true) #(false) #(true)]");
    }

    @Test
    @DisplayName("TUPLE3 and TUPLE4 read a colour straight out")
    void tupleCodesReadAColour() {
        assertThat(answerTo("""
                binary/read #{01020304050607} [TUPLE3 TUPLE4]"""))
                .isEqualTo("[1.2.3 4.5.6.7]");
    }

    @Test
    @DisplayName("FIXED8 and FIXED16 put the point where the format does")
    void fixedCodesPlaceThePoint() {
        assertThat(answerTo("""
                binary/read #{800700800700} [FIXED8 FIXED16]""")).isEqualTo("[7.5 7.5]");
    }

    @Test
    @DisplayName("STRING takes the text up to its nought, and the nought with it")
    void stringStopsAtItsNought() {
        assertThat(answerTo("""
                s: binary #{74657374002A}
                binary/read s [STRING UI8]""")).isEqualTo("[\"test\" 42]");
    }

    @Test
    @DisplayName("the BITSET codes read a run of flags as a set")
    void bitsetCodesReadFlags() {
        assertThat(answerTo("""
                binary/read #{81800180000001} [BITSET8 BITSET16 BITSET32]"""))
                .isEqualTo("[#(bitset! #{81}) #(bitset! #{8001})"
                        + " #(bitset! #{80000001})]");
    }
}
