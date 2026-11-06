package org.jebol.domain.eval.compression.bzip2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class Bzip2TestInputs {

    public static final int NOISE = 0;
    public static final int NO_RUNS = 1;
    public static final int CONSTANT = 2;
    public static final int RUNS = 3;
    public static final int PERIODIC = 4;
    public static final int WORDS = 5;

    public static final long FNV_START = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private Bzip2TestInputs() {
    }

    public static int nextRandom(int[] seed) {
        int value = seed[0];
        value *= (int) 2654435761L;
        value ^= (int) 2246822519L;
        value = Integer.rotateLeft(value, 13);
        seed[0] = value;
        return value >>> 5;
    }

    public static byte[] make(int kind, int size, int a, int b, int seed) {
        int[] state = {seed};
        byte[] out = new byte[size];
        switch (kind) {
            case NOISE -> {
                for (int at = 0; at < size; at++) {
                    out[at] = (byte) (nextRandom(state) % a);
                }
            }
            case NO_RUNS -> {
                int previous = 256;
                for (int at = 0; at < size; at++) {
                    int value = nextRandom(state) % a;
                    if (value == previous) {
                        value = (value + 1) % a;
                    }
                    out[at] = (byte) value;
                    previous = value;
                }
            }
            case CONSTANT -> Arrays.fill(out, (byte) a);
            case RUNS -> fillRuns(out, a, b, state);
            case PERIODIC -> {
                for (int at = 0; at < size; at++) {
                    out[at] = at < a ? (byte) nextRandom(state) : out[at - a];
                }
            }
            case WORDS -> fillWords(out, a, state);
            default -> fillFibonacci(out, a, b, state);
        }
        return out;
    }

    private static void fillRuns(byte[] out, int a, int b, int[] state) {
        int at = 0;
        int value = 0;
        while (at < out.length) {
            int length = a + (b != 0 ? nextRandom(state) % b : 0);
            for (int i = 0; i < length && at < out.length; i++) {
                out[at++] = (byte) value;
            }
            value = (value + 1 + nextRandom(state) % 250) & 0xFF;
        }
    }

    private static void fillWords(byte[] out, int a, int[] state) {
        int at = 0;
        while (at < out.length) {
            int word = nextRandom(state) % a;
            int letters = word;
            int length = 2 + word % 8;
            for (int i = 0; i < length && at < out.length; i++) {
                letters = letters * 31 + 7;
                out[at++] = (byte) ('a' + Integer.remainderUnsigned(letters >>> 8, 26));
            }
            if (at < out.length) {
                out[at++] = ' ';
            }
        }
    }

    private static void fillFibonacci(byte[] out, int a, int b, int[] state) {
        long[] weights = new long[64];
        weights[0] = 1;
        weights[1] = 1;
        for (int i = 2; i < a; i++) {
            weights[i] = weights[i - 1] + weights[i - 2];
        }
        long total = 0;
        for (int i = 0; i < a; i++) {
            total += weights[i];
        }
        for (int at = 0; at < out.length; at++) {
            long high = (long) nextRandom(state) << 27;
            long pick = (high | nextRandom(state)) % total;
            int symbol = 0;
            while (pick >= weights[symbol]) {
                pick -= weights[symbol];
                symbol++;
            }
            out[at] = (byte) (symbol * b);
        }
    }

    public static long fold(long running, byte[] octets, int from, int length) {
        long hash = running;
        for (int at = from; at < from + length; at++) {
            hash ^= octets[at] & 0xFF;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    public static long foldWord(long running, long word) {
        long hash = running;
        for (int at = 0; at < 8; at++) {
            hash ^= (word >>> (8 * at)) & 0xFF;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    public static String digest(byte[] octets, int from, int length) {
        return "%016x".formatted(fold(FNV_START, octets, from, length));
    }

    public static byte[] resource(String path) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                Bzip2TestInputs.class.getResourceAsStream("/bzip2/" + path), path)) {
            return in.readAllBytes();
        }
    }

    public static List<String[]> rows(String table) throws IOException {
        return new String(resource(table), StandardCharsets.US_ASCII).lines().skip(1)
                .map(line -> line.split("\t", -1))
                .toList();
    }
}
