package org.jebol.domain.eval.compression.deflate;

import java.nio.charset.StandardCharsets;

final class DeflateFixtures {

    private static final String[] VOCABULARY = {"the ", "quick ", "brown ", "fox ", "jumps ", "over ", "lazy ",
        "dog ", "REBOL ", "block! ", "[", "]\n", "compress ", "level ", "12 ", "binary ", "#{00} ", "series "};

    private static final int MULTIPLIER = 1103515245;

    private static final int INCREMENT = 12345;

    private static final int MIXED_STRETCH = 3000;

    private int state;

    byte[] of(String kind, int length) {
        return switch (kind) {
            case "words" -> words(length, 1);
            case "noise" -> noise(length, 2);
            case "mixed" -> mixed(length, 3);
            case "zeros" -> new byte[length];
            default -> throw new IllegalArgumentException(kind);
        };
    }

    private int nextRandom() {
        state = state * MULTIPLIER + INCREMENT;
        return (state >>> 16) & 0x7FFF;
    }

    private byte[] words(int length, int seed) {
        state = seed;
        byte[] out = new byte[length];
        int at = 0;
        while (at < length) {
            byte[] word = VOCABULARY[nextRandom() % VOCABULARY.length].getBytes(StandardCharsets.US_ASCII);
            for (int each = 0; each < word.length && at < length; each++) {
                out[at++] = word[each];
            }
        }
        return out;
    }

    private byte[] noise(int length, int seed) {
        state = seed;
        byte[] out = new byte[length];
        for (int at = 0; at < length; at++) {
            out[at] = (byte) nextRandom();
        }
        return out;
    }

    private byte[] mixed(int length, int seed) {
        byte[] words = words(length, seed);
        byte[] noise = noise(length, seed + 1);
        byte[] out = new byte[length];
        for (int at = 0; at < length; at++) {
            out[at] = (at / MIXED_STRETCH) % 2 == 0 ? words[at] : noise[at];
        }
        return out;
    }
}
