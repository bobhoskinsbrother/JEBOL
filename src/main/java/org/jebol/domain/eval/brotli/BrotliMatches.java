package org.jebol.domain.eval.brotli;

import java.util.Arrays;

/**
 * The list of candidate matches at one position.
 *
 * <p>{@code BackwardMatch} in {@code hash.h}, held as parallel arrays because
 * a hundred and twenty eight of them may be found at every byte of the input.
 *
 * <p>A match is a distance and a length. A match on a dictionary word carries a
 * third thing: the length its code will name, which is the length of the word
 * before the transform rather than the length of the bytes it produces. The C
 * packs that into the low five bits of the length and spells "same as the
 * length" as zero, and that is kept, because the packing is what the priced
 * parse compares.
 */
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

    /**
     * Keeps one match and throws the rest away, which is what happens when a
     * copy turns out long enough that nothing shorter is worth pricing.
     */
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
