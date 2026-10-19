package org.jebol.domain.eval.compression.brotli;

import java.util.Arrays;

final class BrotliMatches {

    private long[] distance = new long[0];
    private int[] lengthAndCode = new int[0];

    private void makeRoom(int wanted) {
        if (wanted <= distance.length) {
            return;
        }
        int grown = Math.max(wanted, Math.max(64, distance.length * 2));
        distance = Arrays.copyOf(distance, grown);
        lengthAndCode = Arrays.copyOf(lengthAndCode, grown);
    }

    void addCopy(int which, long howFarBack, int length) {
        makeRoom(which + 1);
        distance[which] = howFarBack;
        lengthAndCode[which] = length << 5;
    }

    void addWord(int which, long howFarBack, int length, int lengthCode) {
        makeRoom(which + 1);
        distance[which] = howFarBack;
        lengthAndCode[which] = (length << 5) | (length == lengthCode ? 0 : lengthCode);
    }

    long readTheDistanceAt(int which) {
        return distance[which];
    }

    int readTheLengthAt(int which) {
        return lengthAndCode[which] >>> 5;
    }

    int readTheLengthCodeAt(int which) {
        int code = lengthAndCode[which] & 31;
        return code != 0 ? code : readTheLengthAt(which);
    }

    void keepOnly(int which) {
        distance[0] = distance[which];
        lengthAndCode[0] = lengthAndCode[which];
    }

    void copyOneOverFrom(int to, BrotliMatches other, int from) {
        makeRoom(to + 1);
        distance[to] = other.distance[from];
        lengthAndCode[to] = other.lengthAndCode[from];
    }
}
