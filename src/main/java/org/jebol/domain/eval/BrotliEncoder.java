package org.jebol.domain.eval;

import java.util.Arrays;

/**
 * Brotli, the writing half, at the fastest of its twelve settings.
 *
 * <p>{@code compress_fragment.c}, which is what {@code BrotliEncoderCompress}
 * runs at quality zero: one pass over the input, emitting each match as it is
 * found rather than gathering statistics first. The literal prefix code is
 * built from the bytes before any matching is done, which the file itself
 * calls an approximation; the command and distance codes start from a baked-in
 * guess and are re-derived per meta-block after the first.
 *
 * <p>It is byte for byte what a real 3.22.5 writes at level zero, which is
 * what the two exact-byte assertions in Rebol's own suite ask for and what a
 * round trip on its own would never establish.
 *
 * <p>Levels above zero are not here. The eleven other settings are eleven
 * other encoders -- block splitting, histogram clustering, context modelling
 * and a near-optimal parse -- and porting them is a far larger job than
 * reading any of them. What this build writes at every level is what a real
 * one writes at level zero: valid Brotli that any decoder reads, and smaller
 * than the input, but not the bytes a real one would write above level zero.
 * {@link BrotliDecoder} has no such limit and reads all twelve.
 */
final class BrotliEncoder {

    private BrotliEncoder() {
    }

    private static final int WINDOW_BITS = 22;
    private static final int COMMAND_SYMBOLS = 704;
    private static final int CODE_LENGTH_CODES = 18;
    private static final int REPEAT_PREVIOUS_CODE_LENGTH = 16;
    private static final int REPEAT_ZERO_CODE_LENGTH = 17;
    private static final int INITIAL_REPEATED_CODE_LENGTH = 8;
    private static final int MAX_HUFFMAN_BITS = 16;
    private static final long HASH_MULTIPLIER = 0x1E35A7BDL;
    private static final int MAX_DISTANCE = (1 << 18) - 16;
    private static final int FIRST_BLOCK_SIZE = 3 << 15;
    private static final int MERGE_BLOCK_SIZE = 1 << 16;
    private static final int INPUT_MARGIN_BYTES = 16;
    private static final int MIN_MATCH_LENGTH = 5;
    private static final int MIN_RATIO = 980;
    private static final int MAX_TABLE_SIZE = 1 << 15;

    private static final int[] CODE_LENGTH_DEPTH =
            {4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 0, 4, 4};
    private static final int[] CODE_LENGTH_BITS =
            {0, 8, 4, 12, 2, 10, 6, 14, 1, 9, 5, 13, 3, 15, 31, 0, 11, 7};

    private static final int[] SHELL_GAPS = {132, 57, 23, 10, 4, 1};

    private static final int[] DEFAULT_COMMAND_DEPTHS = {
            0, 4, 4, 5, 6, 6, 7, 7, 7, 7, 7, 8, 8, 8, 8, 8,
            0, 0, 0, 4, 4, 4, 4, 4, 5, 5, 6, 6, 6, 6, 7, 7,
            7, 7, 10, 10, 10, 10, 10, 10, 0, 4, 4, 5, 5, 5, 6, 6,
            7, 8, 8, 9, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10,
            5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            6, 6, 6, 6, 6, 6, 5, 5, 5, 5, 5, 5, 4, 4, 4, 4,
            4, 4, 4, 5, 5, 5, 5, 5, 5, 6, 6, 7, 7, 7, 8, 10,
            12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
            0, 0, 0, 0,
    };

    private static final int[] DEFAULT_COMMAND_BITS = {
            0, 0, 8, 9, 3, 35, 7, 71,
            39, 103, 23, 47, 175, 111, 239, 31,
            0, 0, 0, 4, 12, 2, 10, 6,
            13, 29, 11, 43, 27, 59, 87, 55,
            15, 79, 319, 831, 191, 703, 447, 959,
            0, 14, 1, 25, 5, 21, 19, 51,
            119, 159, 95, 223, 479, 991, 63, 575,
            127, 639, 383, 895, 255, 767, 511, 1023,
            14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            27, 59, 7, 39, 23, 55, 30, 1, 17, 9, 25, 5, 0, 8, 4, 12,
            2, 10, 6, 21, 13, 29, 3, 19, 11, 15, 47, 31, 95, 63, 127, 255,
            767, 2815, 1791, 3839, 511, 2559, 1535, 3583, 1023, 3071, 2047, 4095,
            0, 0, 0, 0,
    };

    /** The command and distance codes for the first meta-block, already coded. */
    private static final int[] DEFAULT_COMMAND_CODE = {
            0xff, 0x77, 0xd5, 0xbf, 0xe7, 0xde, 0xea, 0x9e, 0x51, 0x5d, 0xde, 0xc6,
            0x70, 0x57, 0xbc, 0x58, 0x58, 0x58, 0xd8, 0xd8, 0x58, 0xd5, 0xcb, 0x8c,
            0xea, 0xe0, 0xc3, 0x87, 0x1f, 0x83, 0xc1, 0x60, 0x1c, 0x67, 0xb2, 0xaa,
            0x06, 0x83, 0xc1, 0x60, 0x30, 0x18, 0xcc, 0xa1, 0xce, 0x88, 0x54, 0x94,
            0x46, 0xe1, 0xb0, 0xd0, 0x4e, 0xb2, 0xf7, 0x04, 0x00,
    };
    private static final int DEFAULT_COMMAND_CODE_BITS = 448;

    private static final int[] COMMAND_HISTOGRAM_SEED = {
            0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 0, 0, 0, 0,
    };

    /**
     * How long a run of code lengths costs, and what it is.
     *
     * <p>The C ships two pairs of generated tables, seven hundred and four
     * numbers each. They are the same thing worked out here: a run of zeros is
     * symbol seventeen with three bits of count, a run of anything else is
     * symbol sixteen with two, and both come out most significant piece first
     * because the writer builds them backwards and reverses.
     */
    private static final int[] ZERO_RUN_WIDTH = new int[COMMAND_SYMBOLS];
    private static final long[] ZERO_RUN_BITS = new long[COMMAND_SYMBOLS];
    private static final int[] OTHER_RUN_WIDTH = new int[COMMAND_SYMBOLS];
    private static final long[] OTHER_RUN_BITS = new long[COMMAND_SYMBOLS];

    static {
        for (int reps = 1; reps < COMMAND_SYMBOLS; reps++) {
            Run run = new Run();
            int remaining = reps;
            if (remaining == 11) {
                run.add(0, 0, 0);
                remaining--;
            }
            if (remaining < 3) {
                for (int each = 0; each < remaining; each++) {
                    run.add(0, 0, 0);
                }
            } else {
                int start = run.count;
                remaining -= 3;
                while (true) {
                    run.add(REPEAT_ZERO_CODE_LENGTH, remaining & 7, 3);
                    remaining >>= 3;
                    if (remaining == 0) {
                        break;
                    }
                    remaining--;
                }
                run.reverseFrom(start);
            }
            ZERO_RUN_WIDTH[reps] = run.totalWidth();
            ZERO_RUN_BITS[reps] = run.packed();
        }
        for (int reps = 0; reps < COMMAND_SYMBOLS; reps++) {
            Run run = new Run();
            int remaining = reps;
            while (true) {
                run.add(REPEAT_PREVIOUS_CODE_LENGTH, remaining & 3, 2);
                remaining >>= 2;
                if (remaining == 0) {
                    break;
                }
                remaining--;
            }
            run.reverseFrom(0);
            OTHER_RUN_WIDTH[reps] = run.totalWidth();
            OTHER_RUN_BITS[reps] = run.packed();
        }
    }

    /** One run of code-length symbols, each with its extra bits. */
    private static final class Run {

        private final int[] symbols = new int[64];
        private final int[] extra = new int[64];
        private final int[] extraWidth = new int[64];
        private int count;

        void add(int symbol, int extraValue, int howManyExtraBits) {
            symbols[count] = symbol;
            extra[count] = extraValue;
            extraWidth[count] = howManyExtraBits;
            count++;
        }

        void reverseFrom(int start) {
            for (int low = start, high = count - 1; low < high; low++, high--) {
                int symbol = symbols[low];
                symbols[low] = symbols[high];
                symbols[high] = symbol;
                int value = extra[low];
                extra[low] = extra[high];
                extra[high] = value;
                int width = extraWidth[low];
                extraWidth[low] = extraWidth[high];
                extraWidth[high] = width;
            }
        }

        int totalWidth() {
            int total = 0;
            for (int each = 0; each < count; each++) {
                total += CODE_LENGTH_DEPTH[symbols[each]] + extraWidth[each];
            }
            return total;
        }

        long packed() {
            long value = 0;
            int at = 0;
            for (int each = 0; each < count; each++) {
                value |= (long) CODE_LENGTH_BITS[symbols[each]] << at;
                at += CODE_LENGTH_DEPTH[symbols[each]];
                value |= (long) extra[each] << at;
                at += extraWidth[each];
            }
            return value;
        }
    }

    /**
     * The whole stream: a window size, then the input in blocks of one window,
     * then an empty last meta-block.
     */
    static byte[] encoded(byte[] source) {
        Fragment fragment = new Fragment(source);
        return fragment.run();
    }

    private static final class Fragment {

        private final byte[] input;
        private final BitWriter writer;

        private final int[] commandDepth = new int[128];
        private final int[] commandBits = new int[128];
        private final int[] commandHistogram = new int[128];
        private final int[] literalDepth = new int[256];
        private final int[] literalBits = new int[256];
        private final int[] histogram = new int[256];
        private final HuffmanTree tree = new HuffmanTree();

        private int[] table = new int[0];
        private int tableBits;

        private int from;
        private int fragmentEnd;
        private int nextEmit;
        private int metaBlockStart;
        private int blockSize;
        private int totalBlockSize;
        private int mlenAt;
        private int literalRatio;

        private Fragment(byte[] input) {
            this.input = input;
            this.writer = new BitWriter(2 * input.length + 512);
            System.arraycopy(DEFAULT_COMMAND_DEPTHS, 0, commandDepth, 0, 128);
            System.arraycopy(DEFAULT_COMMAND_BITS, 0, commandBits, 0, 128);
        }

        private byte[] run() {
            writer.write(4, ((WINDOW_BITS - 17) << 1) | 1);
            int at = 0;
            do {
                int fragmentSize = Math.min(input.length - at, 1 << WINDOW_BITS);
                boolean isLast = at + fragmentSize == input.length;
                compressOneFragment(at, fragmentSize, isLast);
                at += fragmentSize;
            } while (at < input.length);
            return writer.written();
        }

        /**
         * {@code BrotliCompressFragmentFast}: one window's worth of input,
         * with the whole thing rewritten as stored bytes if what came out was
         * longer than that would have been.
         */
        private void compressOneFragment(int at, int size, boolean isLast) {
            int openedAt = writer.at();
            if (size == 0) {
                writer.write(1, 1);
                writer.write(1, 1);
                writer.jumpToByteBoundary();
                return;
            }
            prepareTable(size);
            compress(at, size);
            if (writer.at() - openedAt > 31 + ((long) size << 3)) {
                storeUncompressed(at, at + size, openedAt);
            }
            if (isLast) {
                writer.write(1, 1);
                writer.write(1, 1);
                writer.jumpToByteBoundary();
            }
        }

        /**
         * How wide the match table is.
         *
         * <p>{@code HashTableSize}, then the one-pass encoder's own
         * requirement that only odd widths are supported, which rounds an even
         * one up rather than down.
         */
        private void prepareTable(int size) {
            int wide = 256;
            while (wide < MAX_TABLE_SIZE && wide < size) {
                wide <<= 1;
            }
            int bits = Integer.numberOfTrailingZeros(wide);
            tableBits = (bits & 1) == 0 ? bits + 1 : bits;
            if (table.length != (1 << tableBits)) {
                table = new int[1 << tableBits];
            } else {
                Arrays.fill(table, 0);
            }
        }

        private void compress(int at, int size) {
            from = at;
            fragmentEnd = at + size;
            nextEmit = at;
            metaBlockStart = at;
            blockSize = Math.min(size, FIRST_BLOCK_SIZE);
            totalBlockSize = blockSize;
            mlenAt = writer.at() + 3;

            storeMetaBlockHeader(blockSize, false);
            writer.write(13, 0);
            literalRatio = buildAndStoreLiteralPrefixCode(from, blockSize);
            for (int each = 0; each + 7 < DEFAULT_COMMAND_CODE_BITS; each += 8) {
                writer.write(8, DEFAULT_COMMAND_CODE[each >> 3]);
            }
            writer.write(DEFAULT_COMMAND_CODE_BITS & 7,
                    DEFAULT_COMMAND_CODE[DEFAULT_COMMAND_CODE_BITS >> 3]);

            while (emitCommands()) {
                metaBlockStart = from;
                blockSize = Math.min(fragmentEnd - from, FIRST_BLOCK_SIZE);
                totalBlockSize = blockSize;
                mlenAt = writer.at() + 3;
                storeMetaBlockHeader(blockSize, false);
                writer.write(13, 0);
                literalRatio = buildAndStoreLiteralPrefixCode(from, blockSize);
                buildAndStoreCommandPrefixCode();
            }
        }

        /**
         * One pass of matching over the current block, and the tail of
         * literals after it.
         *
         * <p>Answers whether there is more input, in which case the caller
         * writes a fresh meta-block header and calls again. The block may also
         * be abandoned part way through and stored plainly, which is the same
         * answer.
         */
        private boolean emitCommands() {
            while (true) {
                System.arraycopy(COMMAND_HISTOGRAM_SEED, 0, commandHistogram, 0, 128);
                int end = from + blockSize;
                Match found = scanForMatches(end);
                if (found == Match.ABANDONED) {
                    return from < fragmentEnd;
                }

                from += blockSize;
                blockSize = Math.min(fragmentEnd - from, MERGE_BLOCK_SIZE);

                if (from < fragmentEnd && totalBlockSize + blockSize <= (1 << 20)
                        && shouldMergeBlock(from, blockSize)) {
                    totalBlockSize += blockSize;
                    writer.updateBits(20, totalBlockSize - 1, mlenAt);
                    continue;
                }

                if (nextEmit < end) {
                    int insert = end - nextEmit;
                    if (insert < 6210) {
                        emitInsertLength(insert);
                        emitLiterals(nextEmit, insert);
                    } else if (shouldStoreInstead(insert)) {
                        storeUncompressed(metaBlockStart, end, mlenAt - 3);
                    } else {
                        emitLongInsertLength(insert);
                        emitLiterals(nextEmit, insert);
                    }
                }
                nextEmit = end;
                return from < fragmentEnd;
            }
        }

        private enum Match { RAN_OUT, ABANDONED }

        /**
         * The matching loop.
         *
         * <p>If thirty-two bytes go by without a match, start looking at every
         * other byte; after thirty-two more, every third, and so on. A match
         * resets the stride. The C explains this as costing about a tenth of a
         * per cent of density on data that compresses, and being a large win
         * on data that does not, because the encoder gives up looking quickly.
         */
        private Match scanForMatches(int end) {
            if (blockSize < INPUT_MARGIN_BYTES) {
                return Match.RAN_OUT;
            }
            int limit = from + Math.min(blockSize - MIN_MATCH_LENGTH,
                    fragmentEnd - from - INPUT_MARGIN_BYTES);
            int at = from + 1;
            int lastDistance = -1;
            int nextHash = hashAt(at);

            while (true) {
                int skip = 32;
                int nextAt = at;
                int candidate;
                while (true) {
                    int hash = nextHash;
                    int step = skip++ >> 5;
                    at = nextAt;
                    nextAt = at + step;
                    if (nextAt > limit) {
                        return Match.RAN_OUT;
                    }
                    nextHash = hashAt(nextAt);
                    candidate = at - lastDistance;
                    if (candidate < at && isMatch(at, candidate)) {
                        table[hash] = at;
                        break;
                    }
                    candidate = table[hash];
                    table[hash] = at;
                    if (isMatch(at, candidate) && at - candidate <= MAX_DISTANCE) {
                        break;
                    }
                }

                int base = at;
                int length = MIN_MATCH_LENGTH + matchLength(
                        candidate + MIN_MATCH_LENGTH, at + MIN_MATCH_LENGTH,
                        end - at - MIN_MATCH_LENGTH);
                int distance = base - candidate;
                int insert = base - nextEmit;
                at += length;
                if (insert < 6210) {
                    emitInsertLength(insert);
                } else if (shouldStoreInstead(insert)) {
                    storeUncompressed(metaBlockStart, base, mlenAt - 3);
                    from = base;
                    nextEmit = base;
                    return Match.ABANDONED;
                } else {
                    emitLongInsertLength(insert);
                }
                emitLiterals(nextEmit, insert);
                if (distance == lastDistance) {
                    writer.write(commandDepth[64], commandBits[64]);
                    commandHistogram[64]++;
                } else {
                    emitDistance(distance);
                    lastDistance = distance;
                }
                emitCopyLengthWithLastDistance(length);
                nextEmit = at;
                if (at >= limit) {
                    return Match.RAN_OUT;
                }
                candidate = rehashAroundTheCopy(at);

                while (isMatch(at, candidate)) {
                    base = at;
                    length = MIN_MATCH_LENGTH + matchLength(
                            candidate + MIN_MATCH_LENGTH, at + MIN_MATCH_LENGTH,
                            end - at - MIN_MATCH_LENGTH);
                    if (at - candidate > MAX_DISTANCE) {
                        break;
                    }
                    at += length;
                    lastDistance = base - candidate;
                    emitCopyLength(length);
                    emitDistance(lastDistance);
                    nextEmit = at;
                    if (at >= limit) {
                        return Match.RAN_OUT;
                    }
                    candidate = rehashAroundTheCopy(at);
                }
                at++;
                nextHash = hashAt(at);
            }
        }

        /**
         * Puts the four positions just before {@code at} into the table, and
         * answers the candidate for {@code at} itself.
         *
         * <p>The C could start matching at {@code at} straight away and
         * explains that it fills these in first "to improve compression":
         * positions inside a copy are never scanned by the outer loop, so
         * without this they would never be candidates for anything later.
         */
        private int rehashAroundTheCopy(int at) {
            long window = loadEight(at - 3);
            table[hashOfShifted(window, 0)] = at - 3;
            table[hashOfShifted(window, 1)] = at - 2;
            table[hashOfShifted(window, 2)] = at - 1;
            int current = hashOfShifted(window, 3);
            int candidate = table[current];
            table[current] = at;
            return candidate;
        }

        private long loadEight(int at) {
            long value = 0;
            for (int each = 0; each < 8; each++) {
                int index = at + each;
                long octet = index < input.length ? input[index] & 0xFFL : 0L;
                value |= octet << (each * 8);
            }
            return value;
        }

        private int hashAt(int at) {
            return hashOfShifted(loadEight(at), 0);
        }

        private int hashOfShifted(long window, int offset) {
            long hashed = ((window >>> (8 * offset)) << 24) * HASH_MULTIPLIER;
            return (int) (hashed >>> (64 - tableBits));
        }

        private boolean isMatch(int one, int other) {
            if (other < 0 || one + MIN_MATCH_LENGTH > input.length) {
                return false;
            }
            for (int each = 0; each < MIN_MATCH_LENGTH; each++) {
                if (input[one + each] != input[other + each]) {
                    return false;
                }
            }
            return true;
        }

        private int matchLength(int one, int other, int limit) {
            int matched = 0;
            while (matched < limit
                    && input[one + matched] == input[other + matched]) {
                matched++;
            }
            return matched;
        }

        private void storeMetaBlockHeader(int length, boolean stored) {
            int nibbles = 6;
            writer.write(1, 0);
            if (length <= (1 << 16)) {
                nibbles = 4;
            } else if (length <= (1 << 20)) {
                nibbles = 5;
            }
            writer.write(2, nibbles - 4);
            writer.write(nibbles * 4, length - 1);
            writer.write(1, stored ? 1 : 0);
        }

        private void storeUncompressed(int begin, int end, int rewindTo) {
            writer.rewindTo(rewindTo);
            storeMetaBlockHeader(end - begin, true);
            writer.jumpToByteBoundary();
            writer.writeBytes(input, begin, end - begin);
        }

        /**
         * Whether an insert this long is better left uncompressed.
         *
         * <p>{@code ShouldUseUncompressedMode}. If what has been written so
         * far is more than one fiftieth of what is about to be inserted as
         * literals, the matching is paying for itself; otherwise it is not,
         * and if the literals were costing more than eight bits each anyway
         * the whole block goes down as stored bytes.
         */
        private boolean shouldStoreInstead(int insertLength) {
            int compressed = nextEmit - metaBlockStart;
            if (compressed * 50 > insertLength) {
                return false;
            }
            return literalRatio > MIN_RATIO;
        }

        private void emitLiterals(int at, int howMany) {
            for (int each = 0; each < howMany; each++) {
                int literal = input[at + each] & 0xFF;
                writer.write(literalDepth[literal], literalBits[literal]);
            }
        }

        private void emitInsertLength(int insertLength) {
            if (insertLength < 6) {
                int code = insertLength + 40;
                writer.write(commandDepth[code], commandBits[code]);
                commandHistogram[code]++;
            } else if (insertLength < 130) {
                int tail = insertLength - 2;
                int width = log2Floor(tail) - 1;
                int prefix = tail >> width;
                int code = (width << 1) + prefix + 42;
                writer.write(commandDepth[code], commandBits[code]);
                writer.write(width, tail - (prefix << width));
                commandHistogram[code]++;
            } else if (insertLength < 2114) {
                int tail = insertLength - 66;
                int width = log2Floor(tail);
                int code = width + 50;
                writer.write(commandDepth[code], commandBits[code]);
                writer.write(width, tail - (1 << width));
                commandHistogram[code]++;
            } else {
                writer.write(commandDepth[61], commandBits[61]);
                writer.write(12, insertLength - 2114);
                commandHistogram[61]++;
            }
        }

        private void emitLongInsertLength(int insertLength) {
            if (insertLength < 22594) {
                writer.write(commandDepth[62], commandBits[62]);
                writer.write(14, insertLength - 6210);
                commandHistogram[62]++;
            } else {
                writer.write(commandDepth[63], commandBits[63]);
                writer.write(24, insertLength - 22594);
                commandHistogram[63]++;
            }
        }

        private void emitCopyLength(int copyLength) {
            if (copyLength < 10) {
                writer.write(commandDepth[copyLength + 14],
                        commandBits[copyLength + 14]);
                commandHistogram[copyLength + 14]++;
            } else if (copyLength < 134) {
                int tail = copyLength - 6;
                int width = log2Floor(tail) - 1;
                int prefix = tail >> width;
                int code = (width << 1) + prefix + 20;
                writer.write(commandDepth[code], commandBits[code]);
                writer.write(width, tail - (prefix << width));
                commandHistogram[code]++;
            } else if (copyLength < 2118) {
                int tail = copyLength - 70;
                int width = log2Floor(tail);
                int code = width + 28;
                writer.write(commandDepth[code], commandBits[code]);
                writer.write(width, tail - (1 << width));
                commandHistogram[code]++;
            } else {
                writer.write(commandDepth[39], commandBits[39]);
                writer.write(24, copyLength - 2118);
                commandHistogram[39]++;
            }
        }

        /**
         * The same length, written in the part of the alphabet that says "and
         * the distance is the one before".
         */
        private void emitCopyLengthWithLastDistance(int copyLength) {
            if (copyLength < 12) {
                writer.write(commandDepth[copyLength - 4],
                        commandBits[copyLength - 4]);
                commandHistogram[copyLength - 4]++;
            } else if (copyLength < 72) {
                int tail = copyLength - 8;
                int width = log2Floor(tail) - 1;
                int prefix = tail >> width;
                int code = (width << 1) + prefix + 4;
                writer.write(commandDepth[code], commandBits[code]);
                writer.write(width, tail - (prefix << width));
                commandHistogram[code]++;
            } else if (copyLength < 136) {
                int tail = copyLength - 8;
                int code = (tail >> 5) + 30;
                writer.write(commandDepth[code], commandBits[code]);
                writer.write(5, tail & 31);
                writer.write(commandDepth[64], commandBits[64]);
                commandHistogram[code]++;
                commandHistogram[64]++;
            } else if (copyLength < 2120) {
                int tail = copyLength - 72;
                int width = log2Floor(tail);
                int code = width + 28;
                writer.write(commandDepth[code], commandBits[code]);
                writer.write(width, tail - (1 << width));
                writer.write(commandDepth[64], commandBits[64]);
                commandHistogram[code]++;
                commandHistogram[64]++;
            } else {
                writer.write(commandDepth[39], commandBits[39]);
                writer.write(24, copyLength - 2120);
                writer.write(commandDepth[64], commandBits[64]);
                commandHistogram[39]++;
                commandHistogram[64]++;
            }
        }

        private void emitDistance(int distance) {
            int shifted = distance + 3;
            int width = log2Floor(shifted) - 1;
            int prefix = (shifted >> width) & 1;
            int offset = (2 + prefix) << width;
            int code = 2 * (width - 1) + prefix + 80;
            writer.write(commandDepth[code], commandBits[code]);
            writer.write(width, shifted - offset);
            commandHistogram[code]++;
        }

        /**
         * The literal code, built from the input before any matching.
         *
         * <p>The first eleven appearances of each byte count triple, which the
         * C explains as accounting for the balancing effect of the matching
         * phase: bytes that turn out to sit inside matches are never written as
         * literals at all, so a flatter histogram is closer to the truth than
         * the raw one. Above thirty-two kilobytes only every twenty-ninth byte
         * is counted, and then every byte gets one added so that none of them
         * ends up with no code at all.
         *
         * <p>Answers the estimated cost in millibits per byte, which is what
         * decides later whether to abandon the block and store it plainly.
         */
        private int buildAndStoreLiteralPrefixCode(int at, int size) {
            Arrays.fill(histogram, 0);
            long total;
            if (size < (1 << 15)) {
                for (int each = 0; each < size; each++) {
                    histogram[input[at + each] & 0xFF]++;
                }
                total = size;
                for (int each = 0; each < 256; each++) {
                    int adjust = 2 * Math.min(histogram[each], 11);
                    histogram[each] += adjust;
                    total += adjust;
                }
            } else {
                int sampleRate = 29;
                for (int each = 0; each < size; each += sampleRate) {
                    histogram[input[at + each] & 0xFF]++;
                }
                total = (size + sampleRate - 1) / sampleRate;
                for (int each = 0; each < 256; each++) {
                    int adjust = 1 + 2 * Math.min(histogram[each], 11);
                    histogram[each] += adjust;
                    total += adjust;
                }
            }
            buildAndStoreHuffmanTreeFast(histogram, total, 8,
                    literalDepth, literalBits);
            long ratio = 0;
            for (int each = 0; each < 256; each++) {
                if (histogram[each] != 0) {
                    ratio += (long) histogram[each] * literalDepth[each];
                }
            }
            return (int) ((ratio * 125) / total);
        }

        /**
         * Whether the next block is enough like this one to keep going inside
         * the same meta-block rather than starting another.
         *
         * <p>{@code ShouldMergeBlock}: sample one byte in forty-three and
         * compare what those bytes would cost under this block's literal code
         * against what a fresh code would cost plus two hundred bits of
         * header.
         */
        private boolean shouldMergeBlock(int at, int size) {
            Arrays.fill(histogram, 0);
            int sampleRate = 43;
            for (int each = 0; each < size; each += sampleRate) {
                histogram[input[at + each] & 0xFF]++;
            }
            int total = (size + sampleRate - 1) / sampleRate;
            double room = (log2(total) + 0.5) * total + 200;
            for (int each = 0; each < 256; each++) {
                room -= histogram[each] * (literalDepth[each] + log2(histogram[each]));
            }
            return room >= 0.0;
        }

        /**
         * The command and distance codes for a meta-block after the first.
         *
         * <p>The sixty-four command symbols this encoder uses are not
         * contiguous in the full alphabet of seven hundred and four, so the
         * depths are shuffled into place before the code is stored and the
         * bits are shuffled back afterwards. The C says it does this "because
         * having the symbols in this order in the command bits saves a few
         * branches in the Emit* functions".
         */
        private void buildAndStoreCommandPrefixCode() {
            int[] spreadDepth = new int[COMMAND_SYMBOLS];
            int[] spreadBits = new int[64];

            tree.build(commandHistogram, 0, 64, 15, commandDepth, 0);
            tree.build(commandHistogram, 64, 64, 14, commandDepth, 64);

            System.arraycopy(commandDepth, 0, spreadDepth, 0, 24);
            System.arraycopy(commandDepth, 40, spreadDepth, 24, 8);
            System.arraycopy(commandDepth, 24, spreadDepth, 32, 8);
            System.arraycopy(commandDepth, 48, spreadDepth, 40, 8);
            System.arraycopy(commandDepth, 32, spreadDepth, 48, 8);
            System.arraycopy(commandDepth, 56, spreadDepth, 56, 8);
            convertBitDepthsToSymbols(spreadDepth, 0, 64, spreadBits, 0);
            System.arraycopy(spreadBits, 0, commandBits, 0, 24);
            System.arraycopy(spreadBits, 32, commandBits, 24, 8);
            System.arraycopy(spreadBits, 48, commandBits, 32, 8);
            System.arraycopy(spreadBits, 24, commandBits, 40, 8);
            System.arraycopy(spreadBits, 40, commandBits, 48, 8);
            System.arraycopy(spreadBits, 56, commandBits, 56, 8);
            convertBitDepthsToSymbols(commandDepth, 64, 64, commandBits, 64);

            Arrays.fill(spreadDepth, 0);
            System.arraycopy(commandDepth, 0, spreadDepth, 0, 8);
            System.arraycopy(commandDepth, 8, spreadDepth, 64, 8);
            System.arraycopy(commandDepth, 16, spreadDepth, 128, 8);
            System.arraycopy(commandDepth, 24, spreadDepth, 192, 8);
            System.arraycopy(commandDepth, 32, spreadDepth, 384, 8);
            for (int each = 0; each < 8; each++) {
                spreadDepth[128 + 8 * each] = commandDepth[40 + each];
                spreadDepth[256 + 8 * each] = commandDepth[48 + each];
                spreadDepth[448 + 8 * each] = commandDepth[56 + each];
            }
            storeHuffmanTree(spreadDepth, 0, COMMAND_SYMBOLS);
            storeHuffmanTree(commandDepth, 64, 64);
        }

        /**
         * A prefix code from a histogram, written out as it is built.
         *
         * <p>Four or fewer symbols are named outright, which the format has a
         * short form for; more than that goes out as code lengths under the
         * fixed code-length code, with runs of equal lengths coded.
         */
        private void buildAndStoreHuffmanTreeFast(int[] counts, long total,
                int maxBits, int[] depth, int[] bits) {

            int count = 0;
            int[] symbols = new int[4];
            int length = 0;
            long left = total;
            while (left != 0) {
                if (counts[length] != 0) {
                    if (count < 4) {
                        symbols[count] = length;
                    }
                    count++;
                    left -= counts[length];
                }
                length++;
            }

            if (count <= 1) {
                writer.write(4, 1);
                writer.write(maxBits, symbols[0]);
                depth[symbols[0]] = 0;
                bits[symbols[0]] = 0;
                return;
            }

            Arrays.fill(depth, 0, length, 0);
            tree.buildFast(counts, length, 14, depth);
            convertBitDepthsToSymbols(depth, 0, length, bits, 0);

            if (count <= 4) {
                writer.write(2, 1);
                writer.write(2, count - 1);
                for (int each = 0; each < count; each++) {
                    for (int other = each + 1; other < count; other++) {
                        if (depth[symbols[other]] < depth[symbols[each]]) {
                            int swapped = symbols[other];
                            symbols[other] = symbols[each];
                            symbols[each] = swapped;
                        }
                    }
                }
                for (int each = 0; each < count; each++) {
                    writer.write(maxBits, symbols[each]);
                }
                if (count == 4) {
                    writer.write(1, depth[symbols[0]] == 1 ? 1 : 0);
                }
                return;
            }

            writer.write(40, 0x000000FF55555554L);
            int previousValue = INITIAL_REPEATED_CODE_LENGTH;
            int at = 0;
            while (at < length) {
                int value = depth[at];
                int reps = 1;
                while (at + reps < length && depth[at + reps] == value) {
                    reps++;
                }
                at += reps;
                if (value == 0) {
                    writer.write(ZERO_RUN_WIDTH[reps], ZERO_RUN_BITS[reps]);
                    continue;
                }
                if (previousValue != value) {
                    writer.write(CODE_LENGTH_DEPTH[value], CODE_LENGTH_BITS[value]);
                    reps--;
                }
                if (reps < 3) {
                    while (reps != 0) {
                        reps--;
                        writer.write(CODE_LENGTH_DEPTH[value], CODE_LENGTH_BITS[value]);
                    }
                } else {
                    reps -= 3;
                    writer.write(OTHER_RUN_WIDTH[reps], OTHER_RUN_BITS[reps]);
                }
                previousValue = value;
            }
        }

        /**
         * A prefix code written out the long way: as code lengths, run-length
         * coded, under a second prefix code over the lengths.
         */
        private void storeHuffmanTree(int[] depths, int at, int length) {
            int[] symbols = new int[COMMAND_SYMBOLS];
            int[] extra = new int[COMMAND_SYMBOLS];
            int written = writeTreeAsCodeLengths(depths, at, length, symbols, extra);

            int[] counts = new int[CODE_LENGTH_CODES];
            for (int each = 0; each < written; each++) {
                counts[symbols[each]]++;
            }
            int distinct = 0;
            int onlyCode = 0;
            for (int each = 0; each < CODE_LENGTH_CODES; each++) {
                if (counts[each] != 0) {
                    if (distinct == 0) {
                        onlyCode = each;
                        distinct = 1;
                    } else {
                        distinct = 2;
                        break;
                    }
                }
            }

            int[] lengthDepth = new int[CODE_LENGTH_CODES];
            tree.build(counts, 0, CODE_LENGTH_CODES, 5, lengthDepth, 0);
            int[] lengthBits = new int[CODE_LENGTH_CODES];
            convertBitDepthsToSymbols(lengthDepth, 0, CODE_LENGTH_CODES, lengthBits, 0);

            storeTheCodeOverCodeLengths(distinct, lengthDepth);
            if (distinct == 1) {
                lengthDepth[onlyCode] = 0;
            }
            for (int each = 0; each < written; each++) {
                int symbol = symbols[each];
                writer.write(lengthDepth[symbol], lengthBits[symbol]);
                if (symbol == REPEAT_PREVIOUS_CODE_LENGTH) {
                    writer.write(2, extra[each]);
                } else if (symbol == REPEAT_ZERO_CODE_LENGTH) {
                    writer.write(3, extra[each]);
                }
            }
        }

        private void storeTheCodeOverCodeLengths(int distinct, int[] lengthDepth) {
            int toStore = CODE_LENGTH_CODES;
            if (distinct > 1) {
                while (toStore > 0
                        && lengthDepth[CODE_LENGTH_STORAGE_ORDER[toStore - 1]] == 0) {
                    toStore--;
                }
            }
            int skip = 0;
            if (lengthDepth[CODE_LENGTH_STORAGE_ORDER[0]] == 0
                    && lengthDepth[CODE_LENGTH_STORAGE_ORDER[1]] == 0) {
                skip = lengthDepth[CODE_LENGTH_STORAGE_ORDER[2]] == 0 ? 3 : 2;
            }
            writer.write(2, skip);
            for (int each = skip; each < toStore; each++) {
                int width = lengthDepth[CODE_LENGTH_STORAGE_ORDER[each]];
                writer.write(LENGTH_CODE_WIDTHS[width], LENGTH_CODE_SYMBOLS[width]);
            }
        }
    }

    private static final int[] CODE_LENGTH_STORAGE_ORDER =
            {1, 2, 3, 4, 0, 5, 17, 6, 16, 7, 8, 9, 10, 11, 12, 13, 14, 15};
    private static final int[] LENGTH_CODE_SYMBOLS = {0, 7, 3, 2, 1, 15};
    private static final int[] LENGTH_CODE_WIDTHS = {2, 4, 3, 2, 2, 4};

    /**
     * The code lengths of a prefix code, run-length coded.
     *
     * <p>Trailing zeros are dropped, and whether runs are coded at all is
     * decided by counting them first: for a short code the run markers cost
     * more than the lengths they save, so a code of fifty symbols or fewer
     * never uses them.
     */
    private static int writeTreeAsCodeLengths(int[] depth, int at, int length,
            int[] symbols, int[] extra) {

        int newLength = length;
        while (newLength > 0 && depth[at + newLength - 1] == 0) {
            newLength--;
        }
        boolean runsForNonZero = false;
        boolean runsForZero = false;
        if (length > 50) {
            long totalZeroRuns = 0;
            long totalOtherRuns = 0;
            long countZeroRuns = 1;
            long countOtherRuns = 1;
            for (int each = 0; each < newLength; ) {
                int value = depth[at + each];
                int reps = 1;
                while (each + reps < newLength && depth[at + each + reps] == value) {
                    reps++;
                }
                if (reps >= 3 && value == 0) {
                    totalZeroRuns += reps;
                    countZeroRuns++;
                }
                if (reps >= 4 && value != 0) {
                    totalOtherRuns += reps;
                    countOtherRuns++;
                }
                each += reps;
            }
            runsForNonZero = totalOtherRuns > countOtherRuns * 2;
            runsForZero = totalZeroRuns > countZeroRuns * 2;
        }

        int written = 0;
        int previousValue = INITIAL_REPEATED_CODE_LENGTH;
        for (int each = 0; each < newLength; ) {
            int value = depth[at + each];
            int reps = 1;
            if ((value != 0 && runsForNonZero) || (value == 0 && runsForZero)) {
                while (each + reps < newLength && depth[at + each + reps] == value) {
                    reps++;
                }
            }
            if (value == 0) {
                written = writeZeroRun(reps, symbols, extra, written);
            } else {
                written = writeOtherRun(previousValue, value, reps,
                        symbols, extra, written);
                previousValue = value;
            }
            each += reps;
        }
        return written;
    }

    private static int writeZeroRun(int howMany, int[] symbols, int[] extra,
            int written) {

        int at = written;
        int reps = howMany;
        if (reps == 11) {
            symbols[at] = 0;
            extra[at] = 0;
            at++;
            reps--;
        }
        if (reps < 3) {
            for (int each = 0; each < reps; each++) {
                symbols[at] = 0;
                extra[at] = 0;
                at++;
            }
            return at;
        }
        int start = at;
        reps -= 3;
        while (true) {
            symbols[at] = REPEAT_ZERO_CODE_LENGTH;
            extra[at] = reps & 7;
            at++;
            reps >>= 3;
            if (reps == 0) {
                break;
            }
            reps--;
        }
        reverse(symbols, start, at);
        reverse(extra, start, at);
        return at;
    }

    private static int writeOtherRun(int previousValue, int value, int howMany,
            int[] symbols, int[] extra, int written) {

        int at = written;
        int reps = howMany;
        if (previousValue != value) {
            symbols[at] = value;
            extra[at] = 0;
            at++;
            reps--;
        }
        if (reps == 7) {
            symbols[at] = value;
            extra[at] = 0;
            at++;
            reps--;
        }
        if (reps < 3) {
            for (int each = 0; each < reps; each++) {
                symbols[at] = value;
                extra[at] = 0;
                at++;
            }
            return at;
        }
        int start = at;
        reps -= 3;
        while (true) {
            symbols[at] = REPEAT_PREVIOUS_CODE_LENGTH;
            extra[at] = reps & 3;
            at++;
            reps >>= 2;
            if (reps == 0) {
                break;
            }
            reps--;
        }
        reverse(symbols, start, at);
        reverse(extra, start, at);
        return at;
    }

    private static void reverse(int[] values, int from, int to) {
        for (int low = from, high = to - 1; low < high; low++, high--) {
            int swapped = values[low];
            values[low] = values[high];
            values[high] = swapped;
        }
    }

    private static int log2Floor(int value) {
        return 31 - Integer.numberOfLeadingZeros(value);
    }

    /** {@code FastLog2}, whose defining property is that log2 of nought is nought. */
    private static double log2(int value) {
        return value == 0 ? 0.0 : Math.log(value) / Math.log(2.0);
    }

    private static void convertBitDepthsToSymbols(int[] depth, int depthAt,
            int length, int[] bits, int bitsAt) {

        int[] countPerLength = new int[MAX_HUFFMAN_BITS];
        for (int each = 0; each < length; each++) {
            countPerLength[depth[depthAt + each]]++;
        }
        countPerLength[0] = 0;
        int[] nextCode = new int[MAX_HUFFMAN_BITS];
        int code = 0;
        for (int width = 1; width < MAX_HUFFMAN_BITS; width++) {
            code = (code + countPerLength[width - 1]) << 1;
            nextCode[width] = code;
        }
        for (int each = 0; each < length; each++) {
            int width = depth[depthAt + each];
            if (width != 0) {
                bits[bitsAt + each] = reversedBits(width, nextCode[width]++);
            }
        }
    }

    private static int reversedBits(int howManyBits, int value) {
        int reversed = 0;
        for (int each = 0; each < howManyBits; each++) {
            reversed = (reversed << 1) | ((value >> each) & 1);
        }
        return reversed;
    }

    /**
     * The Huffman builder the C uses: merge the leaves into a tree, and if it
     * comes out deeper than the limit allows, raise the floor under the rare
     * symbols and start again.
     */
    private static final class HuffmanTree {

        private static final int ROOM = 2 * COMMAND_SYMBOLS + 4;
        private static final long SENTINEL = 0xFFFFFFFFL;

        private final long[] totalCount = new long[ROOM];
        private final int[] leftIndex = new int[ROOM];
        private final int[] valueIndex = new int[ROOM];

        private void put(int at, long count, int left, int value) {
            totalCount[at] = count;
            leftIndex[at] = left;
            valueIndex[at] = value;
        }

        /**
         * {@code BrotliCreateHuffmanTree}, whose sort breaks a tie in the
         * counts by putting the later symbol first.
         */
        void build(int[] counts, int countsAt, int length, int limit,
                int[] depth, int depthAt) {

            for (long floor = 1; ; floor *= 2) {
                int leaves = 0;
                for (int each = length; each != 0; ) {
                    each--;
                    long count = counts[countsAt + each] & 0xFFFFFFFFL;
                    if (count != 0) {
                        put(leaves++, Math.max(count, floor), -1, each);
                    }
                }
                if (leaves == 1) {
                    depth[depthAt + valueIndex[0]] = 1;
                    return;
                }
                if (assemble(leaves, depth, depthAt, limit, true)) {
                    return;
                }
            }
        }

        /**
         * {@code BrotliBuildAndStoreHuffmanTreeFast}, whose sort does not.
         *
         * <p>The two are one line apart and the line matters. Each file has a
         * static {@code SortHuffmanTree} of its own, and the one in
         * brotli_bit_stream.c is the whole of
         * {@code v0->total_count_ < v1->total_count_} -- no tie-break -- so
         * two symbols of equal count keep whatever order the shell sort leaves
         * them in. Using the other file's comparator here gives a code of
         * exactly the same shape with two of its symbols swapped, which is
         * valid Brotli, decodes correctly, and is not the bytes a real 3.22.5
         * writes.
         */
        void buildFast(int[] counts, int length, int limit, int[] depth) {
            for (long floor = 1; ; floor *= 2) {
                int leaves = 0;
                for (int each = length; each != 0; ) {
                    each--;
                    long count = counts[each] & 0xFFFFFFFFL;
                    if (count != 0) {
                        put(leaves++, count >= floor ? count : floor, -1, each);
                    }
                }
                if (assemble(leaves, depth, 0, limit, false)) {
                    return;
                }
            }
        }

        /**
         * Merges the leaves and reads the depths off the tree.
         *
         * <p>The leaves are sorted, a sentinel is placed after them, and new
         * parents are appended, which keeps the parents in ascending order and
         * lets the merge take its next-cheapest node from whichever of the two
         * runs has it without ever sorting again.
         */
        private boolean assemble(int leaves, int[] depth, int depthAt, int limit,
                boolean breakTiesBySymbol) {
            sort(leaves, breakTiesBySymbol);
            put(leaves, SENTINEL, -1, -1);
            put(leaves + 1, SENTINEL, -1, -1);
            int nextLeaf = 0;
            int nextParent = leaves + 1;
            for (int remaining = leaves - 1; remaining > 0; remaining--) {
                int left = totalCount[nextLeaf] <= totalCount[nextParent]
                        ? nextLeaf++ : nextParent++;
                int right = totalCount[nextLeaf] <= totalCount[nextParent]
                        ? nextLeaf++ : nextParent++;
                int at = 2 * leaves - remaining;
                totalCount[at] = totalCount[left] + totalCount[right];
                leftIndex[at] = left;
                valueIndex[at] = right;
                put(at + 1, SENTINEL, -1, -1);
            }
            return setDepth(2 * leaves - 1, depth, depthAt, limit);
        }

        /** Cheapest first, and among equals whatever the caller asks for. */
        private boolean sortsBefore(long count, int value, int other,
                boolean breakTiesBySymbol) {
            if (count != totalCount[other]) {
                return count < totalCount[other];
            }
            return breakTiesBySymbol && value > valueIndex[other];
        }

        private void sort(int leaves, boolean breakTiesBySymbol) {
            if (leaves < 13) {
                for (int each = 1; each < leaves; each++) {
                    long count = totalCount[each];
                    int left = leftIndex[each];
                    int value = valueIndex[each];
                    int to = each;
                    int at = each - 1;
                    while (sortsBefore(count, value, at, breakTiesBySymbol)) {
                        totalCount[to] = totalCount[at];
                        leftIndex[to] = leftIndex[at];
                        valueIndex[to] = valueIndex[at];
                        to = at;
                        if (at-- == 0) {
                            break;
                        }
                    }
                    totalCount[to] = count;
                    leftIndex[to] = left;
                    valueIndex[to] = value;
                }
                return;
            }
            for (int gapAt = leaves < 57 ? 2 : 0; gapAt < 6; gapAt++) {
                int gap = SHELL_GAPS[gapAt];
                for (int each = gap; each < leaves; each++) {
                    long count = totalCount[each];
                    int left = leftIndex[each];
                    int value = valueIndex[each];
                    int at = each;
                    while (at >= gap
                            && sortsBefore(count, value, at - gap, breakTiesBySymbol)) {
                        totalCount[at] = totalCount[at - gap];
                        leftIndex[at] = leftIndex[at - gap];
                        valueIndex[at] = valueIndex[at - gap];
                        at -= gap;
                    }
                    totalCount[at] = count;
                    leftIndex[at] = left;
                    valueIndex[at] = value;
                }
            }
        }

        private boolean setDepth(int root, int[] depth, int depthAt, int limit) {
            int[] stack = new int[16];
            int level = 0;
            int at = root;
            stack[0] = -1;
            while (true) {
                if (leftIndex[at] >= 0) {
                    level++;
                    if (level > limit) {
                        return false;
                    }
                    stack[level] = valueIndex[at];
                    at = leftIndex[at];
                    continue;
                }
                depth[depthAt + valueIndex[at]] = level;
                while (level >= 0 && stack[level] == -1) {
                    level--;
                }
                if (level < 0) {
                    return true;
                }
                at = stack[level];
                stack[level] = -1;
            }
        }
    }

    /**
     * The bit stream, least significant bit of each byte first.
     *
     * <p>Every write leaves the byte after the last one it touched at zero, so
     * the next write can simply OR into the partial byte it starts in. The
     * places that rewind and overwrite -- storing a block plainly, or widening
     * a meta-block's length after the fact -- depend on that.
     */
    private static final class BitWriter {

        private byte[] data;
        private int position;

        BitWriter(int room) {
            this.data = new byte[Math.max(room, 64)];
        }

        int at() {
            return position;
        }

        private void room(int howManyBits) {
            int wanted = ((position + howManyBits) >> 3) + 9;
            if (wanted > data.length) {
                data = Arrays.copyOf(data, Math.max(wanted, data.length * 2));
            }
        }

        void write(int howManyBits, long bits) {
            if (howManyBits == 0) {
                return;
            }
            room(howManyBits);
            int at = position >> 3;
            int reserved = position & 7;
            long shifted = bits << reserved;
            data[at] |= (byte) shifted;
            at++;
            for (int left = howManyBits + reserved; left >= 9; left -= 8) {
                shifted >>>= 8;
                data[at++] = (byte) shifted;
            }
            data[at] = 0;
            position += howManyBits;
        }

        void writeBytes(byte[] source, int at, int howMany) {
            room((howMany + 1) << 3);
            System.arraycopy(source, at, data, position >> 3, howMany);
            position += howMany << 3;
            data[position >> 3] = 0;
        }

        void jumpToByteBoundary() {
            room(8);
            position = (position + 7) & ~7;
            data[position >> 3] = 0;
        }

        void rewindTo(int newPosition) {
            data[newPosition >> 3] &= (byte) ((1 << (newPosition & 7)) - 1);
            position = newPosition;
        }

        /** Overwrites bits already written, which is how a meta-block grows. */
        void updateBits(int howManyBits, int bits, int at) {
            int left = howManyBits;
            int value = bits;
            int writingAt = at;
            while (left > 0) {
                int whichByte = writingAt >> 3;
                int unchanged = writingAt & 7;
                int changed = Math.min(left, 8 - unchanged);
                int total = unchanged + changed;
                int mask = -(1 << total) | ((1 << unchanged) - 1);
                int keep = data[whichByte] & mask;
                int fresh = value & ((1 << changed) - 1);
                data[whichByte] = (byte) ((fresh << unchanged) | keep);
                left -= changed;
                value >>>= changed;
                writingAt += changed;
            }
        }

        byte[] written() {
            return Arrays.copyOf(data, position >> 3);
        }
    }
}
