package org.jebol.domain.eval.blend2d;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Blend2DFontTest {

    private static final int REPLACEMENT = 0xFFFD;

    @Test
    @DisplayName("a Latin-1 byte above 0x7F read as UTF-8 is one replacement character, and the bytes after it read on")
    void replacesALatinOneByte() {
        assertThat(Blend2DFont.decodeUtf8(new int[] {0x41, 0xE9, 0x42})).containsExactly(0x41, REPLACEMENT, 0x42);
    }

    @Test
    @DisplayName("two-, three- and four-byte sequences decode to their code points")
    void decodesEachLength() {
        assertThat(Blend2DFont.decodeUtf8(new int[] {0xC3, 0xA9, 0xE2, 0x82, 0xAC, 0xF0, 0x9F, 0x98, 0x80}))
                .containsExactly(0xE9, 0x20AC, 0x1F600);
    }

    @Test
    @DisplayName("an overlong form, a stray continuation byte and a sequence past U+10FFFF each step one byte")
    void stepsOneBytePastEachInvalidByte() {
        assertThat(Blend2DFont.decodeUtf8(new int[] {0xC0, 0x80})).containsExactly(REPLACEMENT, REPLACEMENT);
        assertThat(Blend2DFont.decodeUtf8(new int[] {0x80, 0x41})).containsExactly(REPLACEMENT, 0x41);
        assertThat(Blend2DFont.decodeUtf8(new int[] {0xE0, 0x80, 0x80}))
                .containsExactly(REPLACEMENT, REPLACEMENT, REPLACEMENT);
        assertThat(Blend2DFont.decodeUtf8(new int[] {0xF4, 0x90, 0x80, 0x80}))
                .containsExactly(REPLACEMENT, REPLACEMENT, REPLACEMENT, REPLACEMENT);
    }

    @Test
    @DisplayName("a sequence cut short at the end is a replacement for each byte left")
    void replacesATruncatedSequence() {
        assertThat(Blend2DFont.decodeUtf8(new int[] {0x41, 0xE2, 0x82})).containsExactly(0x41, REPLACEMENT, REPLACEMENT);
    }

    @Test
    @DisplayName("UTF-16 joins a surrogate pair and lets a lone surrogate through, as Blend2D's lenient reader does")
    void readsUtf16Leniently() {
        assertThat(Blend2DFont.decodeUtf16(new int[] {0xD83D, 0xDE00, 0x41})).containsExactly(0x1F600, 0x41);
        assertThat(Blend2DFont.decodeUtf16(new int[] {0xDC00, 0xD800})).containsExactly(0xDC00, 0xD800);
    }
}
