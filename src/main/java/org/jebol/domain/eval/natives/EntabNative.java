package org.jebol.domain.eval.natives;

import java.util.Arrays;

public class EntabNative extends TabbingNative {

    @Override
    public String nativeName() {
        return "entab";
    }

    @Override
    byte[] tab(byte[] bytes, int stop) {
        byte[] entabbed = new byte[bytes.length];
        int written = 0;
        int pendingSpaces = 0;
        int at = 0;
        while (at < bytes.length) {
            byte each = bytes[at];
            if (each == SPACE) {
                pendingSpaces++;
                if (pendingSpaces >= stop) {
                    entabbed[written++] = TAB;
                    pendingSpaces = 0;
                }
                at++;
            } else if (each == TAB) {
                entabbed[written++] = TAB;
                pendingSpaces = 0;
                at++;
            } else {
                written = writeSpaces(entabbed, written, pendingSpaces);
                pendingSpaces = 0;
                int lineEnd = findLineEnd(bytes, at);
                System.arraycopy(bytes, at, entabbed, written, lineEnd - at);
                written += lineEnd - at;
                at = lineEnd;
            }
        }
        written = writeSpaces(entabbed, written, pendingSpaces);
        return Arrays.copyOf(entabbed, written);
    }

    private static int findLineEnd(byte[] bytes, int from) {
        for (int at = from; at < bytes.length; at++) {
            if (bytes[at] == LINE_FEED) {
                return at + 1;
            }
        }
        return bytes.length;
    }

    private static int writeSpaces(byte[] entabbed, int written, int count) {
        Arrays.fill(entabbed, written, written + count, SPACE);
        return written + count;
    }
}
