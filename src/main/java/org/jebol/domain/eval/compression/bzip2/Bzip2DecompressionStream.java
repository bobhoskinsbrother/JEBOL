package org.jebol.domain.eval.compression.bzip2;

import java.util.Arrays;

/** See docs/bzip2-port.md, and do not tidy this. */
public final class Bzip2DecompressionStream {

    private static final int IDLE = 1;
    private static final int OUTPUT = 2;
    private static final int MAGIC_1 = 10;
    private static final int MAGIC_2 = 11;
    private static final int MAGIC_3 = 12;
    private static final int MAGIC_4 = 13;
    private static final int BLOCK_HEADER_1 = 14;
    private static final int BLOCK_HEADER_2 = 15;
    private static final int BLOCK_HEADER_3 = 16;
    private static final int BLOCK_HEADER_4 = 17;
    private static final int BLOCK_HEADER_5 = 18;
    private static final int BLOCK_HEADER_6 = 19;
    private static final int BLOCK_CRC_1 = 20;
    private static final int BLOCK_CRC_2 = 21;
    private static final int BLOCK_CRC_3 = 22;
    private static final int BLOCK_CRC_4 = 23;
    private static final int RANDOM_BIT = 24;
    private static final int ORIGIN_1 = 25;
    private static final int ORIGIN_2 = 26;
    private static final int ORIGIN_3 = 27;
    private static final int MAPPING_1 = 28;
    private static final int MAPPING_2 = 29;
    private static final int SELECTOR_1 = 30;
    private static final int SELECTOR_2 = 31;
    private static final int SELECTOR_3 = 32;
    private static final int CODING_1 = 33;
    private static final int CODING_2 = 34;
    private static final int CODING_3 = 35;
    private static final int SYMBOL_1 = 36;
    private static final int SYMBOL_2 = 37;
    private static final int RUN_SYMBOL_1 = 38;
    private static final int RUN_SYMBOL_2 = 39;
    private static final int NEXT_SYMBOL_1 = 40;
    private static final int NEXT_SYMBOL_2 = 41;
    private static final int END_HEADER_2 = 42;
    private static final int END_HEADER_3 = 43;
    private static final int END_HEADER_4 = 44;
    private static final int END_HEADER_5 = 45;
    private static final int END_HEADER_6 = 46;
    private static final int COMBINED_CRC_1 = 47;
    private static final int COMBINED_CRC_2 = 48;
    private static final int COMBINED_CRC_3 = 49;
    private static final int COMBINED_CRC_4 = 50;

    private static final int MAX_GROUPS = 6;
    private static final int GROUP_SIZE = 50;
    private static final int MAX_SELECTORS = 2 + 900000 / GROUP_SIZE;
    private static final int OCTET_VALUES = 256;
    private static final int RUN_A = 0;
    private static final int RUN_B = 1;
    private static final int LONGEST_CODE = 20;
    private static final int LARGEST_RUN_FACTOR = 2 * 1024 * 1024;
    private static final int MOVE_TABLE_SIZE = 4096;
    private static final int MOVE_LINE_SIZE = 16;
    private static final int MOVE_LINES = OCTET_VALUES / MOVE_LINE_SIZE;
    private static final int RANDOM_TABLE_SIZE = 512;
    private static final int SYMBOL_READ = 100;
    private static final int LENGTH_BITS = 5;

    private int state = MAGIC_1;
    private Bzip2Buffers io;
    private int bitBuffer;
    private int bitsLive;
    private int blockSize100k;
    private int[] transform = new int[0];
    private boolean isRandomised;
    private int randomToGo;
    private int randomAt;
    private int origin;
    private int positionInTransform;
    private int firstOctet;
    private int randomisedOctet;
    private int usedInBlock;
    private int outputOctet;
    private int outputLength;
    private int storedBlockCrc;
    private int storedCombinedCrc;
    private int calculatedBlockCrc;
    private int calculatedCombinedCrc;
    private int inUseCount;
    private final boolean[] inUse = new boolean[OCTET_VALUES];
    private final boolean[] isSixteenUsed = new boolean[17];
    private final byte[] sequenced = new byte[OCTET_VALUES];
    private final byte[] moves = new byte[MOVE_TABLE_SIZE];
    private final int[] moveBase = new int[MOVE_LINES];
    private final byte[] selector = new byte[MAX_SELECTORS];
    private final byte[] selectorMoved = new byte[MAX_SELECTORS];
    private final byte[][] lengths = new byte[MAX_GROUPS][Bzip2Huffman.MAX_ALPHA_SIZE];
    private final int[][] limits = new int[MAX_GROUPS][Bzip2Huffman.MAX_ALPHA_SIZE];
    private final int[][] bases = new int[MAX_GROUPS][Bzip2Huffman.MAX_ALPHA_SIZE];
    private final int[][] perms = new int[MAX_GROUPS][Bzip2Huffman.MAX_ALPHA_SIZE];
    private final int[] shortestLengths = new int[MAX_GROUPS];
    private final int[] counts = new int[OCTET_VALUES];
    private final int[] cumulative = new int[OCTET_VALUES + 1];
    private int i;
    private int j;
    private int table;
    private int alphaSize;
    private int groups;
    private int selectors;
    private int endOfBlock;
    private int groupNumber;
    private int groupLeft;
    private int nextSymbol;
    private int blockLimit;
    private int blockLength;
    private int runLength;
    private int runFactor;
    private int current;
    private int codeBits;
    private int codeValue;
    private int chosenTable;

    public boolean isReadingSelectors() {
        return state == SELECTOR_3;
    }

    public int decompress(Bzip2Buffers buffers) {
        io = buffers;
        for (;;) {
            if (state == IDLE) {
                return Bzip2Codes.SEQUENCE_ERROR;
            }
            if (state == OUTPUT) {
                boolean isCorrupt = isRandomised ? copyRandomisedRuns() : copyRuns();
                if (isCorrupt) {
                    return Bzip2Codes.DATA_ERROR;
                }
                if (usedInBlock == blockLength + 1 && outputLength == 0) {
                    calculatedBlockCrc = ~calculatedBlockCrc;
                    if (calculatedBlockCrc != storedBlockCrc) {
                        return Bzip2Codes.DATA_ERROR;
                    }
                    calculatedCombinedCrc = Integer.rotateLeft(calculatedCombinedCrc, 1) ^ calculatedBlockCrc;
                    state = BLOCK_HEADER_1;
                } else {
                    return Bzip2Codes.OK;
                }
            }
            if (state >= MAGIC_1) {
                int answer = readUntilOutput();
                if (answer == Bzip2Codes.STREAM_END) {
                    return calculatedCombinedCrc != storedCombinedCrc ? Bzip2Codes.DATA_ERROR : answer;
                }
                if (state != OUTPUT) {
                    return answer;
                }
            }
        }
    }

    private boolean hasBits(int count) {
        while (bitsLive < count) {
            if (io.availableIn == 0) {
                return false;
            }
            bitBuffer = (bitBuffer << 8) | (io.input[io.nextIn] & 0xFF);
            bitsLive += 8;
            io.nextIn++;
            io.availableIn--;
        }
        return true;
    }

    private int takeBits(int count) {
        int value = (bitBuffer >>> (bitsLive - count)) & ((1 << count) - 1);
        bitsLive -= count;
        return value;
    }

    private int readUntilOutput() {
        for (;;) {
            int answer = switch (state) {
                case MAGIC_1 -> expectOctet('B', Bzip2Codes.DATA_ERROR_MAGIC, MAGIC_2);
                case MAGIC_2 -> expectOctet('Z', Bzip2Codes.DATA_ERROR_MAGIC, MAGIC_3);
                case MAGIC_3 -> expectOctet('h', Bzip2Codes.DATA_ERROR_MAGIC, MAGIC_4);
                case MAGIC_4 -> readBlockSize();
                case BLOCK_HEADER_1 -> readFirstBlockOctet();
                case BLOCK_HEADER_2 -> expectOctet(0x41, Bzip2Codes.DATA_ERROR, BLOCK_HEADER_3);
                case BLOCK_HEADER_3 -> expectOctet(0x59, Bzip2Codes.DATA_ERROR, BLOCK_HEADER_4);
                case BLOCK_HEADER_4 -> expectOctet(0x26, Bzip2Codes.DATA_ERROR, BLOCK_HEADER_5);
                case BLOCK_HEADER_5 -> expectOctet(0x53, Bzip2Codes.DATA_ERROR, BLOCK_HEADER_6);
                case BLOCK_HEADER_6 -> readLastBlockOctet();
                case BLOCK_CRC_1, BLOCK_CRC_2, BLOCK_CRC_3, BLOCK_CRC_4 -> readBlockCrcOctet();
                case RANDOM_BIT -> readRandomBit();
                case ORIGIN_1, ORIGIN_2, ORIGIN_3 -> readOriginOctet();
                case MAPPING_1 -> readFirstMappingBit();
                case MAPPING_2 -> readSecondMappingBit();
                case SELECTOR_1 -> readGroupCount();
                case SELECTOR_2 -> readSelectorCount();
                case SELECTOR_3 -> readSelectorBit();
                case CODING_1 -> readFirstLength();
                case CODING_2 -> readLengthContinues();
                case CODING_3 -> readLengthStep();
                case SYMBOL_1, RUN_SYMBOL_1, NEXT_SYMBOL_1 -> readSymbolStart();
                case SYMBOL_2, RUN_SYMBOL_2, NEXT_SYMBOL_2 -> readSymbolBit();
                case END_HEADER_2 -> expectOctet(0x72, Bzip2Codes.DATA_ERROR, END_HEADER_3);
                case END_HEADER_3 -> expectOctet(0x45, Bzip2Codes.DATA_ERROR, END_HEADER_4);
                case END_HEADER_4 -> expectOctet(0x38, Bzip2Codes.DATA_ERROR, END_HEADER_5);
                case END_HEADER_5 -> expectOctet(0x50, Bzip2Codes.DATA_ERROR, END_HEADER_6);
                case END_HEADER_6 -> readLastEndOctet();
                case COMBINED_CRC_1, COMBINED_CRC_2, COMBINED_CRC_3 -> readCombinedCrcOctet();
                case COMBINED_CRC_4 -> readLastCombinedCrcOctet();
                default -> throw new IllegalStateException("bzip2's decoder has no state " + state);
            };
            if (answer != SYMBOL_READ) {
                return answer;
            }
            if (state == OUTPUT) {
                return Bzip2Codes.OK;
            }
        }
    }

    private int expectOctet(int wanted, int otherwise, int next) {
        if (!hasBits(8)) {
            return Bzip2Codes.OK;
        }
        if (takeBits(8) != wanted) {
            return otherwise;
        }
        state = next;
        return SYMBOL_READ;
    }

    private int readBlockSize() {
        if (!hasBits(8)) {
            return Bzip2Codes.OK;
        }
        blockSize100k = takeBits(8);
        if (blockSize100k < '1' || blockSize100k > '9') {
            return Bzip2Codes.DATA_ERROR_MAGIC;
        }
        blockSize100k -= '0';
        transform = new int[blockSize100k * 100000];
        state = BLOCK_HEADER_1;
        return SYMBOL_READ;
    }

    private int readFirstBlockOctet() {
        if (!hasBits(8)) {
            return Bzip2Codes.OK;
        }
        int octet = takeBits(8);
        if (octet == 0x17) {
            state = END_HEADER_2;
            return SYMBOL_READ;
        }
        if (octet != 0x31) {
            return Bzip2Codes.DATA_ERROR;
        }
        state = BLOCK_HEADER_2;
        return SYMBOL_READ;
    }

    private int readLastBlockOctet() {
        int answer = expectOctet(0x59, Bzip2Codes.DATA_ERROR, BLOCK_CRC_1);
        if (answer == SYMBOL_READ) {
            storedBlockCrc = 0;
        }
        return answer;
    }

    private int readBlockCrcOctet() {
        if (!hasBits(8)) {
            return Bzip2Codes.OK;
        }
        storedBlockCrc = (storedBlockCrc << 8) | takeBits(8);
        state = state == BLOCK_CRC_4 ? RANDOM_BIT : state + 1;
        return SYMBOL_READ;
    }

    private int readRandomBit() {
        if (!hasBits(1)) {
            return Bzip2Codes.OK;
        }
        isRandomised = takeBits(1) != 0;
        origin = 0;
        state = ORIGIN_1;
        return SYMBOL_READ;
    }

    private int readOriginOctet() {
        if (!hasBits(8)) {
            return Bzip2Codes.OK;
        }
        origin = (origin << 8) | takeBits(8);
        if (state != ORIGIN_3) {
            state++;
            return SYMBOL_READ;
        }
        if (origin < 0 || origin > 10 + 100000 * blockSize100k) {
            return Bzip2Codes.DATA_ERROR;
        }
        i = 0;
        state = MAPPING_1;
        return SYMBOL_READ;
    }

    private int readFirstMappingBit() {
        if (!hasBits(1)) {
            return Bzip2Codes.OK;
        }
        isSixteenUsed[i] = takeBits(1) == 1;
        i++;
        if (i < 16) {
            return SYMBOL_READ;
        }
        Arrays.fill(inUse, false);
        i = 0;
        return moveToNextUsedSixteen();
    }

    private int moveToNextUsedSixteen() {
        while (i < 16 && !isSixteenUsed[i]) {
            i++;
        }
        if (i < 16) {
            j = 0;
            state = MAPPING_2;
            return SYMBOL_READ;
        }
        inUseCount = 0;
        for (int octet = 0; octet < OCTET_VALUES; octet++) {
            if (inUse[octet]) {
                sequenced[inUseCount] = (byte) octet;
                inUseCount++;
            }
        }
        if (inUseCount == 0) {
            return Bzip2Codes.DATA_ERROR;
        }
        alphaSize = inUseCount + 2;
        state = SELECTOR_1;
        return SYMBOL_READ;
    }

    private int readSecondMappingBit() {
        if (!hasBits(1)) {
            return Bzip2Codes.OK;
        }
        int octet = i * 16 + j;
        if (takeBits(1) == 1 && octet < OCTET_VALUES) {
            inUse[octet] = true;
        }
        j++;
        if (j < 16) {
            return SYMBOL_READ;
        }
        i++;
        return moveToNextUsedSixteen();
    }

    private int readGroupCount() {
        if (!hasBits(3)) {
            return Bzip2Codes.OK;
        }
        groups = takeBits(3);
        if (groups < 2 || groups > MAX_GROUPS) {
            return Bzip2Codes.DATA_ERROR;
        }
        state = SELECTOR_2;
        return SYMBOL_READ;
    }

    private int readSelectorCount() {
        if (!hasBits(15)) {
            return Bzip2Codes.OK;
        }
        selectors = takeBits(15);
        if (selectors < 1) {
            return Bzip2Codes.DATA_ERROR;
        }
        i = 0;
        j = 0;
        state = SELECTOR_3;
        return SYMBOL_READ;
    }

    private int readSelectorBit() {
        if (!hasBits(1)) {
            return Bzip2Codes.OK;
        }
        if (takeBits(1) != 0) {
            j++;
            return j >= groups ? Bzip2Codes.DATA_ERROR : SYMBOL_READ;
        }
        if (i < MAX_SELECTORS) {
            selectorMoved[i] = (byte) j;
        }
        i++;
        j = 0;
        return i < selectors ? SYMBOL_READ : undoSelectorMoves();
    }

    private int undoSelectorMoves() {
        if (selectors > MAX_SELECTORS) {
            selectors = MAX_SELECTORS;
        }
        int[] position = new int[MAX_GROUPS];
        for (int group = 0; group < groups; group++) {
            position[group] = group;
        }
        for (int at = 0; at < selectors; at++) {
            int moved = selectorMoved[at] & 0xFF;
            if (moved >= MAX_GROUPS) {
                return Bzip2Codes.DATA_ERROR;
            }
            int held = position[moved];
            while (moved > 0) {
                position[moved] = position[moved - 1];
                moved--;
            }
            position[0] = held;
            selector[at] = (byte) held;
        }
        table = 0;
        state = CODING_1;
        return SYMBOL_READ;
    }

    private int checkLength() {
        if (current < 1 || current > LONGEST_CODE) {
            return Bzip2Codes.DATA_ERROR;
        }
        state = CODING_2;
        return SYMBOL_READ;
    }

    private int readFirstLength() {
        if (!hasBits(LENGTH_BITS)) {
            return Bzip2Codes.OK;
        }
        current = takeBits(LENGTH_BITS);
        i = 0;
        return checkLength();
    }

    private int readLengthContinues() {
        if (!hasBits(1)) {
            return Bzip2Codes.OK;
        }
        if (takeBits(1) != 0) {
            state = CODING_3;
            return SYMBOL_READ;
        }
        lengths[table][i] = (byte) current;
        i++;
        if (i < alphaSize) {
            return checkLength();
        }
        table++;
        if (table < groups) {
            state = CODING_1;
            return SYMBOL_READ;
        }
        return startSymbols();
    }

    private int readLengthStep() {
        if (!hasBits(1)) {
            return Bzip2Codes.OK;
        }
        current += takeBits(1) == 0 ? 1 : -1;
        return checkLength();
    }

    private int startSymbols() {
        for (int group = 0; group < groups; group++) {
            int shortest = 32;
            int longest = 0;
            for (int symbol = 0; symbol < alphaSize; symbol++) {
                int length = lengths[group][symbol] & 0xFF;
                longest = Math.max(longest, length);
                shortest = Math.min(shortest, length);
            }
            Bzip2Huffman.createDecodeTables(limits[group], bases[group], perms[group], lengths[group], shortest,
                    longest, alphaSize);
            shortestLengths[group] = shortest;
        }
        endOfBlock = inUseCount + 1;
        blockLimit = 100000 * blockSize100k;
        groupNumber = -1;
        groupLeft = 0;
        Arrays.fill(counts, 0);
        int at = MOVE_TABLE_SIZE - 1;
        for (int line = MOVE_LINES - 1; line >= 0; line--) {
            for (int offset = MOVE_LINE_SIZE - 1; offset >= 0; offset--) {
                moves[at] = (byte) (line * MOVE_LINE_SIZE + offset);
                at--;
            }
            moveBase[line] = at + 1;
        }
        blockLength = 0;
        return beginSymbol(SYMBOL_1);
    }

    private int beginSymbol(int label) {
        if (groupLeft == 0) {
            groupNumber++;
            if (groupNumber >= selectors) {
                return Bzip2Codes.DATA_ERROR;
            }
            groupLeft = GROUP_SIZE;
            chosenTable = selector[groupNumber];
        }
        groupLeft--;
        codeBits = shortestLengths[chosenTable];
        state = label;
        return SYMBOL_READ;
    }

    private int readSymbolStart() {
        if (!hasBits(codeBits)) {
            return Bzip2Codes.OK;
        }
        codeValue = takeBits(codeBits);
        return finishSymbol(state + 1);
    }

    private int readSymbolBit() {
        if (!hasBits(1)) {
            return Bzip2Codes.OK;
        }
        codeValue = (codeValue << 1) | takeBits(1);
        return finishSymbol(state);
    }

    private int finishSymbol(int bitLabel) {
        int[] limit = limits[chosenTable];
        for (;;) {
            if (codeBits > LONGEST_CODE) {
                return Bzip2Codes.DATA_ERROR;
            }
            if (codeValue <= limit[codeBits]) {
                break;
            }
            codeBits++;
            state = bitLabel;
            if (!hasBits(1)) {
                return Bzip2Codes.OK;
            }
            codeValue = (codeValue << 1) | takeBits(1);
        }
        int at = codeValue - bases[chosenTable][codeBits];
        if (at < 0 || at >= Bzip2Huffman.MAX_ALPHA_SIZE) {
            return Bzip2Codes.DATA_ERROR;
        }
        nextSymbol = perms[chosenTable][at];
        return bitLabel == RUN_SYMBOL_2 ? continueRun() : obeySymbol();
    }

    private int obeySymbol() {
        if (nextSymbol == endOfBlock) {
            return finishBlock();
        }
        if (nextSymbol == RUN_A || nextSymbol == RUN_B) {
            runLength = -1;
            runFactor = 1;
            return continueRun();
        }
        if (blockLength >= blockLimit) {
            return Bzip2Codes.DATA_ERROR;
        }
        int octet = moveSymbolToFront(nextSymbol - 1);
        if (octet < 0) {
            return Bzip2Codes.DATA_ERROR;
        }
        int unsequenced = sequenced[octet] & 0xFF;
        counts[unsequenced]++;
        transform[blockLength] = unsequenced;
        blockLength++;
        return beginSymbol(NEXT_SYMBOL_1);
    }

    private int continueRun() {
        if (nextSymbol != RUN_A && nextSymbol != RUN_B) {
            return finishRun();
        }
        if (runFactor >= LARGEST_RUN_FACTOR) {
            return Bzip2Codes.DATA_ERROR;
        }
        runLength += nextSymbol == RUN_A ? runFactor : 2 * runFactor;
        runFactor *= 2;
        return beginSymbol(RUN_SYMBOL_1);
    }

    private int finishRun() {
        runLength++;
        int unsequenced = sequenced[moves[moveBase[0]] & 0xFF] & 0xFF;
        counts[unsequenced] += runLength;
        while (runLength > 0) {
            if (blockLength >= blockLimit) {
                return Bzip2Codes.DATA_ERROR;
            }
            transform[blockLength] = unsequenced;
            blockLength++;
            runLength--;
        }
        return obeySymbol();
    }

    private int moveSymbolToFront(int position) {
        if (position < MOVE_LINE_SIZE) {
            int base = moveBase[0];
            int octet = moves[base + position] & 0xFF;
            System.arraycopy(moves, base, moves, base + 1, position);
            moves[base] = (byte) octet;
            return octet;
        }
        int line = position / MOVE_LINE_SIZE;
        if (line >= MOVE_LINES) {
            return -1;
        }
        int at = moveBase[line] + position % MOVE_LINE_SIZE;
        int octet = moves[at] & 0xFF;
        while (at > moveBase[line]) {
            moves[at] = moves[at - 1];
            at--;
        }
        moveBase[line]++;
        while (line > 0) {
            moveBase[line]--;
            moves[moveBase[line]] = moves[moveBase[line - 1] + MOVE_LINE_SIZE - 1];
            line--;
        }
        moveBase[0]--;
        moves[moveBase[0]] = (byte) octet;
        if (moveBase[0] == 0) {
            int write = MOVE_TABLE_SIZE - 1;
            for (int each = MOVE_LINES - 1; each >= 0; each--) {
                for (int offset = MOVE_LINE_SIZE - 1; offset >= 0; offset--) {
                    moves[write] = moves[moveBase[each] + offset];
                    write--;
                }
                moveBase[each] = write + 1;
            }
        }
        return octet;
    }

    private int finishBlock() {
        if (origin < 0 || origin >= blockLength) {
            return Bzip2Codes.DATA_ERROR;
        }
        for (int octet = 0; octet < OCTET_VALUES; octet++) {
            if (counts[octet] < 0 || counts[octet] > blockLength) {
                return Bzip2Codes.DATA_ERROR;
            }
        }
        cumulative[0] = 0;
        for (int at = 1; at <= OCTET_VALUES; at++) {
            cumulative[at] = counts[at - 1] + cumulative[at - 1];
        }
        for (int at = 0; at <= OCTET_VALUES; at++) {
            if (cumulative[at] < 0 || cumulative[at] > blockLength) {
                return Bzip2Codes.DATA_ERROR;
            }
        }
        for (int at = 1; at <= OCTET_VALUES; at++) {
            if (cumulative[at - 1] > cumulative[at]) {
                return Bzip2Codes.DATA_ERROR;
            }
        }
        outputLength = 0;
        outputOctet = 0;
        calculatedBlockCrc = Bzip2Tables.CRC_START;
        state = OUTPUT;
        for (int at = 0; at < blockLength; at++) {
            int octet = transform[at] & 0xFF;
            transform[cumulative[octet]] |= at << 8;
            cumulative[octet]++;
        }
        positionInTransform = transform[origin] >>> 8;
        usedInBlock = 0;
        randomToGo = 0;
        randomAt = 0;
        if (isOutsideBlockSize(positionInTransform)) {
            return Bzip2Codes.RUN_OK;
        }
        positionInTransform = transform[positionInTransform];
        firstOctet = positionInTransform & 0xFF;
        positionInTransform >>>= 8;
        usedInBlock++;
        if (isRandomised) {
            firstOctet ^= nextRandomMask();
        }
        return Bzip2Codes.OK;
    }

    private boolean isOutsideBlockSize(int position) {
        return Integer.compareUnsigned(position, 100000 * blockSize100k) >= 0;
    }

    private int nextRandomMask() {
        if (randomToGo == 0) {
            randomToGo = Bzip2Tables.RANDOM_RUNS[randomAt];
            randomAt++;
            if (randomAt == RANDOM_TABLE_SIZE) {
                randomAt = 0;
            }
        }
        randomToGo--;
        return randomToGo == 1 ? 1 : 0;
    }

    private int readLastEndOctet() {
        int answer = expectOctet(0x90, Bzip2Codes.DATA_ERROR, COMBINED_CRC_1);
        if (answer == SYMBOL_READ) {
            storedCombinedCrc = 0;
        }
        return answer;
    }

    private int readCombinedCrcOctet() {
        if (!hasBits(8)) {
            return Bzip2Codes.OK;
        }
        storedCombinedCrc = (storedCombinedCrc << 8) | takeBits(8);
        state++;
        return SYMBOL_READ;
    }

    private int readLastCombinedCrcOctet() {
        if (!hasBits(8)) {
            return Bzip2Codes.OK;
        }
        storedCombinedCrc = (storedCombinedCrc << 8) | takeBits(8);
        state = IDLE;
        return Bzip2Codes.STREAM_END;
    }

    private boolean readRandomisedOctet() {
        if (isOutsideBlockSize(positionInTransform)) {
            return false;
        }
        positionInTransform = transform[positionInTransform];
        randomisedOctet = positionInTransform & 0xFF;
        positionInTransform >>>= 8;
        randomisedOctet = (randomisedOctet ^ nextRandomMask()) & 0xFF;
        usedInBlock++;
        return true;
    }

    private boolean copyRandomisedRuns() {
        for (;;) {
            for (;;) {
                if (io.availableOut == 0) {
                    return false;
                }
                if (outputLength == 0) {
                    break;
                }
                io.output[io.nextOut] = (byte) outputOctet;
                calculatedBlockCrc = Bzip2Tables.updateCrc(calculatedBlockCrc, outputOctet);
                outputLength--;
                io.nextOut++;
                io.availableOut--;
            }
            if (usedInBlock == blockLength + 1) {
                return false;
            }
            if (usedInBlock > blockLength + 1) {
                return true;
            }
            outputLength = 1;
            outputOctet = firstOctet & 0xFF;
            if (!readRandomisedOctet()) {
                return true;
            }
            if (usedInBlock == blockLength + 1) {
                continue;
            }
            if (randomisedOctet != firstOctet) {
                firstOctet = randomisedOctet;
                continue;
            }
            outputLength = 2;
            if (!readRandomisedOctet()) {
                return true;
            }
            if (usedInBlock == blockLength + 1) {
                continue;
            }
            if (randomisedOctet != firstOctet) {
                firstOctet = randomisedOctet;
                continue;
            }
            outputLength = 3;
            if (!readRandomisedOctet()) {
                return true;
            }
            if (usedInBlock == blockLength + 1) {
                continue;
            }
            if (randomisedOctet != firstOctet) {
                firstOctet = randomisedOctet;
                continue;
            }
            if (!readRandomisedOctet()) {
                return true;
            }
            outputLength = randomisedOctet + 4;
            if (isOutsideBlockSize(positionInTransform)) {
                return true;
            }
            positionInTransform = transform[positionInTransform];
            firstOctet = positionInTransform & 0xFF;
            positionInTransform >>>= 8;
            firstOctet ^= nextRandomMask();
            usedInBlock++;
        }
    }

    private boolean copyRuns() {
        int crc = calculatedBlockCrc;
        int octet = outputOctet;
        int length = outputLength;
        int used = usedInBlock;
        int first = firstOctet;
        int position = positionInTransform;
        int out = io.nextOut;
        int available = io.availableOut;
        byte[] output = io.output;
        int[] table32 = transform;
        int limit = blockLength + 1;
        int bound = 100000 * blockSize100k;
        boolean isAtLengthOne = false;
        for (;;) {
            if (isAtLengthOne || length > 0) {
                if (!isAtLengthOne) {
                    for (;;) {
                        if (available == 0) {
                            return saveRuns(crc, octet, length, used, first, position, out, available);
                        }
                        if (length == 1) {
                            break;
                        }
                        output[out] = (byte) octet;
                        crc = Bzip2Tables.updateCrc(crc, octet);
                        length--;
                        out++;
                        available--;
                    }
                }
                isAtLengthOne = false;
                if (available == 0) {
                    return saveRuns(crc, octet, 1, used, first, position, out, available);
                }
                output[out] = (byte) octet;
                crc = Bzip2Tables.updateCrc(crc, octet);
                out++;
                available--;
            }
            if (used > limit) {
                return true;
            }
            if (used == limit) {
                return saveRuns(crc, octet, 0, used, first, position, out, available);
            }
            octet = first;
            if (Integer.compareUnsigned(position, bound) >= 0) {
                return true;
            }
            position = table32[position];
            int next = position & 0xFF;
            position >>>= 8;
            used++;
            if (next != first) {
                first = next;
                isAtLengthOne = true;
                continue;
            }
            if (used == limit) {
                isAtLengthOne = true;
                continue;
            }
            length = 2;
            if (Integer.compareUnsigned(position, bound) >= 0) {
                return true;
            }
            position = table32[position];
            next = position & 0xFF;
            position >>>= 8;
            used++;
            if (used == limit) {
                continue;
            }
            if (next != first) {
                first = next;
                continue;
            }
            length = 3;
            if (Integer.compareUnsigned(position, bound) >= 0) {
                return true;
            }
            position = table32[position];
            next = position & 0xFF;
            position >>>= 8;
            used++;
            if (used == limit) {
                continue;
            }
            if (next != first) {
                first = next;
                continue;
            }
            if (Integer.compareUnsigned(position, bound) >= 0) {
                return true;
            }
            position = table32[position];
            next = position & 0xFF;
            position >>>= 8;
            used++;
            length = next + 4;
            if (Integer.compareUnsigned(position, bound) >= 0) {
                return true;
            }
            position = table32[position];
            first = position & 0xFF;
            position >>>= 8;
            used++;
        }
    }

    private boolean saveRuns(int crc, int octet, int length, int used, int first, int position, int out,
                             int available) {
        calculatedBlockCrc = crc;
        outputOctet = octet;
        outputLength = length;
        usedInBlock = used;
        firstOctet = first;
        positionInTransform = position;
        io.nextOut = out;
        io.availableOut = available;
        return false;
    }
}
