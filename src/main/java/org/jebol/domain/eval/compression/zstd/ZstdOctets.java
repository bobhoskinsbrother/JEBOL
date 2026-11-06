package org.jebol.domain.eval.compression.zstd;

final class ZstdOctets {

    private ZstdOctets() {
    }

    static int highBit(int value) {
        return 31 - Integer.numberOfLeadingZeros(value);
    }

    static int readShort(byte[] buffer, int at) {
        return (buffer[at] & 0xFF) | (buffer[at + 1] & 0xFF) << 8;
    }

    static int read24(byte[] buffer, int at) {
        return readShort(buffer, at) | (buffer[at + 2] & 0xFF) << 16;
    }

    static int readInt(byte[] buffer, int at) {
        return read24(buffer, at) | (buffer[at + 3] & 0xFF) << 24;
    }

    static long readLong(byte[] buffer, int at) {
        return (readInt(buffer, at) & 0xFFFFFFFFL) | (long) readInt(buffer, at + 4) << 32;
    }

    static void writeShort(byte[] buffer, int at, int value) {
        buffer[at] = (byte) value;
        buffer[at + 1] = (byte) (value >>> 8);
    }

    static void writeInt(byte[] buffer, int at, int value) {
        writeShort(buffer, at, value);
        writeShort(buffer, at + 2, value >>> 16);
    }

    static void writeLong(byte[] buffer, int at, long value) {
        writeInt(buffer, at, (int) value);
        writeInt(buffer, at + 4, (int) (value >>> 32));
    }
}
