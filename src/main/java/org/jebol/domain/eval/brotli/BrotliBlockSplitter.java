package org.jebol.domain.eval.brotli;

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
    private int mergesInARow;

    BrotliBlockSplitter(int alphabetSize, int smallestBlock,
            double splitThreshold, int howManySymbols, BrotliBlockSplit split) {

        this.alphabetSize = alphabetSize;
        this.smallestBlock = smallestBlock;
        this.splitThreshold = splitThreshold;
        this.split = split;
        this.targetBlockSize = smallestBlock;

        int mostBlocks = howManySymbols / smallestBlock + 1;
        int mostTypes = Math.min(mostBlocks, BrotliBlockSplit.MOST_TYPES_ALLOWED + 1);
        split.roomFor(mostBlocks);
        split.howManyBlocksIs(mostBlocks);
        this.histograms = BrotliHistogram.freshRow(mostTypes, alphabetSize);
        this.howManyHistogramsAreUsed = mostTypes;
        this.merged[0] = new BrotliHistogram(alphabetSize);
        this.merged[1] = new BrotliHistogram(alphabetSize);
    }

    BrotliHistogram[] histograms() {
        return histograms;
    }

    int howManyHistogramsAreUsed() {
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
            startTheFirstBlock();
        } else if (blockSize > 0) {
            decideWhatToDoWithTheBatch();
        }
        if (isFinal) {
            howManyHistogramsAreUsed = split.howManyTypes();
            split.howManyBlocksIs(howManyBlocks);
        }
    }

    private void startTheFirstBlock() {
        split.lengthIs(0, blockSize);
        split.typeIs(0, 0);
        lastEntropy[0] = BrotliCodes.bitsEntropyFlooredAtOneBitPerLiteral(
                histograms[0].counts(), alphabetSize);
        lastEntropy[1] = lastEntropy[0];
        howManyBlocks++;
        split.oneMoreType();
        currentHistogram++;
        if (currentHistogram < howManyHistogramsAreUsed) {
            histograms[currentHistogram].clear();
        }
        blockSize = 0;
    }

    private void decideWhatToDoWithTheBatch() {
        double onItsOwn = BrotliCodes.bitsEntropyFlooredAtOneBitPerLiteral(
                histograms[currentHistogram].counts(), alphabetSize);
        double[] mergedEntropy = new double[2];
        double[] costOfMerging = new double[2];
        for (int which = 0; which < 2; which++) {
            merged[which].copyFrom(histograms[currentHistogram]);
            merged[which].addAll(histograms[lastHistogram[which]]);
            mergedEntropy[which] = BrotliCodes.bitsEntropyFlooredAtOneBitPerLiteral(
                    merged[which].counts(), alphabetSize);
            costOfMerging[which] =
                    mergedEntropy[which] - onItsOwn - lastEntropy[which];
        }

        if (split.howManyTypes() < BrotliBlockSplit.MOST_TYPES_ALLOWED
                && costOfMerging[0] > splitThreshold
                && costOfMerging[1] > splitThreshold) {
            startANewType(onItsOwn);
        } else if (costOfMerging[1]
                < costOfMerging[0] - SECOND_LAST_MUST_BEAT_LAST_BY) {
            giveTheBatchToTheTypeBeforeLast(mergedEntropy[1]);
        } else {
            foldTheBatchIntoTheLastType(mergedEntropy[0]);
        }
    }

    private void startANewType(double onItsOwn) {
        split.lengthIs(howManyBlocks, blockSize);
        split.typeIs(howManyBlocks, split.howManyTypes());
        lastHistogram[1] = lastHistogram[0];
        lastHistogram[0] = split.howManyTypes();
        lastEntropy[1] = lastEntropy[0];
        lastEntropy[0] = onItsOwn;
        howManyBlocks++;
        split.oneMoreType();
        currentHistogram++;
        if (currentHistogram < howManyHistogramsAreUsed) {
            histograms[currentHistogram].clear();
        }
        blockSize = 0;
        mergesInARow = 0;
        targetBlockSize = smallestBlock;
    }

    private void giveTheBatchToTheTypeBeforeLast(double mergedEntropy) {
        split.lengthIs(howManyBlocks, blockSize);
        split.typeIs(howManyBlocks, split.typeAt(howManyBlocks - 2));
        int swapped = lastHistogram[0];
        lastHistogram[0] = lastHistogram[1];
        lastHistogram[1] = swapped;
        histograms[lastHistogram[0]].copyFrom(merged[1]);
        lastEntropy[1] = lastEntropy[0];
        lastEntropy[0] = mergedEntropy;
        howManyBlocks++;
        blockSize = 0;
        histograms[currentHistogram].clear();
        mergesInARow = 0;
        targetBlockSize = smallestBlock;
    }

    private void foldTheBatchIntoTheLastType(double mergedEntropy) {
        split.lengthGrows(howManyBlocks - 1, blockSize);
        histograms[lastHistogram[0]].copyFrom(merged[0]);
        lastEntropy[0] = mergedEntropy;
        if (split.howManyTypes() == 1) {
            lastEntropy[1] = lastEntropy[0];
        }
        blockSize = 0;
        histograms[currentHistogram].clear();
        if (++mergesInARow > 1) {
            targetBlockSize += smallestBlock;
        }
    }
}
