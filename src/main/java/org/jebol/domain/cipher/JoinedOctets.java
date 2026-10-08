package org.jebol.domain.cipher;

import java.util.Arrays;

public final class JoinedOctets {

    public byte[] of(byte[] first, byte[] second) {
        byte[] both = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }
}
