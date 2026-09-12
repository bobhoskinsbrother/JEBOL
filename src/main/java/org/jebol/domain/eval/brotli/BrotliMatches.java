package org.jebol.domain.eval.brotli;

import java.util.Arrays;

final class BrotliMatches {

    private long[] distance = new long[0];
    private int[] lengthAndCode = new int[0];

    private void room(int wanted) {
        if (wanted <= distance.length) {
            return;
        }
        int grown = Math.max(wanted, Math.max(64, distance.length * 2));
        distance = Arrays.copyOf(distance, grown);
        lengthAndCode = Arrays.copyOf(lengthAndCode, grown);
    }

    void addCopy(int which, long howFarBack, int length) {
        room(which + 1);
        distance[which] = howFarBack;
        lengthAndCode[which] = length << 5;
    }

    void addWord(int which, long howFarBack, int length, int lengthCode) {
        room(which + 1);
        distance[which] = howFarBack;
        lengthAndCode[which] = (length << 5) | (length == lengthCode ? 0 : lengthCode);
    }

    long distanceAt(int which) {
        return distance[which];
    }

    int lengthAt(int which) {
        return lengthAndCode[which] >>> 5;
    }

    int lengthCodeAt(int which) {
        int code = lengthAndCode[which] & 31;
        return code != 0 ? code : lengthAt(which);
    }

    void keepOnly(int which) {
        distance[0] = distance[which];
        lengthAndCode[0] = lengthAndCode[which];
    }

    void copyOneOverFrom(int to, BrotliMatches other, int from) {
        room(to + 1);
        distance[to] = other.distance[from];
        lengthAndCode[to] = other.lengthAndCode[from];
    }
}
