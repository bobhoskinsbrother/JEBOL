package org.jebol.domain.image;

import org.jebol.domain.eval.compression.Octets;

import java.util.HashMap;
import java.util.Map;

final class LzwEncoder {

    private static final int LARGEST_TABLE = 4096;
    private static final int WIDEST_CODE = 12;

    private final Octets out = new Octets();
    private int pending;
    private int pendingBits;

    private LzwEncoder() {
    }

    static byte[] encode(byte[] indices, int minimumCodeSize) {
        return new LzwEncoder().compress(indices, minimumCodeSize);
    }

    private byte[] compress(byte[] indices, int minimumCodeSize) {
        int clear = 1 << minimumCodeSize;
        int end = clear + 1;
        Map<Integer, Integer> table = new HashMap<>();
        int width = minimumCodeSize + 1;
        int next = clear + 2;
        writeCode(clear, width);
        if (indices.length == 0) {
            writeCode(end, width);
            return finish();
        }
        int current = indices[0] & 0xFF;
        for (int at = 1; at < indices.length; at++) {
            int symbol = indices[at] & 0xFF;
            int key = (current << 8) | symbol;
            Integer found = table.get(key);
            if (found != null) {
                current = found;
                continue;
            }
            writeCode(current, width);
            if (next < LARGEST_TABLE) {
                table.put(key, next);
                if (next == (1 << width) && width < WIDEST_CODE) {
                    width++;
                }
                next++;
            } else {
                writeCode(clear, width);
                table.clear();
                width = minimumCodeSize + 1;
                next = clear + 2;
            }
            current = symbol;
        }
        writeCode(current, width);
        writeCode(end, width);
        return finish();
    }

    private void writeCode(int code, int width) {
        pending |= code << pendingBits;
        pendingBits += width;
        while (pendingBits >= 8) {
            out.write(pending & 0xFF);
            pending >>>= 8;
            pendingBits -= 8;
        }
    }

    private byte[] finish() {
        if (pendingBits > 0) {
            out.write(pending & 0xFF);
        }
        return out.toArray();
    }
}
