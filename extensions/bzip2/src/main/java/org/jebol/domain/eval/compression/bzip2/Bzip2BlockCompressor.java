package org.jebol.domain.eval.compression.bzip2;

final class Bzip2BlockCompressor {

    private static final int MAX_GROUPS = 6;
    private static final int GROUP_SIZE = 50;
    private static final int TABLE_ITERATIONS = 4;
    private static final int LONGEST_CODE = 17;
    private static final int LESSER_COST = 0;
    private static final int GREATER_COST = 15;
    private static final int MAX_SELECTORS = 2 + 900000 / GROUP_SIZE;
    private static final int RUN_A = 0;
    private static final int RUN_B = 1;
    private static final int OCTET_VALUES = 256;
    private static final int MOST_COST = 999999999;

    final byte[] block;
    final boolean[] inUse = new boolean[OCTET_VALUES];
    final int blockSize100k;
    final int blockLimit;
    final byte[] compressed;
    int blockLength;
    int blockCrc;
    int compressedLength;
    int blockNumber;

    private final int[] pointers;
    private final char[] symbols;
    private final Bzip2BlockSort sorter;
    private final byte[] selector = new byte[MAX_SELECTORS];
    private final byte[] selectorMoved = new byte[MAX_SELECTORS];
    private final byte[][] lengths = new byte[MAX_GROUPS][Bzip2Huffman.MAX_ALPHA_SIZE];
    private final int[][] codes = new int[MAX_GROUPS][Bzip2Huffman.MAX_ALPHA_SIZE];
    private final int[][] groupFrequencies = new int[MAX_GROUPS][Bzip2Huffman.MAX_ALPHA_SIZE];
    private final int[] symbolFrequencies = new int[Bzip2Huffman.MAX_ALPHA_SIZE];
    private final byte[] unsequenced = new byte[OCTET_VALUES];
    private int inUseCount;
    private int symbolCount;
    private int combinedCrc;
    private int originPointer;
    private int bitBuffer;
    private int bitsLive;

    Bzip2BlockCompressor(int blockSize100k) {
        this.blockSize100k = blockSize100k;
        int size = 100000 * blockSize100k;
        this.blockLimit = size - 19;
        this.block = new byte[size + Bzip2BlockSort.OVERSHOOT];
        this.compressed = new byte[(size + Bzip2BlockSort.OVERSHOOT) * 4];
        this.pointers = new int[size];
        this.symbols = new char[size + 1];
        this.sorter = new Bzip2BlockSort(pointers, block);
    }

    private void writeBits(int count, int value) {
        while (bitsLive >= 8) {
            compressed[compressedLength++] = (byte) (bitBuffer >>> 24);
            bitBuffer <<= 8;
            bitsLive -= 8;
        }
        bitBuffer |= value << (32 - bitsLive - count);
        bitsLive += count;
    }

    private void writeOctet(int octet) {
        writeBits(8, octet);
    }

    private void writeWord(int word) {
        writeBits(8, word >>> 24);
        writeBits(8, (word >>> 16) & 0xFF);
        writeBits(8, (word >>> 8) & 0xFF);
        writeBits(8, word & 0xFF);
    }

    private void finishWriting() {
        while (bitsLive > 0) {
            compressed[compressedLength++] = (byte) (bitBuffer >>> 24);
            bitBuffer <<= 8;
            bitsLive -= 8;
        }
    }

    private void writeRunLength(int pending) {
        int remaining = pending - 1;
        for (;;) {
            int symbol = (remaining & 1) != 0 ? RUN_B : RUN_A;
            symbols[symbolCount++] = (char) symbol;
            symbolFrequencies[symbol]++;
            if (remaining < 2) {
                return;
            }
            remaining = (remaining - 2) / 2;
        }
    }

    private void generateSymbols() {
        inUseCount = 0;
        for (int i = 0; i < OCTET_VALUES; i++) {
            if (inUse[i]) {
                unsequenced[i] = (byte) inUseCount;
                inUseCount++;
            }
        }
        int endOfBlock = inUseCount + 1;
        for (int i = 0; i <= endOfBlock; i++) {
            symbolFrequencies[i] = 0;
        }
        int[] order = new int[OCTET_VALUES];
        for (int i = 0; i < inUseCount; i++) {
            order[i] = i;
        }
        symbolCount = 0;
        int pending = 0;
        for (int i = 0; i < blockLength; i++) {
            int at = pointers[i] - 1;
            if (at < 0) {
                at += blockLength;
            }
            int wanted = unsequenced[block[at] & 0xFF] & 0xFF;
            if (order[0] == wanted) {
                pending++;
                continue;
            }
            if (pending > 0) {
                writeRunLength(pending);
                pending = 0;
            }
            int held = order[1];
            order[1] = order[0];
            int position = 1;
            while (wanted != held) {
                position++;
                int next = order[position];
                order[position] = held;
                held = next;
            }
            order[0] = held;
            symbols[symbolCount++] = (char) (position + 1);
            symbolFrequencies[position + 1]++;
        }
        if (pending > 0) {
            writeRunLength(pending);
        }
        symbols[symbolCount++] = (char) endOfBlock;
        symbolFrequencies[endOfBlock]++;
    }

    private void writeSymbols() {
        int alphaSize = inUseCount + 2;
        for (int table = 0; table < MAX_GROUPS; table++) {
            for (int symbol = 0; symbol < alphaSize; symbol++) {
                lengths[table][symbol] = GREATER_COST;
            }
        }
        int groups = symbolCount < 200 ? 2 : symbolCount < 600 ? 3 : symbolCount < 1200 ? 4 : symbolCount < 2400 ? 5 : 6;
        seedTables(groups, alphaSize);
        int selectors = 0;
        for (int iteration = 0; iteration < TABLE_ITERATIONS; iteration++) {
            selectors = chooseTablesOnce(groups, alphaSize);
        }
        moveSelectorsToFront(groups, selectors);
        for (int table = 0; table < groups; table++) {
            int shortest = 32;
            int longest = 0;
            for (int i = 0; i < alphaSize; i++) {
                int length = lengths[table][i] & 0xFF;
                longest = Math.max(longest, length);
                shortest = Math.min(shortest, length);
            }
            Bzip2Huffman.assignCodes(codes[table], lengths[table], shortest, longest, alphaSize);
        }
        writeOctetMap();
        writeBits(3, groups);
        writeBits(15, selectors);
        for (int i = 0; i < selectors; i++) {
            for (int j = 0; j < (selectorMoved[i] & 0xFF); j++) {
                writeBits(1, 1);
            }
            writeBits(1, 0);
        }
        writeCodeLengths(groups, alphaSize);
        writeCodedSymbols();
    }

    private void seedTables(int groups, int alphaSize) {
        int parts = groups;
        int remaining = symbolCount;
        int start = 0;
        while (parts > 0) {
            int target = remaining / parts;
            int end = start - 1;
            int gathered = 0;
            while (gathered < target && end < alphaSize - 1) {
                end++;
                gathered += symbolFrequencies[end];
            }
            if (end > start && parts != groups && parts != 1 && (groups - parts) % 2 == 1) {
                gathered -= symbolFrequencies[end];
                end--;
            }
            for (int symbol = 0; symbol < alphaSize; symbol++) {
                lengths[parts - 1][symbol] = symbol >= start && symbol <= end ? (byte) LESSER_COST : (byte) GREATER_COST;
            }
            parts--;
            start = end + 1;
            remaining -= gathered;
        }
    }

    private int chooseTablesOnce(int groups, int alphaSize) {
        int[] costs = new int[MAX_GROUPS];
        for (int table = 0; table < groups; table++) {
            for (int symbol = 0; symbol < alphaSize; symbol++) {
                groupFrequencies[table][symbol] = 0;
            }
        }
        int selectors = 0;
        int start = 0;
        while (start < symbolCount) {
            int end = Math.min(start + GROUP_SIZE - 1, symbolCount - 1);
            for (int table = 0; table < groups; table++) {
                costs[table] = 0;
            }
            for (int i = start; i <= end; i++) {
                int symbol = symbols[i];
                for (int table = 0; table < groups; table++) {
                    costs[table] += lengths[table][symbol] & 0xFF;
                }
            }
            int bestCost = MOST_COST;
            int best = -1;
            for (int table = 0; table < groups; table++) {
                if (costs[table] < bestCost) {
                    bestCost = costs[table];
                    best = table;
                }
            }
            selector[selectors++] = (byte) best;
            for (int i = start; i <= end; i++) {
                groupFrequencies[best][symbols[i]]++;
            }
            start = end + 1;
        }
        for (int table = 0; table < groups; table++) {
            Bzip2Huffman.makeCodeLengths(lengths[table], groupFrequencies[table], alphaSize, LONGEST_CODE);
        }
        return selectors;
    }

    private void moveSelectorsToFront(int groups, int selectors) {
        int[] position = new int[MAX_GROUPS];
        for (int i = 0; i < groups; i++) {
            position[i] = i;
        }
        for (int i = 0; i < selectors; i++) {
            int wanted = selector[i];
            int j = 0;
            int held = position[j];
            while (wanted != held) {
                j++;
                int next = held;
                held = position[j];
                position[j] = next;
            }
            position[0] = held;
            selectorMoved[i] = (byte) j;
        }
    }

    private void writeOctetMap() {
        boolean[] isSixteenUsed = new boolean[16];
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                if (inUse[i * 16 + j]) {
                    isSixteenUsed[i] = true;
                }
            }
        }
        for (int i = 0; i < 16; i++) {
            writeBits(1, isSixteenUsed[i] ? 1 : 0);
        }
        for (int i = 0; i < 16; i++) {
            if (isSixteenUsed[i]) {
                for (int j = 0; j < 16; j++) {
                    writeBits(1, inUse[i * 16 + j] ? 1 : 0);
                }
            }
        }
    }

    private void writeCodeLengths(int groups, int alphaSize) {
        for (int table = 0; table < groups; table++) {
            int current = lengths[table][0] & 0xFF;
            writeBits(5, current);
            for (int i = 0; i < alphaSize; i++) {
                int wanted = lengths[table][i] & 0xFF;
                while (current < wanted) {
                    writeBits(2, 2);
                    current++;
                }
                while (current > wanted) {
                    writeBits(2, 3);
                    current--;
                }
                writeBits(1, 0);
            }
        }
    }

    private void writeCodedSymbols() {
        int selected = 0;
        int start = 0;
        while (start < symbolCount) {
            int end = Math.min(start + GROUP_SIZE - 1, symbolCount - 1);
            byte[] tableLengths = lengths[selector[selected]];
            int[] tableCodes = codes[selector[selected]];
            for (int i = start; i <= end; i++) {
                int symbol = symbols[i];
                writeBits(tableLengths[symbol] & 0xFF, tableCodes[symbol]);
            }
            start = end + 1;
            selected++;
        }
    }

    void compressBlock(boolean isLastBlock) {
        if (blockLength > 0) {
            blockCrc = ~blockCrc;
            combinedCrc = Integer.rotateLeft(combinedCrc, 1) ^ blockCrc;
            if (blockNumber > 1) {
                compressedLength = 0;
            }
            originPointer = sorter.sort(blockLength);
        }
        if (blockNumber == 1) {
            bitBuffer = 0;
            bitsLive = 0;
            writeOctet('B');
            writeOctet('Z');
            writeOctet('h');
            writeOctet('0' + blockSize100k);
        }
        if (blockLength > 0) {
            writeOctet(0x31);
            writeOctet(0x41);
            writeOctet(0x59);
            writeOctet(0x26);
            writeOctet(0x53);
            writeOctet(0x59);
            writeWord(blockCrc);
            writeBits(1, 0);
            writeBits(24, originPointer);
            generateSymbols();
            writeSymbols();
        }
        if (isLastBlock) {
            writeOctet(0x17);
            writeOctet(0x72);
            writeOctet(0x45);
            writeOctet(0x38);
            writeOctet(0x50);
            writeOctet(0x90);
            writeWord(combinedCrc);
            finishWriting();
        }
    }
}
