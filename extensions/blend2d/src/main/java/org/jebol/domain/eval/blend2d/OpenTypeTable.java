package org.jebol.domain.eval.blend2d;

record OpenTypeTable(byte[] data, int offset, int size) {

    static final OpenTypeTable EMPTY = new OpenTypeTable(new byte[0], 0, 0);

    boolean isEmpty() {
        return size == 0;
    }

    boolean fits(long length) {
        return size >= length;
    }

    int readU8(int at) {
        return data[offset + at] & 0xFF;
    }

    int readI8(int at) {
        return data[offset + at];
    }

    int readU16(int at) {
        return (data[offset + at] & 0xFF) << 8 | (data[offset + at + 1] & 0xFF);
    }

    int readI16(int at) {
        return (short) readU16(at);
    }

    int readU24(int at) {
        return readU8(at) << 16 | readU16(at + 1);
    }

    long readU32(int at) {
        return (long) readU16(at) << 16 | readU16(at + 2);
    }

    int readI32(int at) {
        return (int) readU32(at);
    }

    OpenTypeTable subTable(long at) {
        int clamped = (int) Math.min(at, size);
        return new OpenTypeTable(data, offset + clamped, size - clamped);
    }

    OpenTypeTable subTableUnchecked(int at) {
        return new OpenTypeTable(data, offset + at, size - at);
    }

    OpenTypeTable truncated(int length) {
        return new OpenTypeTable(data, offset, Math.min(length, size));
    }
}
