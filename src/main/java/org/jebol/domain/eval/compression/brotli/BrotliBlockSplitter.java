package org.jebol.domain.eval.compression.brotli;

final class BrotliBlockSplitter {

    private static final double SECOND_LAST_MUST_BEAT_LAST_BY = 20.0;

    private final int alphabetSize;
    private final int smallestBlock;
    private final double splitThreshold;
    private final BrotliBlockSplit split;
    private final BrotliHistogram[] histograms;
    private final BrotliHistogram[] merged = new BrotliHistogram[2];

    private int howManyHistogramsAreUsed;
    private int howManyBlocks;
    private int targetBlockSize;
    private int blockSize;
    private int currentHistogram;
    private final int[] lastHistogram = new int[2];
    private final double[] lastEntropy = new double[2];
    private int mergesInRow;

    BrotliBlockSplitter(int alphabetSize, int smallestBlock,
            double splitThreshold, int howManySymbols, BrotliBlockSplit split) {

        this.alphabetSize = alphabetSize;
        this.smallestBlock = smallestBlock;
        this.splitThreshold = splitThreshold;
        this.split = split;
        this.targetBlockSize = smallestBlock;

        int mostBlocks = howManySymbols / smallestBlock + 1;
        int mostTypes = Math.min(mostBlocks, BrotliBlockSplit.MOST_TYPES_ALLOWED + 1);
        split.makeRoomFor(mostBlocks);
        split.setBlockCount(mostBlocks);
        this.histograms = BrotliHistogram.makeFreshRow(mostTypes, alphabetSize);
        this.howManyHistogramsAreUsed = mostTypes;
        this.merged[0] = new BrotliHistogram(alphabetSize);
        this.merged[1] = new BrotliHistogram(alphabetSize);
    }

    BrotliHistogram[] histograms() {
        return histograms;
    }

    int countHistogramsUsed() {
        return howManyHistogramsAreUsed;
    }

    void add(int symbol) {
        histograms[currentHistogram].add(symbol);
        blockSize++;
        if (blockSize == targetBlockSize) {
            finishBlock(false);
        }
    }

    void finishBlock(boolean isFinal) {
        blockSize = Math.max(blockSize, smallestBlock);
        if (howManyBlocks == 0) {
            startFirstBlock();
        } else if (blockSize > 0) {
            assignBatch();
        }
        if (isFinal) {
            howManyHistogramsAreUsed = split.countTypes();
            split.setBlockCount(howManyBlocks);
        }
    }

    private void startFirstBlock() {
        split.setLength(0, blockSize);
        split.setType(0, 0);
        lastEntropy[0] = BrotliCodes.measureBitsEntropy(
                histograms[0].counts(), alphabetSize);
        lastEntropy[1] = lastEntropy[0];
        howManyBlocks++;
        split.addType();
        currentHistogram++;
        if (currentHistogram < howManyHistogramsAreUsed) {
            histograms[currentHistogram].clear();
        }
        blockSize = 0;
    }

    private void assignBatch() {
        double onItsOwn = BrotliCodes.measureBitsEntropy(
                histograms[currentHistogram].counts(), alphabetSize);
        double[] mergedEntropy = new double[2];
        double[] costOfMerging = new double[2];
        for (int which = 0; which < 2; which++) {
            merged[which].copyFrom(histograms[currentHistogram]);
            merged[which].addAll(histograms[lastHistogram[which]]);
            mergedEntropy[which] = BrotliCodes.measureBitsEntropy(
                    merged[which].counts(), alphabetSize);
            costOfMerging[which] =
                    mergedEntropy[which] - onItsOwn - lastEntropy[which];
        }

        if (split.countTypes() < BrotliBlockSplit.MOST_TYPES_ALLOWED
                && costOfMerging[0] > splitThreshold
                && costOfMerging[1] > splitThreshold) {
            startNewType(onItsOwn);
        } else if (costOfMerging[1]
                < costOfMerging[0] - SECOND_LAST_MUST_BEAT_LAST_BY) {
            giveBatchToTypeBeforeLast(mergedEntropy[1]);
        } else {
            foldBatchIntoLastType(mergedEntropy[0]);
        }
    }

    private void startNewType(double onItsOwn) {
        split.setLength(howManyBlocks, blockSize);
        split.setType(howManyBlocks, split.countTypes());
        lastHistogram[1] = lastHistogram[0];
        lastHistogram[0] = split.countTypes();
        lastEntropy[1] = lastEntropy[0];
        lastEntropy[0] = onItsOwn;
        howManyBlocks++;
        split.addType();
        currentHistogram++;
        if (currentHistogram < howManyHistogramsAreUsed) {
            histograms[currentHistogram].clear();
        }
        blockSize = 0;
        mergesInRow = 0;
        targetBlockSize = smallestBlock;
    }

    private void giveBatchToTypeBeforeLast(double mergedEntropy) {
        split.setLength(howManyBlocks, blockSize);
        split.setType(howManyBlocks, split.readTypeAt(howManyBlocks - 2));
        int swapped = lastHistogram[0];
        lastHistogram[0] = lastHistogram[1];
        lastHistogram[1] = swapped;
        histograms[lastHistogram[0]].copyFrom(merged[1]);
        lastEntropy[1] = lastEntropy[0];
        lastEntropy[0] = mergedEntropy;
        howManyBlocks++;
        blockSize = 0;
        histograms[currentHistogram].clear();
        mergesInRow = 0;
        targetBlockSize = smallestBlock;
    }

    private void foldBatchIntoLastType(double mergedEntropy) {
        split.growLength(howManyBlocks - 1, blockSize);
        histograms[lastHistogram[0]].copyFrom(merged[0]);
        lastEntropy[0] = mergedEntropy;
        if (split.countTypes() == 1) {
            lastEntropy[1] = lastEntropy[0];
        }
        blockSize = 0;
        histograms[currentHistogram].clear();
        if (++mergesInRow > 1) {
            targetBlockSize += smallestBlock;
        }
    }
}
