package org.jebol.domain.eval.brotli;

import java.util.Arrays;

final class BrotliMetaBlockWriter {

    private static final int LITERAL_SYMBOLS = 256;
    private static final int LITERAL_CONTEXT_BITS = 6;
    private static final int DISTANCE_CONTEXT_BITS = 2;
    private static final int BLOCK_LENGTH_SYMBOLS = 26;
    private static final int SYMBOL_BITS = 9;
    private static final int LONGEST_RUN_CODE = 6;

    private static final int[] BLOCK_LENGTH_FROM = {
            1, 5, 9, 13, 17, 25, 33, 41, 49, 65, 81, 97, 113,
            145, 177, 209, 241, 305, 369, 497, 753, 1265, 2289, 4337, 8433, 16625,
    };
    private static final int[] BLOCK_LENGTH_EXTRA_BITS = {
            2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5,
            5, 5, 5, 6, 6, 7, 8, 9, 10, 11, 12, 13, 24,
    };

    private BrotliMetaBlockWriter() {
    }

    static void writeAnEmptyLastMetaBlock(BrotliBits into) {
        into.write(2, 3);
        into.jumpToByteBoundary();
    }

    private static int blockLengthCodeFor(int length) {
        int code = length >= 177 ? (length >= 753 ? 20 : 14) : (length >= 41 ? 7 : 0);
        while (code < BLOCK_LENGTH_SYMBOLS - 1
                && length >= BLOCK_LENGTH_FROM[code + 1]) {
            code++;
        }
        return code;
    }

    private static void writeTheLength(int length, BrotliBits into) {
        int bitsNeeded = length == 1 ? 1 : BrotliCodes.log2Floor(length - 1) + 1;
        int nibbles = (bitsNeeded < 16 ? 16 : bitsNeeded + 3) / 4;
        into.write(2, nibbles - 4);
        into.write(nibbles * 4, length - 1);
    }

    private static void writeCompressedHeader(boolean isLast, int length,
            BrotliBits into) {

        into.write(1, isLast ? 1 : 0);
        if (isLast) {
            into.write(1, 0);
        }
        writeTheLength(length, into);
        if (!isLast) {
            into.write(1, 0);
        }
    }

    private static void writeNumberNoLargerThanAByte(int number, BrotliBits into) {
        if (number == 0) {
            into.write(1, 0);
            return;
        }
        int width = BrotliCodes.log2Floor(number);
        into.write(1, 1);
        into.write(3, width);
        into.write(width, number - (1L << width));
    }

    static void writeUncompressed(byte[] data, int mask, int from, int length,
            boolean isLast, BrotliBits into) {

        into.write(1, 0);
        writeTheLength(length, into);
        into.write(1, 1);
        into.jumpToByteBoundary();

        int at = from & mask;
        int left = length;
        if (at + left > mask + 1) {
            int upToTheWrap = mask + 1 - at;
            into.writeBytes(data, at, upToTheWrap);
            left -= upToTheWrap;
            at = 0;
        }
        into.writeBytes(data, at, left);

        if (isLast) {
            into.write(1, 1);
            into.write(1, 1);
            into.jumpToByteBoundary();
        }
    }

    private static void writeATreeFor(int[] histogram, int histogramLength,
            int alphabetSize, BrotliCodes.Tree tree, int[] depth, int depthAt,
            int[] bits, int bitsAt, BrotliBits into) {

        int used = 0;
        int[] firstFour = new int[4];
        for (int symbol = 0; symbol < histogramLength; symbol++) {
            if (histogram[symbol] != 0) {
                if (used < 4) {
                    firstFour[used] = symbol;
                } else if (used > 4) {
                    break;
                }
                used++;
            }
        }

        int widthOfASymbol = 0;
        for (int left = alphabetSize - 1; left != 0; left >>= 1) {
            widthOfASymbol++;
        }

        if (used <= 1) {
            into.write(4, 1);
            into.write(widthOfASymbol, firstFour[0]);
            depth[depthAt + firstFour[0]] = 0;
            bits[bitsAt + firstFour[0]] = 0;
            return;
        }

        Arrays.fill(depth, depthAt, depthAt + histogramLength, 0);
        tree.buildBreakingTiesByPuttingTheLaterSymbolFirst(
                histogram, 0, histogramLength, 15, depth, depthAt);
        BrotliCodes.convertBitDepthsToSymbols(depth, depthAt, histogramLength,
                bits, bitsAt);

        if (used <= 4) {
            writeTheFewSymbolsOutright(depth, depthAt, firstFour, used,
                    widthOfASymbol, into);
        } else {
            BrotliCodes.storeHuffmanTree(tree, depth, depthAt, histogramLength, into);
        }
    }

    private static void writeTheFewSymbolsOutright(int[] depth, int depthAt,
            int[] symbols, int howMany, int widthOfASymbol, BrotliBits into) {

        into.write(2, 1);
        into.write(2, howMany - 1);
        for (int each = 0; each < howMany; each++) {
            for (int other = each + 1; other < howMany; other++) {
                if (depth[depthAt + symbols[other]] < depth[depthAt + symbols[each]]) {
                    int swapped = symbols[other];
                    symbols[other] = symbols[each];
                    symbols[each] = swapped;
                }
            }
        }
        for (int each = 0; each < howMany; each++) {
            into.write(widthOfASymbol, symbols[each]);
        }
        if (howMany == 4) {
            into.write(1, depth[depthAt + symbols[0]] == 1 ? 1 : 0);
        }
    }

    private static void writeContextMap(int[] contextMap, int howManyHistograms,
            BrotliCodes.Tree tree, BrotliBits into) {

        writeNumberNoLargerThanAByte(howManyHistograms - 1, into);
        if (howManyHistograms == 1) {
            return;
        }
        int[] recentlyUsedFirst = movedToFront(contextMap);
        RunCoded coded = codeRunsOfZeros(recentlyUsedFirst, LONGEST_RUN_CODE);
        int longestRunCode = coded.longestRunCode();
        int howManySymbols = coded.howManySymbols();

        int[] histogram = new int[howManyHistograms + longestRunCode];
        for (int each = 0; each < howManySymbols; each++) {
            histogram[recentlyUsedFirst[each] & ((1 << SYMBOL_BITS) - 1)]++;
        }
        into.write(1, longestRunCode > 0 ? 1 : 0);
        if (longestRunCode > 0) {
            into.write(4, longestRunCode - 1);
        }
        int alphabetSize = howManyHistograms + longestRunCode;
        int[] depth = new int[alphabetSize];
        int[] bits = new int[alphabetSize];
        writeATreeFor(histogram, alphabetSize, alphabetSize, tree, depth, 0,
                bits, 0, into);
        for (int each = 0; each < howManySymbols; each++) {
            int symbol = recentlyUsedFirst[each] & ((1 << SYMBOL_BITS) - 1);
            into.write(depth[symbol], bits[symbol]);
            if (symbol > 0 && symbol <= longestRunCode) {
                into.write(symbol, recentlyUsedFirst[each] >>> SYMBOL_BITS);
            }
        }
        into.write(1, 1);
    }

    private static int[] movedToFront(int[] contextMap) {
        int[] howLongAgo = new int[contextMap.length];
        if (contextMap.length == 0) {
            return howLongAgo;
        }
        int highest = 0;
        for (int each : contextMap) {
            highest = Math.max(highest, each);
        }
        int[] mostRecentFirst = new int[highest + 1];
        for (int each = 0; each <= highest; each++) {
            mostRecentFirst[each] = each;
        }
        for (int each = 0; each < contextMap.length; each++) {
            int found = 0;
            while (found <= highest && mostRecentFirst[found] != contextMap[each]) {
                found++;
            }
            howLongAgo[each] = found;
            int wanted = mostRecentFirst[found];
            System.arraycopy(mostRecentFirst, 0, mostRecentFirst, 1, found);
            mostRecentFirst[0] = wanted;
        }
        return howLongAgo;
    }

    private record RunCoded(int howManySymbols, int longestRunCode) {
    }

    private static RunCoded codeRunsOfZeros(int[] values, int mostBitsARunMayUse) {
        int longestRun = 0;
        for (int at = 0; at < values.length; ) {
            int run = 0;
            while (at < values.length && values[at] != 0) {
                at++;
            }
            while (at < values.length && values[at] == 0) {
                at++;
                run++;
            }
            longestRun = Math.max(longestRun, run);
        }
        int longestCode = longestRun > 0 ? BrotliCodes.log2Floor(longestRun) : 0;
        longestCode = Math.min(longestCode, mostBitsARunMayUse);

        int written = 0;
        for (int at = 0; at < values.length; ) {
            if (values[at] != 0) {
                values[written++] = values[at++] + longestCode;
                continue;
            }
            int run = 1;
            for (int ahead = at + 1; ahead < values.length && values[ahead] == 0;
                    ahead++) {
                run++;
            }
            at += run;
            while (run != 0) {
                if (run < (2 << longestCode)) {
                    int code = BrotliCodes.log2Floor(run);
                    values[written++] = code + ((run - (1 << code)) << SYMBOL_BITS);
                    break;
                }
                int extra = (1 << longestCode) - 1;
                values[written++] = longestCode + (extra << SYMBOL_BITS);
                run -= (2 << longestCode) - 1;
            }
        }
        return new RunCoded(written, longestCode);
    }

    private static void writeTrivialContextMap(int howManyTypes, int contextBits,
            BrotliCodes.Tree tree, BrotliBits into) {

        writeNumberNoLargerThanAByte(howManyTypes - 1, into);
        if (howManyTypes <= 1) {
            return;
        }
        int runCode = contextBits - 1;
        int runBits = (1 << runCode) - 1;
        int alphabetSize = howManyTypes + runCode;
        int[] histogram = new int[alphabetSize];
        into.write(1, 1);
        into.write(4, runCode - 1);
        histogram[runCode] = howManyTypes;
        histogram[0] = 1;
        for (int each = contextBits; each < alphabetSize; each++) {
            histogram[each] = 1;
        }
        int[] depth = new int[alphabetSize];
        int[] bits = new int[alphabetSize];
        writeATreeFor(histogram, alphabetSize, alphabetSize, tree, depth, 0,
                bits, 0, into);
        for (int type = 0; type < howManyTypes; type++) {
            int code = type == 0 ? 0 : type + contextBits - 1;
            into.write(depth[code], bits[code]);
            into.write(depth[runCode], bits[runCode]);
            into.write(runCode, runBits);
        }
        into.write(1, 1);
    }

    private static final class OneAlphabet {

        private final int alphabetLength;
        private final int howManyTypes;
        private final BrotliBlockSplit split;

        private int lastType = 1;
        private int typeBeforeLast;
        private int[] typeDepth = new int[0];
        private int[] typeBits = new int[0];
        private int[] lengthDepth = new int[0];
        private int[] lengthBits = new int[0];

        private int whichBlock;
        private int leftInTheBlock;

        private int whereTheCurrentBlockTypesCodeStarts;
        private int[] depths = new int[0];
        private int[] bits = new int[0];

        OneAlphabet(int alphabetLength, BrotliBlockSplit split) {
            this.alphabetLength = alphabetLength;
            this.howManyTypes = split.howManyTypes();
            this.split = split;
            this.leftInTheBlock =
                    split.howManyBlocks() == 0 ? 0 : split.lengthAt(0);
        }

        private int nextTypeCode(int type) {
            int code = type == lastType + 1 ? 1 : type == typeBeforeLast ? 0 : type + 2;
            typeBeforeLast = lastType;
            lastType = type;
            return code;
        }

        void writeTheSwitchingCode(BrotliCodes.Tree tree, BrotliBits into) {
            int[] typeHistogram = new int[howManyTypes + 2];
            int[] lengthHistogram = new int[BLOCK_LENGTH_SYMBOLS];
            OneAlphabet counting = new OneAlphabet(alphabetLength, split);
            for (int block = 0; block < split.howManyBlocks(); block++) {
                int code = counting.nextTypeCode(split.typeAt(block));
                if (block != 0) {
                    typeHistogram[code]++;
                }
                lengthHistogram[blockLengthCodeFor(split.lengthAt(block))]++;
            }

            writeNumberNoLargerThanAByte(howManyTypes - 1, into);
            if (howManyTypes <= 1) {
                return;
            }
            typeDepth = new int[howManyTypes + 2];
            typeBits = new int[howManyTypes + 2];
            lengthDepth = new int[BLOCK_LENGTH_SYMBOLS];
            lengthBits = new int[BLOCK_LENGTH_SYMBOLS];
            writeATreeFor(typeHistogram, howManyTypes + 2, howManyTypes + 2,
                    tree, typeDepth, 0, typeBits, 0, into);
            writeATreeFor(lengthHistogram, BLOCK_LENGTH_SYMBOLS,
                    BLOCK_LENGTH_SYMBOLS, tree, lengthDepth, 0, lengthBits, 0,
                    into);
            writeASwitch(split.lengthAt(0), split.typeAt(0), true, into);
        }

        private void writeASwitch(int blockLength, int blockType,
                boolean isTheFirst, BrotliBits into) {

            int typeCode = nextTypeCode(blockType);
            if (!isTheFirst) {
                into.write(typeDepth[typeCode], typeBits[typeCode]);
            }
            int lengthCode = blockLengthCodeFor(blockLength);
            into.write(lengthDepth[lengthCode], lengthBits[lengthCode]);
            into.write(BLOCK_LENGTH_EXTRA_BITS[lengthCode],
                    blockLength - BLOCK_LENGTH_FROM[lengthCode]);
        }

        void writeTheCodes(BrotliHistogram[] histograms, int howMany,
                           int alphabetSize, BrotliCodes.Tree tree, BrotliBits into) {

            depths = new int[howMany * alphabetLength];
            bits = new int[howMany * alphabetLength];
            for (int which = 0; which < howMany; which++) {
                int at = which * alphabetLength;
                writeATreeFor(histograms[which].counts(), alphabetLength,
                        alphabetSize, tree, depths, at, bits, at, into);
            }
        }

        private int startTheNextBlockIfThisOneRanOut(BrotliBits into) {
            if (leftInTheBlock != 0) {
                return -1;
            }
            whichBlock++;
            int length = split.lengthAt(whichBlock);
            int type = split.typeAt(whichBlock);
            leftInTheBlock = length;
            writeASwitch(length, type, false, into);
            return type;
        }

        void write(int symbol, BrotliBits into) {
            int newType = startTheNextBlockIfThisOneRanOut(into);
            if (newType >= 0) {
                whereTheCurrentBlockTypesCodeStarts = newType * alphabetLength;
            }
            leftInTheBlock--;
            into.write(depths[whereTheCurrentBlockTypesCodeStarts + symbol],
                    bits[whereTheCurrentBlockTypesCodeStarts + symbol]);
        }

        void writeInContext(int symbol, int context, int[] contextMap,
                int contextBits, BrotliBits into) {

            int newType = startTheNextBlockIfThisOneRanOut(into);
            if (newType >= 0) {
                whereTheCurrentBlockTypesCodeStarts = newType << contextBits;
            }
            leftInTheBlock--;
            int whichCode =
                    contextMap[whereTheCurrentBlockTypesCodeStarts + context];
            int at = whichCode * alphabetLength + symbol;
            into.write(depths[at], bits[at]);
        }
    }

    private static void writeTheExtraBitsOfALength(BrotliCommand commands,
                                                   int which, BrotliBits into) {

        int copyLengthCode = commands.copyLengthCodeAt(which);
        int insertCode = BrotliCommand.insertLengthCode(
                commands.insertLengthAt(which));
        int copyCode = BrotliCommand.copyLengthCode(copyLengthCode);
        int insertExtraBits = BrotliCommand.insertExtra(insertCode);
        long insertExtra = commands.insertLengthAt(which)
                - BrotliCommand.insertBase(insertCode);
        long copyExtra = copyLengthCode - BrotliCommand.copyBase(copyCode);
        into.write(insertExtraBits + BrotliCommand.copyExtra(copyCode),
                (copyExtra << insertExtraBits) | insertExtra);
    }

    static void writeTheFullThing(byte[] data, int mask, int from, int length,
            int previousByte, int theByteBeforeThat, boolean isLast,
            BrotliDistances distances, BrotliCommand commands,
            BrotliMetaBlockSplit split, int contextMode, BrotliBits into) {

        writeCompressedHeader(isLast, length, into);
        BrotliCodes.Tree tree = new BrotliCodes.Tree();

        OneAlphabet literals = new OneAlphabet(LITERAL_SYMBOLS, split.literals);
        OneAlphabet commandSymbols = new OneAlphabet(
                BrotliCodes.COMMAND_SYMBOLS, split.commands);
        OneAlphabet distanceSymbols = new OneAlphabet(
                distances.alphabetSize(), split.distances);

        literals.writeTheSwitchingCode(tree, into);
        commandSymbols.writeTheSwitchingCode(tree, into);
        distanceSymbols.writeTheSwitchingCode(tree, into);

        into.write(2, distances.postfixBits());
        into.write(4, distances.directCodes() >> distances.postfixBits());
        for (int type = 0; type < split.literals.howManyTypes(); type++) {
            into.write(2, contextMode);
        }

        if (split.literalContextMap.length == 0) {
            writeTrivialContextMap(split.howManyLiteralHistograms,
                    LITERAL_CONTEXT_BITS, tree, into);
        } else {
            writeContextMap(split.literalContextMap,
                    split.howManyLiteralHistograms, tree, into);
        }
        if (split.distanceContextMap.length == 0) {
            writeTrivialContextMap(split.howManyDistanceHistograms,
                    DISTANCE_CONTEXT_BITS, tree, into);
        } else {
            writeContextMap(split.distanceContextMap,
                    split.howManyDistanceHistograms, tree, into);
        }

        literals.writeTheCodes(split.literalHistograms,
                split.howManyLiteralHistograms, LITERAL_SYMBOLS, tree, into);
        commandSymbols.writeTheCodes(split.commandHistograms,
                split.howManyCommandHistograms, BrotliCodes.COMMAND_SYMBOLS,
                tree, into);
        distanceSymbols.writeTheCodes(split.distanceHistograms,
                split.howManyDistanceHistograms, distances.alphabetSize(),
                tree, into);

        boolean literalsCarryContext = split.literalContextMap.length != 0;
        boolean distancesCarryContext = split.distanceContextMap.length != 0;
        int at = from;
        int previous = previousByte;
        int beforeThat = theByteBeforeThat;
        for (int which = 0; which < commands.count(); which++) {
            commandSymbols.write(commands.commandPrefixAt(which), into);
            writeTheExtraBitsOfALength(commands, which, into);
            for (int left = commands.insertLengthAt(which); left != 0; left--) {
                int literal = data[at & mask] & 0xFF;
                if (literalsCarryContext) {
                    int context = BrotliContext.of(contextMode, previous,
                            beforeThat);
                    literals.writeInContext(literal, context,
                            split.literalContextMap, LITERAL_CONTEXT_BITS, into);
                    beforeThat = previous;
                    previous = literal;
                } else {
                    literals.write(literal, into);
                }
                at++;
            }
            int copyLength = commands.copyLengthAt(which);
            at += copyLength;
            if (copyLength == 0) {
                continue;
            }
            beforeThat = data[(at - 2) & mask] & 0xFF;
            previous = data[(at - 1) & mask] & 0xFF;
            if (commands.commandPrefixAt(which) < 128) {
                continue;
            }
            int distanceCode = commands.distancePrefixAt(which) & 0x3FF;
            int extraBits = commands.distancePrefixAt(which) >>> 10;
            if (distancesCarryContext) {
                distanceSymbols.writeInContext(distanceCode,
                        commands.distanceContextAt(which),
                        split.distanceContextMap, DISTANCE_CONTEXT_BITS, into);
            } else {
                distanceSymbols.write(distanceCode, into);
            }
            into.write(extraBits,
                    commands.distanceExtraAt(which) & 0xFFFFFFFFL);
        }
        if (isLast) {
            into.jumpToByteBoundary();
        }
    }

    private record ThreeHistograms(BrotliHistogram literals,
            BrotliHistogram commands, BrotliHistogram distances) {
    }

    private static ThreeHistograms countedUp(byte[] data, int mask, int from,
            BrotliCommand commands, int distanceAlphabetSize) {

        ThreeHistograms counted = new ThreeHistograms(
                new BrotliHistogram(LITERAL_SYMBOLS),
                new BrotliHistogram(BrotliCodes.COMMAND_SYMBOLS),
                new BrotliHistogram(distanceAlphabetSize));
        int at = from;
        for (int which = 0; which < commands.count(); which++) {
            counted.commands.add(commands.commandPrefixAt(which));
            for (int left = commands.insertLengthAt(which); left != 0; left--) {
                counted.literals.add(data[at & mask] & 0xFF);
                at++;
            }
            int copyLength = commands.copyLengthAt(which);
            at += copyLength;
            if (copyLength != 0 && commands.commandPrefixAt(which) >= 128) {
                counted.distances.add(commands.distancePrefixAt(which) & 0x3FF);
            }
        }
        return counted;
    }

    private static void writeTheSymbols(byte[] data, int mask, int from,
            BrotliCommand commands, int[] literalDepth, int[] literalBits,
            int[] commandDepth, int[] commandBits, int[] distanceDepth,
            int[] distanceBits, BrotliBits into) {

        int at = from;
        for (int which = 0; which < commands.count(); which++) {
            int commandCode = commands.commandPrefixAt(which);
            into.write(commandDepth[commandCode], commandBits[commandCode]);
            writeTheExtraBitsOfALength(commands, which, into);
            for (int left = commands.insertLengthAt(which); left != 0; left--) {
                int literal = data[at & mask] & 0xFF;
                into.write(literalDepth[literal], literalBits[literal]);
                at++;
            }
            int copyLength = commands.copyLengthAt(which);
            at += copyLength;
            if (copyLength == 0 || commands.commandPrefixAt(which) < 128) {
                continue;
            }
            int distanceCode = commands.distancePrefixAt(which) & 0x3FF;
            into.write(distanceDepth[distanceCode], distanceBits[distanceCode]);
            into.write(commands.distancePrefixAt(which) >>> 10,
                    commands.distanceExtraAt(which) & 0xFFFFFFFFL);
        }
    }

    static void writeWithOneCodePerAlphabet(byte[] data, int mask, int from,
            int length, boolean isLast, BrotliDistances distances,
            BrotliCommand commands, BrotliBits into) {

        writeCompressedHeader(isLast, length, into);
        int distanceSymbols = distances.alphabetSize();
        ThreeHistograms counted = countedUp(data, mask, from, commands,
                MOST_DISTANCE_SYMBOLS_A_SIMPLE_CODE_NEEDS);

        into.write(13, 0);
        BrotliCodes.Tree tree = new BrotliCodes.Tree();
        int[] literalDepth = new int[LITERAL_SYMBOLS];
        int[] literalBits = new int[LITERAL_SYMBOLS];
        int[] commandDepth = new int[BrotliCodes.COMMAND_SYMBOLS];
        int[] commandBits = new int[BrotliCodes.COMMAND_SYMBOLS];
        int[] distanceDepth = new int[MOST_DISTANCE_SYMBOLS_A_SIMPLE_CODE_NEEDS];
        int[] distanceBits = new int[MOST_DISTANCE_SYMBOLS_A_SIMPLE_CODE_NEEDS];

        writeATreeFor(counted.literals.counts(), LITERAL_SYMBOLS,
                LITERAL_SYMBOLS, tree, literalDepth, 0, literalBits, 0, into);
        writeATreeFor(counted.commands.counts(), BrotliCodes.COMMAND_SYMBOLS,
                BrotliCodes.COMMAND_SYMBOLS, tree, commandDepth, 0,
                commandBits, 0, into);
        writeATreeFor(counted.distances.counts(),
                MOST_DISTANCE_SYMBOLS_A_SIMPLE_CODE_NEEDS, distanceSymbols,
                tree, distanceDepth, 0, distanceBits, 0, into);
        writeTheSymbols(data, mask, from, commands, literalDepth, literalBits,
                commandDepth, commandBits, distanceDepth, distanceBits, into);
        if (isLast) {
            into.jumpToByteBoundary();
        }
    }

    private static final int MOST_DISTANCE_SYMBOLS_A_SIMPLE_CODE_NEEDS = 140;
    private static final int TOO_FEW_COMMANDS_TO_MEASURE = 128;

    static void writeWithMostlyFixedCodes(byte[] data, int mask, int from,
            int length, boolean isLast, BrotliDistances distances,
            BrotliCommand commands, BrotliBits into) {

        writeCompressedHeader(isLast, length, into);
        into.write(13, 0);

        BrotliCodes.Tree tree = new BrotliCodes.Tree();
        int[] literalDepth = new int[LITERAL_SYMBOLS];
        int[] literalBits = new int[LITERAL_SYMBOLS];

        if (commands.count() <= TOO_FEW_COMMANDS_TO_MEASURE) {
            int[] histogram = new int[LITERAL_SYMBOLS];
            int at = from;
            long howManyLiterals = 0;
            for (int which = 0; which < commands.count(); which++) {
                for (int left = commands.insertLengthAt(which); left != 0; left--) {
                    histogram[data[at & mask] & 0xFF]++;
                    at++;
                }
                howManyLiterals += commands.insertLengthAt(which);
                at += commands.copyLengthAt(which);
            }
            BrotliCodes.buildAndStoreHuffmanTreeFast(tree, histogram,
                    howManyLiterals, 8, literalDepth, literalBits, into);
            BrotliStaticCodes.writeTheCommandTree(into);
            BrotliStaticCodes.writeTheDistanceTree(into);
            writeTheSymbols(data, mask, from, commands, literalDepth, literalBits,
                    BrotliStaticCodes.COMMAND_DEPTH, BrotliStaticCodes.COMMAND_BITS,
                    BrotliStaticCodes.DISTANCE_DEPTH, BrotliStaticCodes.DISTANCE_BITS,
                    into);
        } else {
            ThreeHistograms counted = countedUp(data, mask, from, commands,
                    MOST_DISTANCE_SYMBOLS_A_SIMPLE_CODE_NEEDS);
            int[] commandDepth = new int[BrotliCodes.COMMAND_SYMBOLS];
            int[] commandBits = new int[BrotliCodes.COMMAND_SYMBOLS];
            int[] distanceDepth = new int[MOST_DISTANCE_SYMBOLS_A_SIMPLE_CODE_NEEDS];
            int[] distanceBits = new int[MOST_DISTANCE_SYMBOLS_A_SIMPLE_CODE_NEEDS];
            int distanceCodeWidth =
                    BrotliCodes.log2Floor(distances.alphabetSize() - 1) + 1;
            BrotliCodes.buildAndStoreHuffmanTreeFast(tree,
                    counted.literals.counts(), counted.literals.total(), 8,
                    literalDepth, literalBits, into);
            BrotliCodes.buildAndStoreHuffmanTreeFast(tree,
                    counted.commands.counts(), counted.commands.total(), 10,
                    commandDepth, commandBits, into);
            BrotliCodes.buildAndStoreHuffmanTreeFast(tree,
                    counted.distances.counts(), counted.distances.total(),
                    distanceCodeWidth, distanceDepth, distanceBits, into);
            writeTheSymbols(data, mask, from, commands, literalDepth, literalBits,
                    commandDepth, commandBits, distanceDepth, distanceBits, into);
        }
        if (isLast) {
            into.jumpToByteBoundary();
        }
    }
}
