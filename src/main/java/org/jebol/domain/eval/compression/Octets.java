package org.jebol.domain.eval.compression;

import java.util.Arrays;

public final class Octets {

    private byte[] held = new byte[64];
    private int used;

    public void write(int octet) {
        if (used == held.length) {
            held = Arrays.copyOf(held, held.length * 2);
        }
        held[used++] = (byte) octet;
    }

    public void write(byte[] more, int from, int count) {
        while (used + count > held.length) {
            held = Arrays.copyOf(held, held.length * 2);
        }
        System.arraycopy(more, from, held, used, count);
        used += count;
    }

    int length() {
        return used;
    }

    public byte[] toArray() {
        return Arrays.copyOf(held, used);
    }
}
