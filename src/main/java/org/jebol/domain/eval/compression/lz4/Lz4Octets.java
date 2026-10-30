package org.jebol.domain.eval.compression.lz4;

final class Lz4Octets {

    private Lz4Octets() {
    }

    static int read16(byte[] octets, int at) {
        return (octets[at] & 0xFF) | (octets[at + 1] & 0xFF) << 8;
    }

    static int read32(byte[] octets, int at) {
        return (octets[at] & 0xFF) | (octets[at + 1] & 0xFF) << 8
                | (octets[at + 2] & 0xFF) << 16 | (octets[at + 3] & 0xFF) << 24;
    }

    static long read64(byte[] octets, int at) {
        return (read32(octets, at) & 0xFFFFFFFFL) | (long) read32(octets, at + 4) << 32;
    }

    static int countMatching(byte[] octets, int from, int against, int limit) {
        int at = from;
        while (at < limit && octets[at] == octets[against + (at - from)]) {
            at++;
        }
        return at - from;
    }

    static int countBack(byte[] octets, int at, int match, int lowestAt, int lowestMatch) {
        int furthest = Math.max(lowestAt - at, lowestMatch - match);
        int back = 0;
        while (back > furthest && octets[at + back - 1] == octets[match + back - 1]) {
            back--;
        }
        return back;
    }

    static int countPattern(byte[] octets, int from, int limit, int pattern) {
        int at = from;
        while (at < limit && (octets[at] & 0xFF) == patternOctet(pattern, at - from)) {
            at++;
        }
        return at - from;
    }

    static int countPatternBackwards(byte[] octets, int from, int lowest, int pattern) {
        int at = from;
        int step = 3;
        while (at > lowest && (octets[at - 1] & 0xFF) == patternOctet(pattern, step)) {
            at--;
            step = (step + 3) & 3;
        }
        return from - at;
    }

    private static int patternOctet(int pattern, int position) {
        return (pattern >>> (8 * (position & 3))) & 0xFF;
    }
}
