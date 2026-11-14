package org.jebol.domain.value;

import java.nio.Buffer;

public final class Buffers {

    private Buffers() {
    }

    public static <T extends Buffer> T flip(T buffer) {
        Buffer flipping = buffer;
        flipping.flip();
        return buffer;
    }
}
