package org.jebol.domain.eval.brotli;

import java.util.Arrays;

final class BrotliBits {

    private byte[] data;
    private int position;

    BrotliBits(int room) {
        this.data = new byte[Math.max(room, 64)];
    }

    int at() {
        return position;
    }

    private void room(int howManyBits) {
        int wanted = ((position + howManyBits) >> 3) + 9;
        if (wanted > data.length) {
            data = Arrays.copyOf(data, Math.max(wanted, data.length * 2));
        }
    }

    void write(int howManyBits, long bits) {
        if (howManyBits == 0) {
            return;
        }
        room(howManyBits);
        int at = position >> 3;
        int reserved = position & 7;
        long shifted = bits << reserved;
        data[at] |= (byte) shifted;
        at++;
        for (int left = howManyBits + reserved; left >= 9; left -= 8) {
            shifted >>>= 8;
            data[at++] = (byte) shifted;
        }
        data[at] = 0;
        position += howManyBits;
    }

    void writeBytes(byte[] source, int at, int howMany) {
        room((howMany + 1) << 3);
        System.arraycopy(source, at, data, position >> 3, howMany);
        position += howMany << 3;
        data[position >> 3] = 0;
    }

    void jumpToByteBoundary() {
        room(8);
        position = (position + 7) & ~7;
        data[position >> 3] = 0;
    }

    void rewindTo(int newPosition) {
        data[newPosition >> 3] &= (byte) ((1 << (newPosition & 7)) - 1);
        position = newPosition;
    }

    void updateBits(int howManyBits, int bits, int at) {
        int left = howManyBits;
        int value = bits;
        int writingAt = at;
        while (left > 0) {
            int whichByte = writingAt >> 3;
            int unchanged = writingAt & 7;
            int changed = Math.min(left, 8 - unchanged);
            int total = unchanged + changed;
            int mask = -(1 << total) | ((1 << unchanged) - 1);
            int keep = data[whichByte] & mask;
            int fresh = value & ((1 << changed) - 1);
            data[whichByte] = (byte) ((fresh << unchanged) | keep);
            left -= changed;
            value >>>= changed;
            writingAt += changed;
        }
    }

    byte[] written() {
        return Arrays.copyOf(data, position >> 3);
    }
}
