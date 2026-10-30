package org.jebol.domain.eval.compression.lzav;

import java.util.Arrays;

final class LzavEncoder {

    static final int FORMAT = 3;
    static final int SHORT_REFERENCE_THRESHOLD = 1 << 18;

    private static final int WINDOW = 1 << 21;
    private static final int FINAL_LITERALS = 9;
    private static final int SHORTEST_OFFSET = 8;
    private static final int FIRST_OFFSET_LIMIT = (1 << 10) - 1;
    private static final int SECOND_OFFSET_LIMIT = (1 << 15) - 1;
    private static final int SHORT_INPUT = 16;
    private static final int SMALLEST_TABLE = (1 << 7) * 4 * 4;
    private static final int LARGEST_TABLE = 1 << 20;
    private static final int HASH_SHIFT = 12;
    private static final int[] CARRY_SHIFT_AFTER = {0, 0, 3, 5};
    private static final int SLACK = 64;

    private final byte[] input;
    private final int shortestReference;
    private final byte[] output;
    private final int[] table;
    private final int bucketMask;
    private int written;
    private int carryAt;
    private int carryShift;

    private LzavEncoder(byte[] input) {
        this.input = input;
        shortestReference = input.length < SHORT_REFERENCE_THRESHOLD ? 5 : 6;
        output = new byte[boundFor(input.length) + SLACK];
        int tableSize = SMALLEST_TABLE;
        while (tableSize < (long) input.length << 2 && tableSize << 1 <= LARGEST_TABLE) {
            tableSize <<= 1;
        }
        table = new int[tableSize / 4];
        bucketMask = (tableSize - 1) ^ 15;
    }

    static byte[] encode(byte[] input) {
        return new LzavEncoder(input).encode();
    }

    static int boundFor(int length) {
        if (length < SHORT_REFERENCE_THRESHOLD) {
            int longBlocks = length / (16 + 5);
            return (length - longBlocks * 5 + 15) / 16 * 2 - longBlocks + length + 16;
        }
        int perLength = 16 + 127 + 1;
        int longBlocks = length / (perLength + 6);
        return (length - longBlocks * 6 + perLength - 1) / perLength * 2 - longBlocks + length + 16;
    }

    private byte[] encode() {
        output[written++] = (byte) (FORMAT << 4 | shortestReference);
        if (input.length < SHORT_INPUT) {
            return encodeShort();
        }
        fillTable();
        compressBlocks();
        return Arrays.copyOf(output, written);
    }

    private byte[] encodeShort() {
        output[written++] = (byte) input.length;
        System.arraycopy(input, 0, output, written, input.length);
        return Arrays.copyOf(output, 2 + Math.max(input.length, FINAL_LITERALS));
    }

    private void fillTable() {
        int first = read32(0);
        for (int bucket = 0; bucket < table.length; bucket += 2) {
            table[bucket] = first;
        }
    }

    private void compressBlocks() {
        int matchEnd = input.length - FINAL_LITERALS;
        int hashEnd = matchEnd - 15 + FINAL_LITERALS;
        int at = 0;
        int anchor = 0;
        carryAt = written;
        long averageMatchRate = 100L << 17;
        while (at < hashEnd) {
            int word = read32(at);
            int second = readSecond(at + 4);
            int bucket = bucketFor(word, second);
            int position = at;
            int firstWord = table[bucket];
            int windowAt;
            if (word != firstWord) {
                if (word != table[bucket + 2] || second != readSecond(table[bucket + 3] + 4)) {
                    at = skipUnmatched(bucket, word, position, at, anchor, averageMatchRate);
                    averageMatchRate -= averageMatchRate >> 11;
                    continue;
                }
                windowAt = table[bucket + 3];
            } else {
                windowAt = table[bucket + 1];
                if (second != readSecond(windowAt + 4)) {
                    if (word != table[bucket + 2] || second != readSecond(table[bucket + 3] + 4)) {
                        at = skipUnmatched(bucket, word, position, at, anchor, averageMatchRate);
                        averageMatchRate -= averageMatchRate >> 11;
                        continue;
                    }
                    windowAt = table[bucket + 3];
                }
            }
            long distance = (long) position - windowAt;
            if (distance < SHORTEST_OFFSET) {
                at++;
                continue;
            }
            if (word == firstWord) {
                if (distance > 15) {
                    table[bucket + 1] = position;
                }
            } else {
                table[bucket + 2] = firstWord;
                table[bucket + 3] = table[bucket + 1];
                table[bucket] = word;
                table[bucket + 1] = position;
            }
            int repeat = matchLength(at, windowAt, matchEnd - at);
            int started = at;
            int literals = at - anchor;
            if (literals != 0) {
                int earlier = matchLengthBackwards(at, windowAt, Math.min(literals, windowAt));
                literals -= earlier;
                repeat += earlier;
                at -= earlier;
            }
            if (distance < (long) WINDOW << carryShift << (literals != 0 ? 2 : 0)) {
                int skipped = bucketFor(read32(started + 2), readSecond(started + 6));
                averageMatchRate -= averageMatchRate >> 10;
                table[skipped + 2] = read32(started + 2);
                table[skipped + 3] = position + 2;
                writeBlock(literals, repeat, distance, anchor);
                at += repeat;
                averageMatchRate += (long) repeat << 7;
                anchor = at;
                continue;
            }
            at = started + 1;
        }
        writeFinalLiterals(matchEnd - anchor + FINAL_LITERALS, anchor);
    }

    private int skipUnmatched(int bucket, int word, int position, int at, int anchor, long averageMatchRate) {
        table[bucket + 2] = word;
        table[bucket + 3] = position;
        long rate = averageMatchRate - (averageMatchRate >> 11);
        int next = at + 1;
        if (rate < (200 << 10) && at != anchor) {
            next += 1 + (position & 1);
            if (rate < (130 << 10)) {
                next++;
                if (rate < (100 << 10)) {
                    next += (int) (100 - (rate >> 10));
                }
            }
        }
        return next;
    }

    private void writeBlock(int literals, int repeat, long distance, int anchor) {
        int shift = carryShift;
        int lengthCode = repeat + 1 - shortestReference;
        output[carryAt] |= (byte) ((distance << 8) >>> shift);
        long offset = distance >>> shift;
        if (literals != 0) {
            long carried = offset << 6;
            offset >>>= 2;
            writeLiterals(literals, carried, anchor);
        }
        writeReference(lengthCode, offset);
    }

    private void writeLiterals(int literals, long carried, int anchor) {
        if (literals < 16) {
            output[written] = (byte) (carried | literals);
            written++;
        } else if (literals < 33) {
            output[written] = (byte) carried;
            output[written + 1] = (byte) (literals - 16);
            written += 2;
        } else {
            output[written] = (byte) carried;
            int remaining = literals - 16;
            while (remaining > 127) {
                output[written + 1] = (byte) (0x80 | remaining);
                remaining >>>= 7;
                written++;
            }
            output[written + 1] = (byte) remaining;
            written += 2;
        }
        System.arraycopy(input, anchor, output, written, literals);
        written += literals;
    }

    private void writeReference(int lengthCode, long offset) {
        int offsetOctets = 1 + (offset > FIRST_OFFSET_LIMIT ? 1 : 0) + (offset > SECOND_OFFSET_LIMIT ? 1 : 0);
        int header = (int) (offset << 6 | (long) offsetOctets << 4);
        int lastOffsetOctet = written + offsetOctets;
        carryShift = CARRY_SHIFT_AFTER[offsetOctets];
        carryAt = lastOffsetOctet;
        if (lengthCode < 16) {
            write32(written, header | lengthCode);
            written = lastOffsetOctet + 1;
            return;
        }
        write32(written, header);
        int remaining = lengthCode - 16;
        int at = lastOffsetOctet;
        while (remaining > 127) {
            output[at + 1] = (byte) (0x80 | remaining);
            remaining >>>= 7;
            at++;
        }
        output[at + 1] = (byte) remaining;
        written = at + 2;
    }

    private void writeFinalLiterals(int literals, int anchor) {
        int remaining = literals;
        if (literals > 15) {
            output[written++] = 0;
            remaining -= 16;
            while (remaining > 127) {
                output[written++] = (byte) (0x80 | remaining);
                remaining >>>= 7;
            }
        }
        output[written++] = (byte) remaining;
        System.arraycopy(input, anchor, output, written, literals);
        written += literals;
    }

    private int matchLength(int at, int windowAt, int longest) {
        int length = shortestReference;
        while (length < longest && input[at + length] == input[windowAt + length]) {
            length++;
        }
        return length;
    }

    private int matchLengthBackwards(int at, int windowAt, int longest) {
        int length = 0;
        while (length < longest && input[at - length - 1] == input[windowAt - length - 1]) {
            length++;
        }
        return length;
    }

    private int bucketFor(int word, int second) {
        int hash = (0x85A308D3 ^ second) * (0x243F6A88 ^ word);
        return ((hash >>> HASH_SHIFT) & bucketMask) >>> 2;
    }

    private int readSecond(int at) {
        return shortestReference == 5
                ? input[at] & 0xFF
                : (input[at] & 0xFF) | (input[at + 1] & 0xFF) << 8;
    }

    private int read32(int at) {
        return (input[at] & 0xFF) | (input[at + 1] & 0xFF) << 8
                | (input[at + 2] & 0xFF) << 16 | (input[at + 3] & 0xFF) << 24;
    }

    private void write32(int at, int value) {
        output[at] = (byte) value;
        output[at + 1] = (byte) (value >>> 8);
        output[at + 2] = (byte) (value >>> 16);
        output[at + 3] = (byte) (value >>> 24);
    }
}
