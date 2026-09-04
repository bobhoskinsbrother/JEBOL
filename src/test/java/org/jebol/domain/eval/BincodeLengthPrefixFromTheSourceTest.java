package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LENGTH and LENGTH?, which are two codes and were one.
 *
 * <p>{@code SYM_LENGTH} reads a length written the way certificates write it:
 * a first byte up to and including 128 is the length itself, and anything
 * above has its low seven bits saying how many bytes carry the number, most
 * significant first. {@code SYM_LENGTHQ} consumes nothing and answers how
 * many bytes are left.
 *
 * <p>JEBOL had both spellings answering the second. So every DER structure --
 * every certificate, every key, every PKCS container -- came back with its
 * fields at the wrong offsets, and the tag after a long length was read out
 * of the middle of the length itself. {@code load %some.pfx} gave a tree with
 * plausible-looking words in it and nothing in the right place.
 */
class BincodeLengthPrefixFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a first byte up to 128 is the length, and costs one byte")
    void aShortLength() {
        assertThat(answerTo("""
                b: binary #{05AABBCCDDEE}
                binary/read b [LENGTH INDEX]""")).isEqualTo("[5 2]");
    }

    @Test
    @DisplayName("including 128 itself, which is the boundary the C draws")
    void theBoundaryIsInclusive() {
        assertThat(answerTo("""
                b: binary #{80AABB}
                binary/read b [LENGTH INDEX]""")).isEqualTo("[128 2]");
        assertThat(answerTo("""
                b: binary #{81 05 AA BB CC}
                binary/read b [LENGTH INDEX]""")).isEqualTo("[5 3]");
    }

    @Test
    @DisplayName("above it the low seven bits count the bytes that carry the number")
    void aLongLength() {
        assertThat(answerTo("""
                b: binary #{82091802010330}
                binary/read b [LENGTH INDEX]""")).isEqualTo("[2328 4]");
        assertThat(answerTo("""
                b: binary #{8300010000}
                binary/read b [LENGTH INDEX]""")).isEqualTo("[256 5]");
    }

    @Test
    @DisplayName("and the bytes are most significant first")
    void mostSignificantFirst() {
        assertThat(answerTo("""
                b: binary #{820100}
                binary/read b [LENGTH]""")).isEqualTo("[256]");
    }

    @Test
    @DisplayName("LENGTH? is the other question and moves nothing")
    void lengthQuestionMovesNothing() {
        assertThat(answerTo("""
                b: binary #{05AABBCCDDEE}
                binary/read b [LENGTH? INDEX LENGTH? INDEX]"""))
                .isEqualTo("[6 1 6 1]");
    }

    @Test
    @DisplayName("a length running past the end is out of range")
    void aLengthPastTheEnd() {
        assertThat(answerTo("""
                b: binary #{8409}
                e: try [binary/read b [LENGTH]]
                error? e""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the bit codes leave the cursor where LENGTH expects it")
    void afterTheBitCodes() {
        assertThat(answerTo("""
                b: binary #{308209180201}
                binary/read b [UB 2 BIT UB 5 LENGTH INDEX]"""))
                .isEqualTo("[0 #(true) 16 2328 5]");
    }

    @Test
    @DisplayName("and a certificate reads back with its fields where they belong")
    void aCertificateReadsBack() {
        assertThat(answerTo("""
                der: binary #{3009020103300406010502}
                binary/read der [UB 2 BIT UB 5 LENGTH UB 2 BIT UB 5 LENGTH BYTES 1]"""))
                .isEqualTo("[0 #(true) 16 9 0 #(false) 2 1 #{03}]");
    }
}
