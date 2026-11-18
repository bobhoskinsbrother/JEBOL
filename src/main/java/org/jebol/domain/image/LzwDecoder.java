package org.jebol.domain.image;

final class LzwDecoder {

    private static final int LARGEST_TABLE = 4096;
    private static final int WIDEST_CODE = 12;
    private static final int NO_CODE = -1;

    private final byte[] data;
    private final int minimumCodeSize;
    private final short[] prefix = new short[LARGEST_TABLE];
    private final byte[] suffix = new byte[LARGEST_TABLE];
    private final byte[] firstOf = new byte[LARGEST_TABLE];
    private final byte[] stack = new byte[LARGEST_TABLE + 1];
    private int bitPosition;

    LzwDecoder(byte[] data, int minimumCodeSize) {
        this.data = data;
        this.minimumCodeSize = Math.max(1, Math.min(minimumCodeSize, WIDEST_CODE - 1));
    }

    byte[] decode(int pixels) {
        byte[] out = new byte[pixels];
        int clear = 1 << minimumCodeSize;
        int end = clear + 1;
        for (int code = 0; code < clear; code++) {
            suffix[code] = (byte) code;
            firstOf[code] = (byte) code;
        }
        int width = minimumCodeSize + 1;
        int next = clear + 2;
        int previous = NO_CODE;
        int written = 0;
        while (written < pixels) {
            int code = readCode(width);
            if (code < 0 || code == end) {
                break;
            }
            if (code == clear) {
                width = minimumCodeSize + 1;
                next = clear + 2;
                previous = NO_CODE;
                continue;
            }
            if (previous == NO_CODE) {
                if (code >= clear) {
                    break;
                }
                out[written++] = suffix[code];
                previous = code;
                continue;
            }
            int expanding = code;
            int depth = 0;
            if (code >= next) {
                if (code > next) {
                    break;
                }
                stack[depth++] = firstOf[previous];
                expanding = previous;
            }
            while (expanding >= clear) {
                stack[depth++] = suffix[expanding];
                expanding = prefix[expanding];
            }
            stack[depth++] = suffix[expanding];
            byte first = suffix[expanding];
            if (next < LARGEST_TABLE) {
                prefix[next] = (short) previous;
                suffix[next] = first;
                firstOf[next] = firstOf[previous];
                next++;
                if (next == (1 << width) && width < WIDEST_CODE) {
                    width++;
                }
            }
            while (depth > 0 && written < pixels) {
                out[written++] = stack[--depth];
            }
            previous = code;
        }
        return out;
    }

    private int readCode(int width) {
        if (bitPosition + width > data.length * 8) {
            return -1;
        }
        int code = 0;
        for (int bit = 0; bit < width; bit++) {
            int position = bitPosition + bit;
            if ((data[position >> 3] & (1 << (position & 7))) != 0) {
                code |= 1 << bit;
            }
        }
        bitPosition += width;
        return code;
    }
}
