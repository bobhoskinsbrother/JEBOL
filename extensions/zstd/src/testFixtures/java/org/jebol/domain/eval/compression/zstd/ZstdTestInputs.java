package org.jebol.domain.eval.compression.zstd;

import java.util.Arrays;

public final class ZstdTestInputs {

    public static final int DATAGEN = 0;
    public static final int ZEROS = 1;
    public static final int TWO_SYMBOLS = 2;
    public static final int FAR_REPEATS = 3;
    public static final int ONE_BYTE_THEN_NOISE = 4;
    public static final int PERIODIC = 5;
    public static final int NOISE_THEN_REPEAT = 6;

    private static final int LITERAL_TABLE_LOG = 13;
    private static final int LITERAL_TABLE_SIZE = 1 << LITERAL_TABLE_LOG;
    private static final int LITERAL_TABLE_MASK = LITERAL_TABLE_SIZE - 1;

    private ZstdTestInputs() {
    }

    public static int nextRandom(int[] seed) {
        int value = seed[0];
        value *= (int) 2654435761L;
        value ^= (int) 2246822519L;
        value = Integer.rotateLeft(value, 13);
        seed[0] = value;
        return value >>> 5;
    }

    private static void fillLiteralDistribution(byte[] table, int literalProbability) {
        int first = literalProbability <= 0 ? 0 : '(';
        int last = literalProbability <= 0 ? 255 : '}';
        int character = literalProbability <= 0 ? 0 : '0';
        long probability = Math.max(literalProbability, 0);
        int at = 0;
        while (at < LITERAL_TABLE_SIZE) {
            long weight = (((LITERAL_TABLE_SIZE - at) * probability) >>> 8) + 1;
            long end = Math.min(at + weight, LITERAL_TABLE_SIZE);
            while (at < end) {
                table[at++] = (byte) character;
            }
            character = (character + 1) & 0xFF;
            if (character > last) {
                character = first;
            }
        }
    }

    private static int randomLength(int[] seed) {
        if ((nextRandom(seed) & 7) != 0) {
            return nextRandom(seed) & 0xF;
        }
        return (nextRandom(seed) & 0x1FF) + 0xF;
    }

    private static void generateBlock(byte[] buffer, int size, double matchProbability, byte[] table, int[] seed) {
        int matchProbability32 = (int) (32768 * matchProbability);
        int position = 0;
        int previousOffset = 1;
        while (matchProbability >= 1.0) {
            int size0 = nextRandom(seed) & 3;
            size0 = 1 << (16 + size0 * 2);
            size0 += nextRandom(seed) & (size0 - 1);
            if (size < position + size0) {
                Arrays.fill(buffer, position, size, (byte) 0);
                return;
            }
            Arrays.fill(buffer, position, position + size0, (byte) 0);
            position += size0;
            buffer[position - 1] = table[nextRandom(seed) & LITERAL_TABLE_MASK];
        }
        if (position == 0) {
            buffer[0] = table[nextRandom(seed) & LITERAL_TABLE_MASK];
            position = 1;
        }
        while (position < size) {
            if ((nextRandom(seed) & 0x7FFF) < matchProbability32) {
                int length = randomLength(seed) + 4;
                int end = Math.min(position + length, size);
                boolean isRepeat = (nextRandom(seed) & 15) == 2;
                int randomOffset = (nextRandom(seed) & 0x7FFF) + 1;
                int offset = isRepeat ? previousOffset : Math.min(randomOffset, position);
                int match = position - offset;
                while (position < end) {
                    buffer[position++] = buffer[match++];
                }
                previousOffset = offset;
            } else {
                int length = randomLength(seed);
                int end = Math.min(position + length, size);
                while (position < end) {
                    buffer[position++] = table[nextRandom(seed) & LITERAL_TABLE_MASK];
                }
            }
        }
    }

    public static byte[] datagen(int size, double matchProbability, double literalProbability, int seed) {
        byte[] buffer = new byte[size];
        byte[] table = new byte[LITERAL_TABLE_SIZE];
        Arrays.fill(table, (byte) '0');
        double literals = literalProbability <= 0.0 ? matchProbability / 4.5 : literalProbability;
        fillLiteralDistribution(table, (int) (literals * 256 + 0.001));
        if (size != 0) {
            generateBlock(buffer, size, matchProbability, table, new int[]{seed});
        }
        return buffer;
    }

    public static byte[] make(int kind, int size, int a, int b, int seed) {
        int[] state = {seed};
        byte[] buffer = new byte[size];
        switch (kind) {
            case DATAGEN -> {
                return datagen(size, a / 100.0, b / 100.0, seed);
            }
            case ZEROS -> Arrays.fill(buffer, (byte) a);
            case TWO_SYMBOLS -> {
                for (int at = 0; at < size; at++) {
                    buffer[at] = Integer.remainderUnsigned(nextRandom(state), 100) < a ? (byte) 'a' : (byte) 'b';
                }
            }
            case FAR_REPEATS -> {
                int head = a * 16;
                for (int at = 0; at < head && at < size; at++) {
                    buffer[at] = (byte) nextRandom(state);
                }
                int tail = size - head;
                for (int at = tail - b * 1000; at < tail; at++) {
                    buffer[at] = (byte) ('a' + (nextRandom(state) & 15));
                }
                for (int chunk = 0; chunk < a; chunk++) {
                    int from = Integer.remainderUnsigned(nextRandom(state), a) * 16;
                    System.arraycopy(buffer, from, buffer, tail + chunk * 16, 16);
                }
            }
            case PERIODIC -> {
                int period = b * 1024;
                byte[] block = datagen(period, a / 100.0, 0, seed);
                for (int at = 0; at < size; at++) {
                    buffer[at] = block[at % period];
                }
                for (int at = 0; at < size; at += 4096) {
                    buffer[at] ^= (byte) (1 + (nextRandom(state) & 0x7F));
                }
            }
            case NOISE_THEN_REPEAT -> {
                int noise = a * 1024;
                int repeat = b * 1024;
                for (int at = 0; at < size; at++) {
                    buffer[at] = (byte) nextRandom(state);
                }
                for (int at = 0; at < repeat && noise + at < size; at++) {
                    buffer[noise + at] = buffer[at];
                }
            }
            default -> {
                Arrays.fill(buffer, (byte) 'x');
                for (int at = 0; at < size; at += 1 + Integer.remainderUnsigned(nextRandom(state), a + 1)) {
                    buffer[at] = (byte) nextRandom(state);
                }
            }
        }
        return buffer;
    }

    public static String digest(byte[] octets, int from, int length) {
        long running = 0xcbf29ce484222325L;
        for (int at = from; at < from + length; at++) {
            running ^= octets[at] & 0xFF;
            running *= 0x100000001b3L;
        }
        return "%016x".formatted(running);
    }
}
