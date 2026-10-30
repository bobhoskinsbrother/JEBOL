package org.jebol.domain.eval.compression.lzav;

import java.util.Arrays;

final class LzavDecoder {

    static final int BAD_PARAMETERS = -1;
    static final int SOURCE_OUT_OF_BOUNDS = -2;
    static final int DESTINATION_OUT_OF_BOUNDS = -3;
    static final int REFERENCE_OUT_OF_BOUNDS = -4;
    static final int LENGTH_MISMATCH = -5;
    static final int UNKNOWN_FORMAT = -6;

    private static final int FAST_COPY_MARGIN = 63;
    private static final int LONGEST_LENGTH_SHIFT = 28;
    private static final int FIRST_ALLOCATION = 64 * 1024;
    private static final int SLACK = 128;
    private static final int CHUNKS_PER_RUN = 4;

    private final byte[] input;
    private final int format;
    private final long inputEnd;
    private final long blockLimit;
    private final long outputEnd;
    private final long fastOutputLimit;
    private final long shortestReferenceLessOne;
    private byte[] output;
    private long at;
    private long written;
    private long header;
    private long carry;
    private int carryShift;
    private long distance;
    private long from;
    private long copyEnd;
    private int failure;

    private LzavDecoder(byte[] input, int wanted, int format) {
        this.input = input;
        this.format = format;
        inputEnd = input.length;
        blockLimit = inputEnd - (format == 3 ? 9 : 6);
        outputEnd = wanted;
        fastOutputLimit = outputEnd - FAST_COPY_MARGIN;
        shortestReferenceLessOne = (input[0] & 15) - 1;
        output = new byte[(int) Math.min((long) wanted + SLACK, FIRST_ALLOCATION)];
    }

    record Outcome(int code, byte[] octets) {
    }

    static Outcome decode(byte[] input, int wanted) {
        if (input.length == 0) {
            return new Outcome(wanted == 0 ? 0 : BAD_PARAMETERS, new byte[0]);
        }
        if (wanted <= 0) {
            return new Outcome(BAD_PARAMETERS, null);
        }
        int format = (input[0] & 0xFF) >>> 4;
        if (format != 3 && format != 2) {
            return new Outcome(UNKNOWN_FORMAT, null);
        }
        if (input.length < (format == 3 ? 10 : 7)) {
            return new Outcome(SOURCE_OUT_OF_BOUNDS, null);
        }
        LzavDecoder decoder = new LzavDecoder(input, wanted, format);
        int code = decoder.decodeBlocks();
        return new Outcome(code, code < 0 ? null : Arrays.copyOf(decoder.output, wanted));
    }

    private enum Step { BLOCK, REFERENCE, LITERAL_TAIL, FAILED, DONE }

    private int decodeBlocks() {
        at = 1;
        header = octetAt(at);
        Step step = Step.BLOCK;
        while (step != Step.DONE) {
            if (step == Step.FAILED) {
                return failure;
            }
            if (step == Step.BLOCK) {
                step = at < blockLimit ? readBlockHeader() : Step.DONE;
            } else if (step == Step.REFERENCE) {
                step = format == 3 ? copyThirdReference() : copySecondReference();
            } else {
                step = copyLiteralTail();
            }
        }
        return written == outputEnd ? (int) written : LENGTH_MISMATCH;
    }

    private Step readBlockHeader() {
        return (header & 0x30) != 0 ? Step.REFERENCE : readLiterals();
    }

    private Step fail(int code) {
        failure = code;
        return Step.FAILED;
    }

    private boolean reachesBeforeStart(long fromHeader, long nextCarry, int nextShift) {
        distance = fromHeader << carryShift | carry;
        carryShift = nextShift;
        carry = nextCarry;
        from = written - distance;
        return distance > written;
    }

    private static final long[] THIRD_OFFSET_MASK = {0, 0x3FF, 0x7FFF, 0x1FFFFF};
    private static final long[] THIRD_CARRY_MASK = {0, 0, 7, 31};
    private static final int[] THIRD_CARRY_SHIFT = {0, 0, 3, 5};

    private Step copyThirdReference() {
        at++;
        int octets = (int) ((header >>> 4) & 3);
        long word = read32(at);
        int nextShift = THIRD_CARRY_SHIFT[octets];
        int octetBits = octets << 3;
        if (reachesBeforeStart((header >>> 6 | (word << 2 & 0xFFFFFFFFL)) & THIRD_OFFSET_MASK[octets],
                (word >>> (octetBits - nextShift)) & THIRD_CARRY_MASK[octets], nextShift)) {
            return fail(REFERENCE_OUT_OF_BOUNDS);
        }
        at += octets;
        word >>>= octetBits;
        copyEnd = written + shortestReferenceLessOne;
        long count = header & 15;
        if (count != 0) {
            copyEnd += count;
            header = word & 0xFF;
            if (written < fastOutputLimit) {
                if (distance > 15) {
                    copyChunks(16, 1, 0);
                    copyChunks(4, 1, 16);
                    written = copyEnd;
                    return Step.BLOCK;
                }
                if (distance > 7) {
                    copyChunks(8, 2, 0);
                    copyChunks(4, 1, 16);
                    written = copyEnd;
                    return Step.BLOCK;
                }
                return fail(REFERENCE_OUT_OF_BOUNDS);
            }
        } else {
            header = word;
            at++;
            copyEnd += 16 + (header & 0x7F);
            if ((header & 0x80) != 0) {
                int shift = 7;
                do {
                    header = octetAt(at);
                    at++;
                    copyEnd += (header & 0x7F) << shift;
                    if (shift == LONGEST_LENGTH_SHIFT) {
                        break;
                    }
                    shift += 7;
                } while ((header & 0x80) != 0);
            }
            header = octetAt(at);
            if (copyEnd < fastOutputLimit && distance > 15) {
                copyRunsUpToEnd(16);
                return Step.BLOCK;
            }
            if (copyEnd < fastOutputLimit && distance > 7) {
                copyRunsUpToEnd(8);
                return Step.BLOCK;
            }
        }
        if (distance < 8) {
            return fail(REFERENCE_OUT_OF_BOUNDS);
        }
        if (copyEnd > outputEnd) {
            copyOctetsUntil(outputEnd);
            return fail(DESTINATION_OUT_OF_BOUNDS);
        }
        while (written < copyEnd - 15) {
            copyChunks(8, 2, 0);
            written += 16;
            from += 16;
        }
        copyOctetsUntil(copyEnd);
        return Step.BLOCK;
    }

    private static final long[] SECOND_OFFSET_MASK = {0, 0xFF, 0xFFFF, 0xFFFFFF};
    private static final int[] SECOND_CARRY_SHIFT = {0, 0, 0, 3};

    private Step copySecondReference() {
        int octets = (int) ((header >>> 4) & 3);
        at++;
        int octetBits = octets << 3;
        long word = read32(at);
        at += octets;
        long offsetBits = word & SECOND_OFFSET_MASK[octets];
        word >>>= octetBits;
        if (reachesBeforeStart((header >>> 6 | offsetBits << 2) & 0x7FFFFF, offsetBits >>> 21,
                SECOND_CARRY_SHIFT[octets])) {
            return fail(REFERENCE_OUT_OF_BOUNDS);
        }
        long count = header & 15;
        if (count != 0) {
            count += shortestReferenceLessOne;
            header = word & 0xFF;
            if (written < fastOutputLimit) {
                if (distance > 15) {
                    copyChunks(16, 1, 0);
                    copyChunks(4, 1, 16);
                } else if (distance > 7) {
                    copyChunks(8, 2, 0);
                } else if (distance > 3) {
                    copyChunks(4, 2, 0);
                } else {
                    return fail(REFERENCE_OUT_OF_BOUNDS);
                }
                written += count;
                return Step.BLOCK;
            }
            return copyCheckedReference(count);
        }
        header = word & 0xFF;
        at++;
        count = 16 + shortestReferenceLessOne + header;
        if (header == 255) {
            count += octetAt(at);
            at++;
        }
        copyEnd = written + count;
        header = octetAt(at);
        if (copyEnd < fastOutputLimit && distance > 15) {
            copyRunsUpToEnd(16);
            return Step.BLOCK;
        }
        return copyCheckedReference(count);
    }

    private Step copyCheckedReference(long count) {
        if (count > distance) {
            return fail(REFERENCE_OUT_OF_BOUNDS);
        }
        if (written + count > outputEnd) {
            copyOctetsUntil(outputEnd);
            return fail(DESTINATION_OUT_OF_BOUNDS);
        }
        copyOctetsUntil(written + count);
        return Step.BLOCK;
    }

    private Step readLiterals() {
        long carried = header >>> 6;
        at++;
        long count = header & 15;
        if (count != 0) {
            from = at;
            at += count;
            addCarry(carried);
            if (written < fastOutputLimit && from < blockLimit - 16) {
                header = octetAt(at);
                copyLiteralChunk(16);
                written += count;
                return Step.REFERENCE;
            }
        } else {
            header = octetAt(at);
            addCarry(carried);
            at++;
            count = readLongLiteralCount() + 16;
            from = at;
            at += count;
            copyEnd = written + count;
            if (copyEnd < fastOutputLimit && at < blockLimit - 64) {
                do {
                    copyLiteralChunk(64);
                    written += 64;
                    from += 64;
                } while (written < copyEnd);
                written = copyEnd;
                header = octetAt(at);
                return Step.REFERENCE;
            }
        }
        copyEnd = written + count;
        return Step.LITERAL_TAIL;
    }

    private void addCarry(long carried) {
        carry |= carried << carryShift;
        carryShift += 2;
    }

    private long readLongLiteralCount() {
        long count = header & 0x7F;
        if ((header & 0x80) != 0) {
            int shift = 7;
            do {
                header = octetAt(at);
                at++;
                count |= (header & 0x7F) << shift;
                if (shift == LONGEST_LENGTH_SHIFT) {
                    break;
                }
                shift += 7;
            } while ((header & 0x80) != 0);
            if (format == 2) {
                count &= 0x7FFFFFFF;
            }
        }
        return count;
    }

    private Step copyLiteralTail() {
        if (copyEnd > outputEnd) {
            if (at > inputEnd) {
                return sourceRanOut();
            }
            copyInputUntil(outputEnd);
            return fail(DESTINATION_OUT_OF_BOUNDS);
        }
        if (at < inputEnd) {
            header = octetAt(at);
            copyInputUntil(copyEnd);
            return Step.BLOCK;
        }
        if (at != inputEnd) {
            return sourceRanOut();
        }
        copyInputUntil(copyEnd);
        return Step.DONE;
    }

    private Step sourceRanOut() {
        long available = inputEnd - from;
        copyInputUntil(written + Math.min(available, outputEnd - written));
        return fail(SOURCE_OUT_OF_BOUNDS);
    }

    private void copyChunks(int size, int count, int skip) {
        makeRoomUpTo(written + skip + (long) size * count);
        for (int chunk = 0; chunk < count; chunk++) {
            System.arraycopy(output, (int) (from + skip + (long) chunk * size), output,
                    (int) (written + skip + (long) chunk * size), size);
        }
    }

    private void copyRunsUpToEnd(int chunkSize) {
        do {
            copyChunks(chunkSize, CHUNKS_PER_RUN, 0);
            written += (long) chunkSize * CHUNKS_PER_RUN;
            from += (long) chunkSize * CHUNKS_PER_RUN;
        } while (written < copyEnd);
        written = copyEnd;
    }

    private void copyOctetsUntil(long end) {
        makeRoomUpTo(end);
        while (written < end) {
            output[(int) written++] = output[(int) from++];
        }
    }

    private void copyLiteralChunk(int length) {
        makeRoomUpTo(written + length);
        for (int each = 0; each < length; each++) {
            output[(int) (written + each)] = (byte) octetAt(from + each);
        }
    }

    private void copyInputUntil(long end) {
        makeRoomUpTo(end);
        while (written < end) {
            output[(int) written++] = (byte) octetAt(from++);
        }
    }

    private void makeRoomUpTo(long end) {
        if (end > output.length) {
            long grown = Math.max(end, 2L * output.length);
            output = Arrays.copyOf(output, (int) Math.min(grown, outputEnd + SLACK));
        }
    }

    private long octetAt(long position) {
        return position >= 0 && position < inputEnd ? input[(int) position] & 0xFF : 0;
    }

    private long read32(long position) {
        return octetAt(position) | octetAt(position + 1) << 8
                | octetAt(position + 2) << 16 | octetAt(position + 3) << 24;
    }
}
