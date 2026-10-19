package org.jebol.domain.eval.hashing;

final class EndianOctets {

    int readTheLittleEndianWordAt(byte[] bytes, int at) {
        return bytes[at] & 0xFF
                | (bytes[at + 1] & 0xFF) << 8
                | (bytes[at + 2] & 0xFF) << 16
                | (bytes[at + 3] & 0xFF) << 24;
    }

    long readTheLittleEndianLongAt(byte[] bytes, int at) {
        return readTheLittleEndianWordAt(bytes, at) & 0xFFFFFFFFL
                | (long) readTheLittleEndianWordAt(bytes, at + 4) << 32;
    }

    byte[] writeBigEndian(long value, int width) {
        byte[] written = new byte[width];
        for (int at = 0; at < width; at++) {
            written[at] = (byte) (value >>> (width - 1 - at) * 8);
        }
        return written;
    }
}
