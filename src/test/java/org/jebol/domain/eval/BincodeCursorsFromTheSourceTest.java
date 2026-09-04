package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The binary dialect's context, which is one series read and written through
 * two cursors.
 *
 * <p>{@code u-bincode.c}. BINARY answers a context and every refinement
 * changes the one it was handed, so a protocol writes a header, works out a
 * length, writes that, and reads the reply, all through the same context.
 * Answering a fresh one each time meant every step after the first was written
 * into something nobody was holding.
 *
 * <p>{@code buffer} is where the next read starts and {@code buffer-write} is
 * where the next write lands. They move independently, which is what lets a
 * context be filled and then read from the beginning.
 */
class BincodeCursorsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("raised: try [" + source + "] raised/id");
    }

    @Test
    @DisplayName("what is written stays in the context that was written to")
    void writingLandsInTheContextGiven() {
        assertThat(answerTo("""
                b: binary 64
                binary/write b [ui64 1 ui32 1 ui24 1 ui16 1 ui8 1]
                b/buffer""")).isEqualTo("#{000000000000000100000001000001000101}");
    }

    @Test
    @DisplayName("a write moves the write cursor and leaves the read cursor alone")
    void aWriteMovesOnlyTheWriteCursor() {
        assertThat(answerTo("""
                b: binary 64
                binary/write b [ui64 1 ui32 1 ui24 1 ui16 1 ui8 1]
                reduce [index? b/buffer index? b/buffer-write]"""))
                .isEqualTo("[1 19]");
    }

    @Test
    @DisplayName("so what was written can be read straight back from the head")
    void whatWasWrittenReadsBack() {
        assertThat(answerTo("""
                b: binary 64
                binary/write b [ui64 1 ui32 1 ui24 1 ui16 1 ui8 1]
                rejoin binary/read b [ui64 ui32 ui24 ui16 ui8]"""))
                .isEqualTo("\"11111\"");
    }

    @Test
    @DisplayName("each read carries on from where the last one stopped")
    void readsCarryOn() {
        assertThat(answerTo("""
                c: binary #{01020304}
                reduce [binary/read c 'ui8 binary/read c 'ui8 index? c/buffer]"""))
                .isEqualTo("[1 2 3]");
    }

    @Test
    @DisplayName("a write pokes at its cursor rather than appending")
    void aWritePokes() {
        assertThat(answerTo("""
                d: binary #{0102}
                binary/write d [ui8 9]
                reduce [head d/buffer index? d/buffer-write]"""))
                .isEqualTo("[#{0902} 2]");
    }

    @Test
    @DisplayName("INIT empties the context and puts both cursors back")
    void initEmptiesTheContext() {
        assertThat(answerTo("""
                i: binary #{0102}
                binary/init i none
                reduce [head i/buffer index? i/buffer]""")).isEqualTo("[#{} 1]");
    }

    @Test
    @DisplayName("a binary laid in on its own is its own bytes")
    void aBareBinaryIsItsOwnBytes() {
        assertThat(answerTo("""
                g: binary 16
                binary/write g [ui8 1 #{FFFF} ui8 2]
                head g/buffer""")).isEqualTo("#{01FFFF02}");
    }

    @Test
    @DisplayName("and so are a string, a file and an email")
    void bareStringsAreTheirOwnBytes() {
        assertThat(answerTo("""
                h: binary 16
                binary/write h ["abc" %def a@b]
                head h/buffer""")).isEqualTo("#{616263646566614062}");
    }

    @Test
    @DisplayName("a tag is not, although it is a string")
    void aTagIsRefused() {
        assertThat(errorIdFrom("""
                binary/write binary 16 [<x>]""")).isEqualTo("dialect");
    }

    @Test
    @DisplayName("UI8BYTES writes the length in front of the bytes")
    void ui8BytesWritesItsLength() {
        assertThat(answerTo("""
                e: binary 16
                binary/write e [UI8BYTES #{cafe}]
                head e/buffer""")).isEqualTo("#{02CAFE}");
    }

    @Test
    @DisplayName("and the little-endian form writes the length the other way round")
    void ui16LeBytesWritesItsLength() {
        assertThat(answerTo("""
                f: binary 16
                binary/write f [UI16LEBYTES #{cafe}]
                head f/buffer""")).isEqualTo("#{0200CAFE}");
    }

    @Test
    @DisplayName("every length-prefixed form reads its bytes back")
    void everyLengthPrefixedFormReadsBack() {
        assertThat(answerTo("""
                reduce [
                    binary/read #{02CAFE} 'UI8BYTES
                    binary/read #{0002CAFE} 'UI16BYTES
                    binary/read #{0200CAFE} 'UI16LEBYTES
                    binary/read #{000002CAFE} 'UI24BYTES
                    binary/read #{020000CAFE} 'UI24LEBYTES
                    binary/read #{00000002CAFE} 'UI32BYTES
                    binary/read #{02000000CAFE} 'UI32LEBYTES
                ]""")).isEqualTo(
                "[#{CAFE} #{CAFE} #{CAFE} #{CAFE} #{CAFE} #{CAFE} #{CAFE}]");
    }

    @Test
    @DisplayName("BYTES reads what is left, and consumes it")
    void bytesReadsTheRestAndConsumesIt() {
        assertThat(answerTo("""
                b: binary 32
                binary/write b [#{cafe}]
                reduce [binary/read b 'bytes binary/read b 'bytes]"""))
                .isEqualTo("[#{CAFE} #{}]");
    }

    @Test
    @DisplayName("a signed field refuses a number outside its symmetric range")
    void signedFieldsAreSymmetric() {
        assertThat(answerTo("""
                collect [
                    foreach spec [
                        [SI8 127] [SI8 -127] [SI8 128] [SI8 -128]
                        [SI16 32767] [SI16 -32767] [SI16 32768] [SI16 -32768]
                    ][
                        keep error? try [binary/write binary 16 spec]
                    ]
                ]""")).isEqualTo(
                "[#(false) #(false) #(true) #(true)"
                        + " #(false) #(false) #(true) #(true)]");
    }

    @Test
    @DisplayName("an unsigned field has a ceiling but takes a negative")
    void unsignedFieldsTakeNegatives() {
        assertThat(answerTo("""
                b: binary 16
                binary/write b [UI8 -1 UI16 -1 UI24 -1 UI32 -1 UI64 -1]
                head b/buffer""")).isEqualTo("#{FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF}");
    }

    @Test
    @DisplayName("and refuses one over its ceiling")
    void unsignedFieldsHaveACeiling() {
        assertThat(errorIdFrom("""
                binary/write binary 16 [UI8 256]""")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("AT counts from one and will not go before the head")
    void atCountsFromOne() {
        assertThat(errorIdFrom("""
                binary/write binary 8 [AT 0 UI8 1]""")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("SKIP reads but does not write")
    void skipIsReadOnly() {
        assertThat(answerTo("""
                refused: try [binary/write binary 8 [SKIP 1 UI8 9]]
                reduce [binary/read #{01020304} [SKIP 1 UI8] refused/id]"""))
                .isEqualTo("[[2] dialect]");
    }

    @Test
    @DisplayName("writing into a protected binary is refused")
    void aProtectedBinaryIsRefused() {
        assertThat(errorIdFrom("""
                out: protect #{0000} binary/write out #{babe}""")).isEqualTo("protected");
    }

    @Test
    @DisplayName("a set-word takes the next value the read produces")
    void aSetWordTakesTheNextValue() {
        assertThat(answerTo("""
                b: binary #{01020304}
                i: 0
                reduce [binary/read b [AT 1 i: UI8] i]""")).isEqualTo("[[1] 1]");
    }

    @Test
    @DisplayName("and skips a code that produces nothing to take")
    void aSetWordSkipsAPositionCode() {
        assertThat(answerTo("""
                b: binary #{01020304}
                x: 0
                reduce [binary/read b [x: AT 1 UI8] x]""")).isEqualTo("[[1] 1]");
    }

    @Test
    @DisplayName("a set-word with nothing produced after it leaves its word alone")
    void aTrailingSetWordChangesNothing() {
        assertThat(answerTo("""
                b: binary #{01020304}
                z: 'untouched
                reduce [binary/read b [AT 1 UI8 z:] z]"""))
                .isEqualTo("[[1] untouched]");
    }

    @Test
    @DisplayName("it works for a run of bytes as well as a number")
    void aSetWordTakesBytesToo() {
        assertThat(answerTo("""
                b: binary #{01020304}
                w: 0
                reduce [binary/read b [AT 1 w: BYTES] w]"""))
                .isEqualTo("[[#{01020304}] #{01020304}]");
    }

    @Test
    @DisplayName("reading past the end is out of range, not a short number")
    void readingPastTheEndRaises() {
        assertThat(errorIdFrom("""
                binary/read #{01020304} [AT 5 UI8]""")).isEqualTo("out-of-range");
        assertThat(errorIdFrom("""
                binary/read #{01} [UI16]""")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("LENGTH? answers the bytes still to come")
    void lengthAnswersWhatIsLeft() {
        assertThat(answerTo("""
                reduce [
                    binary/read #{01020304} [LENGTH?]
                    binary/read #{01020304} [UI16 LENGTH?]
                ]""")).isEqualTo("[[4] [258 2]]");
    }

    @Test
    @DisplayName("PAD aligns up to a multiple, and stays put when already there")
    void padAlignsUpToAMultiple() {
        assertThat(answerTo("""
                collect [
                    foreach spec [
                        [UI8 1 PAD 4]
                        [UI8 1 UI8 2 UI8 3 UI8 4 PAD 4]
                        [UI8 1 UI8 2 UI8 3 UI8 4 UI8 5 PAD 4]
                        [PAD 4]
                    ][
                        b: binary 32
                        binary/write b spec
                        keep head b/buffer
                    ]
                ]""")).isEqualTo(
                "[#{01000000} #{01020304} #{0102030405000000} #{}]");
    }

    @Test
    @DisplayName("PAD reads as well as writes, moving without laying anything down")
    void padReadsToo() {
        assertThat(answerTo("""
                binary/read #{FF000000FF} [UI8 PAD 4 UI8]""")).isEqualTo("[255 255]");
    }

    @Test
    @DisplayName("a binary handed straight to WRITE is written into")
    void aBareBinaryIsWrittenInto() {
        assertThat(answerTo("""
                c: #{}
                binary/write c [UI8 255 PAD 4 UI8 255]
                c""")).isEqualTo("#{FF000000FF}");
    }
}
