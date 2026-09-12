package org.jebol.domain.eval.brotli;

import java.util.Arrays;

final class BrotliTwoPassEncoder {

    private BrotliTwoPassEncoder() {
    }

    private static final int BLOCK_SIZE = 1 << 17;
    private static final int MAX_TABLE_SIZE = 1 << 17;
    private static final int MAX_DISTANCE = (1 << 18) - 16;
    private static final int INPUT_MARGIN_BYTES = 16;
    private static final long HASH_MULTIPLIER = 0x1E35A7BDL;

    private static final double LITERALS_PER_BYTE_BELOW_WHICH_MATCHES_PAID = 0.98;
    private static final int ONE_BYTE_IN_THIS_MANY_IS_SAMPLED = 43;

    private static final int[] EXTRA_BITS = {
            0, 0, 0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5,
            6, 7, 8, 9, 10, 12, 14, 24, 0, 0, 0, 0, 0, 0, 0, 0,
            1, 1, 2, 2, 3, 3, 4, 4, 0, 0, 0, 0, 0, 0, 0, 0,
            1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 7, 8, 9, 10, 24,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8,
            9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 14, 14, 15, 15, 16, 16,
            17, 17, 18, 18, 19, 19, 20, 20, 21, 21, 22, 22, 23, 23, 24, 24,
    };

    private static final int[] INSERT_OFFSET = {
            0, 1, 2, 3, 4, 5, 6, 8, 10, 14, 18, 26,
            34, 50, 66, 98, 130, 194, 322, 578, 1090, 2114, 6210, 22594,
    };

    static void writeOneFragment(byte[] source, int at, int size,
            boolean isLast, BrotliBits into) {

        new Fragment(source, into).writeFragment(at, size, isLast);
    }

    private static final class Fragment {

        private final byte[] input;
        private final BrotliBits writer;
        private final BrotliCodes.Tree tree = new BrotliCodes.Tree();

        private final int[] literalDepth = new int[256];
        private final int[] literalBits = new int[256];
        private final int[] literalHistogram = new int[256];
        private final int[] commandDepth = new int[128];
        private final int[] commandBits = new int[128];
        private final int[] commandHistogram = new int[128];

        private int[] table = new int[0];
        private int tableBits;
        private int minMatch;

        private int[] commands = new int[0];
        private byte[] literals = new byte[0];
        private int commandCount;
        private int literalCount;

        private Fragment(byte[] input, BrotliBits writer) {
            this.input = input;
            this.writer = writer;
        }

        private void writeFragment(int at, int size, boolean isLast) {
            int openedAt = writer.at();
            prepareTable(size);
            int from = at;
            int left = size;
            while (left > 0) {
                int blockSize = Math.min(left, BLOCK_SIZE);
                createCommands(at, from, blockSize, at + size);
                if (worthCompressing(from, blockSize)) {
                    storeMetaBlockHeader(blockSize, false);
                    writer.write(13, 0);
                    storeCommands();
                } else {
                    storeUncompressed(from, blockSize);
                }
                from += blockSize;
                left -= blockSize;
            }
            if (writer.at() - openedAt > 31 + ((long) size << 3)) {
                writer.rewindTo(openedAt);
                storeUncompressed(at, size);
            }
            if (isLast) {
                writer.write(1, 1);
                writer.write(1, 1);
                writer.jumpToByteBoundary();
            }
        }

        private void prepareTable(int size) {
            int wide = 256;
            while (wide < MAX_TABLE_SIZE && wide < size) {
                wide <<= 1;
            }
            tableBits = Integer.numberOfTrailingZeros(wide);
            minMatch = tableBits <= 15 ? 4 : 6;
            if (table.length != wide) {
                table = new int[wide];
            } else {
                Arrays.fill(table, 0);
            }
            int room = Math.min(size, BLOCK_SIZE) + 16;
            if (commands.length < room) {
                commands = new int[room];
                literals = new byte[room];
            }
        }

        private long loadEight(int at) {
            long value = 0;
            for (int each = 0; each < 8; each++) {
                int index = at + each;
                long octet = index >= 0 && index < input.length
                        ? input[index] & 0xFFL : 0L;
                value |= octet << (each * 8);
            }
            return value;
        }

        private int hashAt(int at) {
            return hashOfShifted(loadEight(at), 0);
        }

        private int hashOfShifted(long window, int offset) {
            long hashed = ((window >>> (8 * offset)) << ((8 - minMatch) * 8))
                    * HASH_MULTIPLIER;
            return (int) (hashed >>> (64 - tableBits));
        }

        private boolean isMatch(int one, int other) {
            if (other < 0 || one + minMatch > input.length) {
                return false;
            }
            for (int each = 0; each < 4; each++) {
                if (input[one + each] != input[other + each]) {
                    return false;
                }
            }
            if (minMatch == 4) {
                return true;
            }
            return input[one + 4] == input[other + 4]
                    && input[one + 5] == input[other + 5];
        }

        private int matchLength(int one, int other, int limit) {
            int matched = 0;
            while (matched < limit
                    && input[one + matched] == input[other + matched]) {
                matched++;
            }
            return matched;
        }

        private void createCommands(int baseAt, int from, int blockSize, int fragmentEnd) {
            commandCount = 0;
            literalCount = 0;
            int at = from;
            int end = from + blockSize;
            int nextEmit = from;
            int lastDistance = -1;

            if (blockSize >= INPUT_MARGIN_BYTES) {
                int limit = from + Math.min(blockSize - minMatch,
                        fragmentEnd - from - INPUT_MARGIN_BYTES);
                at++;
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
                            nextEmit = emitRemainder(nextEmit, end);
                            return;
                        }
                        nextHash = hashAt(nextAt);
                        candidate = at - lastDistance;
                        if (candidate < at && isMatch(at, candidate)) {
                            table[hash] = at - baseAt;
                            break;
                        }
                        candidate = baseAt + table[hash];
                        table[hash] = at - baseAt;
                        if (isMatch(at, candidate) && at - candidate <= MAX_DISTANCE) {
                            break;
                        }
                    }

                    int base = at;
                    int length = minMatch + matchLength(candidate + minMatch,
                            at + minMatch, end - at - minMatch);
                    int distance = base - candidate;
                    int insert = base - nextEmit;
                    at += length;
                    emitInsertLength(insert);
                    System.arraycopy(input, nextEmit, literals, literalCount, insert);
                    literalCount += insert;
                    if (distance == lastDistance) {
                        commands[commandCount++] = 64;
                    } else {
                        emitDistance(distance);
                        lastDistance = distance;
                    }
                    emitCopyLengthWithLastDistance(length);
                    nextEmit = at;
                    if (at >= limit) {
                        nextEmit = emitRemainder(nextEmit, end);
                        return;
                    }
                    candidate = baseAt
                            + rehashAfterAnInsertFilingTheThirdPositionUnderTheFirstsHash(
                                    at, baseAt);

                    while (at - candidate <= MAX_DISTANCE && isMatch(at, candidate)) {
                        base = at;
                        length = minMatch + matchLength(candidate + minMatch,
                                at + minMatch, end - at - minMatch);
                        at += length;
                        lastDistance = base - candidate;
                        emitCopyLength(length);
                        emitDistance(lastDistance);
                        nextEmit = at;
                        if (at >= limit) {
                            nextEmit = emitRemainder(nextEmit, end);
                            return;
                        }
                        candidate = baseAt + rehashAfterACopy(at, baseAt);
                    }
                    at++;
                    nextHash = hashAt(at);
                }
            }
            emitRemainder(nextEmit, end);
        }

        private int emitRemainder(int nextEmit, int end) {
            if (nextEmit < end) {
                int insert = end - nextEmit;
                emitInsertLength(insert);
                System.arraycopy(input, nextEmit, literals, literalCount, insert);
                literalCount += insert;
            }
            return end;
        }

        /** The repeated nought is the C's, and correcting it changes the bytes. */
        private int rehashAfterAnInsertFilingTheThirdPositionUnderTheFirstsHash(
                int at, int baseAt) {
            if (minMatch != 4) {
                return rehashSixBytes(at, baseAt);
            }
            long window = loadEight(at - 3);
            int current = hashOfShifted(window, 3);
            table[hashOfShifted(window, 0)] = at - baseAt - 3;
            table[hashOfShifted(window, 1)] = at - baseAt - 2;
            table[hashOfShifted(window, 0)] = at - baseAt - 1;
            int candidate = table[current];
            table[current] = at - baseAt;
            return candidate;
        }

        private int rehashAfterACopy(int at, int baseAt) {
            if (minMatch != 4) {
                return rehashSixBytes(at, baseAt);
            }
            long window = loadEight(at - 3);
            int current = hashOfShifted(window, 3);
            table[hashOfShifted(window, 0)] = at - baseAt - 3;
            table[hashOfShifted(window, 1)] = at - baseAt - 2;
            table[hashOfShifted(window, 2)] = at - baseAt - 1;
            int candidate = table[current];
            table[current] = at - baseAt;
            return candidate;
        }

        private int rehashSixBytes(int at, int baseAt) {
            long window = loadEight(at - 5);
            table[hashOfShifted(window, 0)] = at - baseAt - 5;
            table[hashOfShifted(window, 1)] = at - baseAt - 4;
            table[hashOfShifted(window, 2)] = at - baseAt - 3;
            long closer = loadEight(at - 2);
            int current = hashOfShifted(closer, 2);
            table[hashOfShifted(closer, 0)] = at - baseAt - 2;
            table[hashOfShifted(closer, 1)] = at - baseAt - 1;
            int candidate = table[current];
            table[current] = at - baseAt;
            return candidate;
        }

        private void emitInsertLength(int insertLength) {
            if (insertLength < 6) {
                commands[commandCount++] = insertLength;
            } else if (insertLength < 130) {
                int tail = insertLength - 2;
                int width = BrotliCodes.log2Floor(tail) - 1;
                int prefix = tail >> width;
                int code = (width << 1) + prefix + 2;
                commands[commandCount++] = code | ((tail - (prefix << width)) << 8);
            } else if (insertLength < 2114) {
                int tail = insertLength - 66;
                int width = BrotliCodes.log2Floor(tail);
                commands[commandCount++] =
                        (width + 10) | ((tail - (1 << width)) << 8);
            } else if (insertLength < 6210) {
                commands[commandCount++] = 21 | ((insertLength - 2114) << 8);
            } else if (insertLength < 22594) {
                commands[commandCount++] = 22 | ((insertLength - 6210) << 8);
            } else {
                commands[commandCount++] = 23 | ((insertLength - 22594) << 8);
            }
        }

        private void emitCopyLength(int copyLength) {
            if (copyLength < 10) {
                commands[commandCount++] = copyLength + 38;
            } else if (copyLength < 134) {
                int tail = copyLength - 6;
                int width = BrotliCodes.log2Floor(tail) - 1;
                int prefix = tail >> width;
                int code = (width << 1) + prefix + 44;
                commands[commandCount++] = code | ((tail - (prefix << width)) << 8);
            } else if (copyLength < 2118) {
                int tail = copyLength - 70;
                int width = BrotliCodes.log2Floor(tail);
                commands[commandCount++] =
                        (width + 52) | ((tail - (1 << width)) << 8);
            } else {
                commands[commandCount++] = 63 | ((copyLength - 2118) << 8);
            }
        }

        private void emitCopyLengthWithLastDistance(int copyLength) {
            if (copyLength < 12) {
                commands[commandCount++] = copyLength + 20;
            } else if (copyLength < 72) {
                int tail = copyLength - 8;
                int width = BrotliCodes.log2Floor(tail) - 1;
                int prefix = tail >> width;
                int code = (width << 1) + prefix + 28;
                commands[commandCount++] = code | ((tail - (prefix << width)) << 8);
            } else if (copyLength < 136) {
                int tail = copyLength - 8;
                commands[commandCount++] = ((tail >> 5) + 54) | ((tail & 31) << 8);
                commands[commandCount++] = 64;
            } else if (copyLength < 2120) {
                int tail = copyLength - 72;
                int width = BrotliCodes.log2Floor(tail);
                commands[commandCount++] =
                        (width + 52) | ((tail - (1 << width)) << 8);
                commands[commandCount++] = 64;
            } else {
                commands[commandCount++] = 63 | ((copyLength - 2120) << 8);
                commands[commandCount++] = 64;
            }
        }

        private void emitDistance(int distance) {
            int shifted = distance + 3;
            int width = BrotliCodes.log2Floor(shifted) - 1;
            int prefix = (shifted >> width) & 1;
            int offset = (2 + prefix) << width;
            int code = 2 * (width - 1) + prefix + 80;
            commands[commandCount++] = code | ((shifted - offset) << 8);
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

        private void storeUncompressed(int at, int size) {
            storeMetaBlockHeader(size, true);
            writer.jumpToByteBoundary();
            writer.writeBytes(input, at, size);
        }

        private boolean worthCompressing(int at, int size) {
            if (theMatchesCoveredEnoughToPayOutright(size)) {
                return true;
            }
            double mostTheSampledBytesMayCost = (double) size * 8
                    * LITERALS_PER_BYTE_BELOW_WHICH_MATCHES_PAID
                    / ONE_BYTE_IN_THIS_MANY_IS_SAMPLED;
            Arrays.fill(literalHistogram, 0);
            for (int each = 0; each < size;
                    each += ONE_BYTE_IN_THIS_MANY_IS_SAMPLED) {
                literalHistogram[input[at + each] & 0xFF]++;
            }
            return BrotliCodes.bitsEntropyFlooredAtOneBitPerLiteral(literalHistogram, 256)
                    < mostTheSampledBytesMayCost;
        }

        private boolean theMatchesCoveredEnoughToPayOutright(int size) {
            return (double) literalCount
                    < LITERALS_PER_BYTE_BELOW_WHICH_MATCHES_PAID * (double) size;
        }

        private void storeCommands() {
            Arrays.fill(literalHistogram, 0);
            Arrays.fill(commandDepth, 0);
            Arrays.fill(commandBits, 0);
            Arrays.fill(commandHistogram, 0);
            for (int each = 0; each < literalCount; each++) {
                literalHistogram[literals[each] & 0xFF]++;
            }
            BrotliCodes.buildAndStoreHuffmanTreeFast(tree, literalHistogram,
                    literalCount, 8, literalDepth, literalBits, writer);

            for (int each = 0; each < commandCount; each++) {
                commandHistogram[commands[each] & 0xFF]++;
            }
            commandHistogram[1]++;
            commandHistogram[2]++;
            commandHistogram[64]++;
            commandHistogram[84]++;
            buildAndStoreCommandPrefixCode();

            int literalAt = 0;
            for (int each = 0; each < commandCount; each++) {
                int command = commands[each];
                int code = command & 0xFF;
                int extra = command >>> 8;
                writer.write(commandDepth[code], commandBits[code]);
                writer.write(EXTRA_BITS[code], extra);
                if (code < 24) {
                    int insert = INSERT_OFFSET[code] + extra;
                    for (int written = 0; written < insert; written++) {
                        int literal = literals[literalAt++] & 0xFF;
                        writer.write(literalDepth[literal], literalBits[literal]);
                    }
                }
            }
        }

        private void buildAndStoreCommandPrefixCode() {
            int[] spreadDepth = new int[BrotliCodes.COMMAND_SYMBOLS];
            int[] spreadBits = new int[64];

            tree.buildBreakingTiesByPuttingTheLaterSymbolFirst(
                    commandHistogram, 0, 64, 15, commandDepth, 0);
            tree.buildBreakingTiesByPuttingTheLaterSymbolFirst(
                    commandHistogram, 64, 64, 14, commandDepth, 64);

            System.arraycopy(commandDepth, 24, spreadDepth, 0, 24);
            System.arraycopy(commandDepth, 0, spreadDepth, 24, 8);
            System.arraycopy(commandDepth, 48, spreadDepth, 32, 8);
            System.arraycopy(commandDepth, 8, spreadDepth, 40, 8);
            System.arraycopy(commandDepth, 56, spreadDepth, 48, 8);
            System.arraycopy(commandDepth, 16, spreadDepth, 56, 8);
            BrotliCodes.convertBitDepthsToSymbols(spreadDepth, 0, 64, spreadBits, 0);
            System.arraycopy(spreadBits, 24, commandBits, 0, 8);
            System.arraycopy(spreadBits, 40, commandBits, 8, 8);
            System.arraycopy(spreadBits, 56, commandBits, 16, 8);
            System.arraycopy(spreadBits, 0, commandBits, 24, 24);
            System.arraycopy(spreadBits, 32, commandBits, 48, 8);
            System.arraycopy(spreadBits, 48, commandBits, 56, 8);
            BrotliCodes.convertBitDepthsToSymbols(commandDepth, 64, 64, commandBits, 64);

            Arrays.fill(spreadDepth, 0);
            System.arraycopy(commandDepth, 24, spreadDepth, 0, 8);
            System.arraycopy(commandDepth, 32, spreadDepth, 64, 8);
            System.arraycopy(commandDepth, 40, spreadDepth, 128, 8);
            System.arraycopy(commandDepth, 48, spreadDepth, 192, 8);
            System.arraycopy(commandDepth, 56, spreadDepth, 384, 8);
            for (int each = 0; each < 8; each++) {
                spreadDepth[128 + 8 * each] = commandDepth[each];
                spreadDepth[256 + 8 * each] = commandDepth[8 + each];
                spreadDepth[448 + 8 * each] = commandDepth[16 + each];
            }
            BrotliCodes.storeHuffmanTree(tree, spreadDepth, 0,
                    BrotliCodes.COMMAND_SYMBOLS, writer);
            BrotliCodes.storeHuffmanTree(tree, commandDepth, 64, 64, writer);
        }
    }
}
