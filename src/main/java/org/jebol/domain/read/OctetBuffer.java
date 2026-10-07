package org.jebol.domain.read;

import java.nio.charset.Charset;
import java.util.Arrays;

final class OctetBuffer {

    private static final int ROOM_TO_START_WITH = 64;

    private byte[] octets = new byte[ROOM_TO_START_WITH];

    private int count;

    void write(int octet) {
        roomFor(1);
        octets[count++] = (byte) octet;
    }

    void write(byte[] from, int start, int length) {
        roomFor(length);
        System.arraycopy(from, start, octets, count, length);
        count += length;
    }

    void reset() {
        count = 0;
    }

    byte[] toByteArray() {
        return Arrays.copyOf(octets, count);
    }

    String toString(Charset charset) {
        return new String(octets, 0, count, charset);
    }

    private void roomFor(int more) {
        if (count + more > octets.length) {
            octets = Arrays.copyOf(octets, Math.max(octets.length * 2, count + more));
        }
    }
}
