package org.jebol.domain.eval;

import java.util.Arrays;

final class Octets {

    private byte[] held = new byte[64];
    private int used;

    void write(int octet) {
        if (used == held.length) {
            held = Arrays.copyOf(held, held.length * 2);
        }
        held[used++] = (byte) octet;
    }

    void write(byte[] more, int from, int count) {
        while (used + count > held.length) {
            held = Arrays.copyOf(held, held.length * 2);
        }
        System.arraycopy(more, from, held, used, count);
        used += count;
    }

    int length() {
        return used;
    }

    byte[] toArray() {
        return Arrays.copyOf(held, used);
    }
}
