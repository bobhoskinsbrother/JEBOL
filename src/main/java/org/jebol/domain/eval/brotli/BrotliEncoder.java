package org.jebol.domain.eval.brotli;

import org.jebol.domain.eval.*;

import java.util.Arrays;

final class BrotliEncoder {

    private BrotliEncoder() {
    }

    private static final int WINDOW_BITS = 22;
    private static final int COMMAND_SYMBOLS = 704;
    private static final long HASH_MULTIPLIER = 0x1E35A7BDL;
    private static final int MAX_DISTANCE = (1 << 18) - 16;
    private static final int FIRST_BLOCK_SIZE = 3 << 15;
    private static final int MERGE_BLOCK_SIZE = 1 << 16;
    private static final int INPUT_MARGIN_BYTES = 16;
    private static final int MIN_MATCH_LENGTH = 5;
    private static final int MIN_RATIO = 980;
    private static final int MAX_TABLE_SIZE = 1 << 15;

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

    private static int qualityFor(int level) {
        int asked = level == NOBODY_ASKED ? DEFAULT_QUALITY : level;
        return Integer.compareUnsigned(asked, HIGHEST_QUALITY) > 0
                ? HIGHEST_QUALITY
                : asked;
    }

    private static final int NOBODY_ASKED = -1;
    private static final int DEFAULT_QUALITY = 6;
    private static final int HIGHEST_QUALITY = 11;
    private static final int ONE_PASS_QUALITY = 0;
    private static final int TWO_PASS_QUALITY = 1;

    static byte[] encoded(byte[] source, int level) {
        int quality = qualityFor(level);
        if (quality == ONE_PASS_QUALITY) {
            return new Fragment(source).run();
        }
        if (quality == TWO_PASS_QUALITY) {
            return twoPass(source);
        }
        return BrotliGenericEncoder.encoded(source, quality);
    }

    private static byte[] twoPass(byte[] source) {
        BrotliBits writer = new BrotliBits(2 * source.length + 512);
        writer.write(4, ((WINDOW_BITS - 17) << 1) | 1);
        int at = 0;
        do {
            int fragmentSize = Math.min(source.length - at, 1 << WINDOW_BITS);
            boolean isLast = at + fragmentSize == source.length;
            if (fragmentSize == 0) {
                writer.write(1, 1);
                writer.write(1, 1);
                writer.jumpToByteBoundary();
            } else {
                BrotliTwoPassEncoder.writeOneFragment(
                        source, at, fragmentSize, isLast, writer);
            }
            at += fragmentSize;
        } while (at < source.length);
        return writer.written();
    }

    private static final class Fragment {

        private final byte[] input;
        private final BrotliBits writer;

        private final int[] commandDepth = new int[128];
        private final int[] commandBits = new int[128];
        private final int[] commandHistogram = new int[128];
        private final int[] literalDepth = new int[256];
        private final int[] literalBits = new int[256];
        private final int[] histogram = new int[256];
        private final BrotliCodes.Tree tree = new BrotliCodes.Tree();

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
            this.writer = new BrotliBits(2 * input.length + 512);
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

        private void compressOneFragment(int at, int size, boolean isLast) {
            int openedAt = writer.at();
            if (size == 0) {
                writer.write(1, 1);
                writer.write(1, 1);
                writer.jumpToByteBoundary();
                return;
            }
            prepareTableRoundingAnEvenWidthUpBecauseOnlyOddOnesWork(size);
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

        private void prepareTableRoundingAnEvenWidthUpBecauseOnlyOddOnesWork(
                int size) {
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
            literalRatio = buildAndStoreLiteralPrefixCodeAnsweringMillibitsPerByte(
                        from, blockSize);
            for (int each = 0; each + 7 < DEFAULT_COMMAND_CODE_BITS; each += 8) {
                writer.write(8, DEFAULT_COMMAND_CODE[each >> 3]);
            }
            writer.write(DEFAULT_COMMAND_CODE_BITS & 7,
                    DEFAULT_COMMAND_CODE[DEFAULT_COMMAND_CODE_BITS >> 3]);

            while (emitCommandsAnsweringWhetherThereIsMoreInput()) {
                metaBlockStart = from;
                blockSize = Math.min(fragmentEnd - from, FIRST_BLOCK_SIZE);
                totalBlockSize = blockSize;
                mlenAt = writer.at() + 3;
                storeMetaBlockHeader(blockSize, false);
                writer.write(13, 0);
                literalRatio = buildAndStoreLiteralPrefixCodeAnsweringMillibitsPerByte(
                        from, blockSize);
                buildAndStoreCommandPrefixCode();
            }
        }

        private boolean emitCommandsAnsweringWhetherThereIsMoreInput() {
            while (true) {
                System.arraycopy(COMMAND_HISTOGRAM_SEED, 0, commandHistogram, 0, 128);
                int end = from + blockSize;
                Match found =
                        scanForMatchesWideningTheStrideTheLongerNothingIsFound(end);
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

        private Match scanForMatchesWideningTheStrideTheLongerNothingIsFound(
                int end) {
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
                int width = BrotliCodes.log2Floor(tail) - 1;
                int prefix = tail >> width;
                int code = (width << 1) + prefix + 42;
                writer.write(commandDepth[code], commandBits[code]);
                writer.write(width, tail - ((long) prefix << width));
                commandHistogram[code]++;
            } else if (insertLength < 2114) {
                int tail = insertLength - 66;
                int width = BrotliCodes.log2Floor(tail);
                int code = width + 50;
                writer.write(commandDepth[code], commandBits[code]);
                writer.write(width, tail - (1L << width));
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
                int width = BrotliCodes.log2Floor(tail) - 1;
                int prefix = tail >> width;
                int code = (width << 1) + prefix + 20;
                writer.write(commandDepth[code], commandBits[code]);
                writer.write(width, tail - ((long) prefix << width));
                commandHistogram[code]++;
            } else if (copyLength < 2118) {
                int tail = copyLength - 70;
                int width = BrotliCodes.log2Floor(tail);
                int code = width + 28;
                writer.write(commandDepth[code], commandBits[code]);
                writer.write(width, tail - (1L << width));
                commandHistogram[code]++;
            } else {
                writer.write(commandDepth[39], commandBits[39]);
                writer.write(24, copyLength - 2118);
                commandHistogram[39]++;
            }
        }

        private void emitCopyLengthWithLastDistance(int copyLength) {
            if (copyLength < 12) {
                writer.write(commandDepth[copyLength - 4],
                        commandBits[copyLength - 4]);
                commandHistogram[copyLength - 4]++;
            } else if (copyLength < 72) {
                int tail = copyLength - 8;
                int width = BrotliCodes.log2Floor(tail) - 1;
                int prefix = tail >> width;
                int code = (width << 1) + prefix + 4;
                writer.write(commandDepth[code], commandBits[code]);
                writer.write(width, tail - ((long) prefix << width));
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
                int width = BrotliCodes.log2Floor(tail);
                int code = width + 28;
                writer.write(commandDepth[code], commandBits[code]);
                writer.write(width, tail - (1L << width));
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
            int width = BrotliCodes.log2Floor(shifted) - 1;
            int prefix = (shifted >> width) & 1;
            int offset = (2 + prefix) << width;
            int code = 2 * (width - 1) + prefix + 80;
            writer.write(commandDepth[code], commandBits[code]);
            writer.write(width, shifted - offset);
            commandHistogram[code]++;
        }

        private int buildAndStoreLiteralPrefixCodeAnsweringMillibitsPerByte(
                int at, int size) {
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
            BrotliCodes.buildAndStoreHuffmanTreeFast(tree, histogram, total, 8,
                    literalDepth, literalBits, writer);
            long ratio = 0;
            for (int each = 0; each < 256; each++) {
                if (histogram[each] != 0) {
                    ratio += (long) histogram[each] * literalDepth[each];
                }
            }
            return (int) ((ratio * 125) / total);
        }

        private boolean shouldMergeBlock(int at, int size) {
            Arrays.fill(histogram, 0);
            int sampleRate = 43;
            for (int each = 0; each < size; each += sampleRate) {
                histogram[input[at + each] & 0xFF]++;
            }
            int total = (size + sampleRate - 1) / sampleRate;
            double room = (BrotliCodes.fastLog2(total) + 0.5) * total + 200;
            for (int each = 0; each < 256; each++) {
                room -= histogram[each] * (literalDepth[each] + BrotliCodes.fastLog2(histogram[each]));
            }
            return room >= 0.0;
        }

        private void buildAndStoreCommandPrefixCode() {
            int[] spreadDepth = new int[COMMAND_SYMBOLS];
            int[] spreadBits = new int[64];

            tree.buildBreakingTiesByPuttingTheLaterSymbolFirst(
                    commandHistogram, 0, 64, 15, commandDepth, 0);
            tree.buildBreakingTiesByPuttingTheLaterSymbolFirst(
                    commandHistogram, 64, 64, 14, commandDepth, 64);

            System.arraycopy(commandDepth, 0, spreadDepth, 0, 24);
            System.arraycopy(commandDepth, 40, spreadDepth, 24, 8);
            System.arraycopy(commandDepth, 24, spreadDepth, 32, 8);
            System.arraycopy(commandDepth, 48, spreadDepth, 40, 8);
            System.arraycopy(commandDepth, 32, spreadDepth, 48, 8);
            System.arraycopy(commandDepth, 56, spreadDepth, 56, 8);
            BrotliCodes.convertBitDepthsToSymbols(spreadDepth, 0, 64, spreadBits, 0);
            System.arraycopy(spreadBits, 0, commandBits, 0, 24);
            System.arraycopy(spreadBits, 32, commandBits, 24, 8);
            System.arraycopy(spreadBits, 48, commandBits, 32, 8);
            System.arraycopy(spreadBits, 24, commandBits, 40, 8);
            System.arraycopy(spreadBits, 40, commandBits, 48, 8);
            System.arraycopy(spreadBits, 56, commandBits, 56, 8);
            BrotliCodes.convertBitDepthsToSymbols(commandDepth, 64, 64, commandBits, 64);

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
            BrotliCodes.storeHuffmanTree(tree, spreadDepth, 0, COMMAND_SYMBOLS, writer);
            BrotliCodes.storeHuffmanTree(tree, commandDepth, 64, 64, writer);
        }

    }

}
