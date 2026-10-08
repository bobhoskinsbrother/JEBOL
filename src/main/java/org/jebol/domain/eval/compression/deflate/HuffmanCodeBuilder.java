package org.jebol.domain.eval.compression.deflate;

final class HuffmanCodeBuilder {

    private static final int SYMBOL_BITS = 10;

    private static final int SYMBOL_MASK = (1 << SYMBOL_BITS) - 1;

    private static final int FREQUENCY_MASK = ~SYMBOL_MASK;

    private static final int LONGEST_CODEWORD_IN_ANY_CODE = 15;

    void build(int symbolCount, int longestCodeword, int[] frequencies,
            int[] lengths, int lengthsFrom, int[] codewords) {
        int usedSymbols = sortSymbols(symbolCount, frequencies, lengths, lengthsFrom, codewords);
        if (usedSymbols < 2) {
            giveTwoOneBitCodewords(usedSymbols, lengths, lengthsFrom, codewords);
            return;
        }
        buildTree(codewords, usedSymbols);
        int[] lengthCounts = lengthCounts(codewords, usedSymbols - 2, longestCodeword);
        generateCodewords(codewords, lengths, lengthsFrom, lengthCounts, longestCodeword, symbolCount);
    }

    private void giveTwoOneBitCodewords(int usedSymbols, int[] lengths, int lengthsFrom, int[] codewords) {
        int used = usedSymbols != 0 ? codewords[0] & SYMBOL_MASK : 0;
        int nonZero = used != 0 ? used : 1;
        codewords[0] = 0;
        lengths[lengthsFrom] = 1;
        codewords[nonZero] = 1;
        lengths[lengthsFrom + nonZero] = 1;
    }

    private int sortSymbols(int counterCount, int[] frequencies, int[] lengths, int lengthsFrom, int[] sorted) {
        int symbolCount = counterCount;
        int[] counters = new int[counterCount];
        for (int symbol = 0; symbol < symbolCount; symbol++) {
            counters[Math.min(frequencies[symbol], counterCount - 1)]++;
        }
        int usedSymbols = 0;
        for (int counter = 1; counter < counterCount; counter++) {
            int count = counters[counter];
            counters[counter] = usedSymbols;
            usedSymbols += count;
        }
        for (int symbol = 0; symbol < symbolCount; symbol++) {
            int frequency = frequencies[symbol];
            if (frequency != 0) {
                sorted[counters[Math.min(frequency, counterCount - 1)]++] = symbol | (frequency << SYMBOL_BITS);
            } else {
                lengths[lengthsFrom + symbol] = 0;
            }
        }
        heapSort(sorted, counters[counterCount - 2], counters[counterCount - 1] - counters[counterCount - 2]);
        return usedSymbols;
    }

    private void heapSort(int[] values, int from, int length) {
        int beforeTheFirst = from - 1;
        for (int subtree = length / 2; subtree >= 1; subtree--) {
            heapify(values, beforeTheFirst, length, subtree);
        }
        int remaining = length;
        while (remaining >= 2) {
            int last = values[beforeTheFirst + remaining];
            values[beforeTheFirst + remaining] = values[beforeTheFirst + 1];
            values[beforeTheFirst + 1] = last;
            remaining--;
            heapify(values, beforeTheFirst, remaining, 1);
        }
    }

    private void heapify(int[] values, int beforeTheFirst, int length, int subtree) {
        int value = values[beforeTheFirst + subtree];
        int parent = subtree;
        int child = parent * 2;
        while (child <= length) {
            if (child < length && values[beforeTheFirst + child + 1] > values[beforeTheFirst + child]) {
                child++;
            }
            if (value >= values[beforeTheFirst + child]) {
                break;
            }
            values[beforeTheFirst + parent] = values[beforeTheFirst + child];
            parent = child;
            child = parent * 2;
        }
        values[beforeTheFirst + parent] = value;
    }

    private void buildTree(int[] nodes, int symbolCount) {
        int lastIndex = symbolCount - 1;
        int leaf = 0;
        int branch = 0;
        int created = 0;
        do {
            int newFrequency;
            if (leaf + 1 <= lastIndex
                    && (branch == created
                    || (nodes[leaf + 1] & FREQUENCY_MASK) <= (nodes[branch] & FREQUENCY_MASK))) {
                newFrequency = (nodes[leaf] & FREQUENCY_MASK) + (nodes[leaf + 1] & FREQUENCY_MASK);
                leaf += 2;
            } else if (branch + 2 <= created
                    && (leaf > lastIndex
                    || (nodes[branch + 1] & FREQUENCY_MASK) < (nodes[leaf] & FREQUENCY_MASK))) {
                newFrequency = (nodes[branch] & FREQUENCY_MASK) + (nodes[branch + 1] & FREQUENCY_MASK);
                nodes[branch] = (created << SYMBOL_BITS) | (nodes[branch] & SYMBOL_MASK);
                nodes[branch + 1] = (created << SYMBOL_BITS) | (nodes[branch + 1] & SYMBOL_MASK);
                branch += 2;
            } else {
                newFrequency = (nodes[leaf] & FREQUENCY_MASK) + (nodes[branch] & FREQUENCY_MASK);
                nodes[branch] = (created << SYMBOL_BITS) | (nodes[branch] & SYMBOL_MASK);
                leaf++;
                branch++;
            }
            nodes[created] = newFrequency | (nodes[created] & SYMBOL_MASK);
        } while (++created < lastIndex);
    }

    private int[] lengthCounts(int[] nodes, int root, int longestCodeword) {
        int[] lengthCounts = new int[LONGEST_CODEWORD_IN_ANY_CODE + 1];
        lengthCounts[1] = 2;
        nodes[root] &= SYMBOL_MASK;
        for (int node = root - 1; node >= 0; node--) {
            int parent = nodes[node] >>> SYMBOL_BITS;
            int depth = (nodes[parent] >>> SYMBOL_BITS) + 1;
            nodes[node] = (nodes[node] & SYMBOL_MASK) | (depth << SYMBOL_BITS);
            if (depth >= longestCodeword) {
                depth = longestCodeword;
                do {
                    depth--;
                } while (lengthCounts[depth] == 0);
            }
            lengthCounts[depth]--;
            lengthCounts[depth + 1] += 2;
        }
        return lengthCounts;
    }

    private void generateCodewords(int[] codewords, int[] lengths, int lengthsFrom, int[] lengthCounts,
            int longestCodeword, int symbolCount) {
        int sortedAt = 0;
        for (int length = longestCodeword; length >= 1; length--) {
            for (int count = lengthCounts[length]; count > 0; count--) {
                lengths[lengthsFrom + (codewords[sortedAt++] & SYMBOL_MASK)] = length;
            }
        }
        int[] nextCodewords = new int[LONGEST_CODEWORD_IN_ANY_CODE + 1];
        for (int length = 2; length <= longestCodeword; length++) {
            nextCodewords[length] = (nextCodewords[length - 1] + lengthCounts[length - 1]) << 1;
        }
        for (int symbol = 0; symbol < symbolCount; symbol++) {
            int length = lengths[lengthsFrom + symbol];
            codewords[symbol] = Integer.reverse(nextCodewords[length]++) >>> (32 - length);
        }
    }
}
