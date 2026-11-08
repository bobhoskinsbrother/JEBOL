package org.jebol.adapter.cli;

final class Utf8Lengths {

    private static final int CONTINUATION_MASK = 0xC0;
    private static final int CONTINUATION = 0x80;

    private Utf8Lengths() {
    }

    static int encodedLength(int lead) {
        if (lead < 0xC0) {
            return 1;
        }
        if (lead < 0xE0) {
            return 2;
        }
        if (lead < 0xF0) {
            return 3;
        }
        if (lead < 0xF8) {
            return 4;
        }
        return lead < 0xFC ? 5 : 6;
    }

    static int decodedLength(byte[] bytes, int at, int limit) {
        if (at >= limit) {
            return -1;
        }
        int lead = bytes[at] & 0xFF;
        if (lead == 0) {
            return 0;
        }
        int length = validLengthFor(lead);
        if (length < 0 || at + length > limit) {
            return -1;
        }
        for (int each = 1; each < length; each++) {
            if ((bytes[at + each] & CONTINUATION_MASK) != CONTINUATION) {
                return -1;
            }
        }
        return length;
    }

    static int decode(byte[] bytes, int at, int length) {
        int lead = bytes[at] & 0xFF;
        if (length == 1) {
            return lead;
        }
        int codepoint = lead & (0x7F >> length);
        for (int each = 1; each < length; each++) {
            codepoint = (codepoint << 6) | (bytes[at + each] & 0x3F);
        }
        return codepoint;
    }

    private static int validLengthFor(int lead) {
        if (lead < 0x80) {
            return 1;
        }
        if (lead < 0xC2) {
            return -1;
        }
        if (lead < 0xE0) {
            return 2;
        }
        if (lead < 0xF0) {
            return 3;
        }
        return lead < 0xF5 ? 4 : -1;
    }
}
