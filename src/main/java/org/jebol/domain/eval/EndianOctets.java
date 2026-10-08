package org.jebol.domain.eval;

final class EndianOctets {

    int littleEndianWordAt(byte[] bytes, int at) {
        return bytes[at] & 0xFF
                | (bytes[at + 1] & 0xFF) << 8
                | (bytes[at + 2] & 0xFF) << 16
                | (bytes[at + 3] & 0xFF) << 24;
    }

    long littleEndianLongAt(byte[] bytes, int at) {
        return littleEndianWordAt(bytes, at) & 0xFFFFFFFFL
                | (long) littleEndianWordAt(bytes, at + 4) << 32;
    }

    byte[] bigEndian(long value, int width) {
        byte[] written = new byte[width];
        for (int at = 0; at < width; at++) {
            written[at] = (byte) (value >>> (width - 1 - at) * 8);
        }
        return written;
    }
}
