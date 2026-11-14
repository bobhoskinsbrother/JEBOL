package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class BuffersTest {

    @Test
    @DisplayName("flipping a byte buffer answers the same buffer, ready to read what was put")
    void flipsBytes() {
        ByteBuffer written = ByteBuffer.allocate(8).put((byte) 1).put((byte) 2);

        ByteBuffer flipped = Buffers.flip(written);

        assertThat(flipped).isSameAs(written);
        assertThat(flipped.position()).isZero();
        assertThat(flipped.limit()).isEqualTo(2);
        assertThat(flipped.get()).isEqualTo((byte) 1);
    }

    @Test
    @DisplayName("flipping a character buffer answers the characters put, as text")
    void flipsCharacters() {
        CharBuffer written = CharBuffer.allocate(8).put("ab");

        assertThat(Buffers.flip(written).toString()).isEqualTo("ab");
    }
}
