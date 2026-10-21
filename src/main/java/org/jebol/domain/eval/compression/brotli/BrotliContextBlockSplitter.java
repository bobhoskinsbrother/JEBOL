package org.jebol.domain.eval.compression.brotli;

final class BrotliContextBlockSplitter {

    private static final double SECOND_LAST_MUST_BEAT_LAST_BY = 20.0;

    private final int alphabetSize;
    private final int howManyContexts;
    private final int mostTypesAllowed;
    private final int smallestBlock;
    private final double splitThreshold;
    private final BrotliBlockSplit split;
    private final BrotliHistogram[] histograms;
    private final BrotliHistogram[] merged;

    private int howManyHistogramsAreUsed;
    private int howManyBlocks;
    private int targetBlockSize;
    private int blockSize;
    private int currentHistogram;
    private final int[] lastHistogram = new int[2];
    private final double[] lastEntropy;
    private int mergesInARow;

    BrotliContextBlockSplitter(int alphabetSize, int howManyContexts,
            int smallestBlock, double splitThreshold, int howManySymbols,
            BrotliBlockSplit split) {

        this.alphabetSize = alphabetSize;
        this.howManyContexts = howManyContexts;
        this.mostTypesAllowed = BrotliBlockSplit.MOST_TYPES_ALLOWED / howManyContexts;
        this.smallestBlock = smallestBlock;
        this.splitThreshold = splitThreshold;
        this.split = split;
        this.targetBlockSize = smallestBlock;
        this.lastEntropy = new double[2 * howManyContexts];
        this.merged = BrotliHistogram.makeFreshRow(2 * howManyContexts, alphabetSize);

        int mostBlocks = howManySymbols / smallestBlock + 1;
        int mostTypes = Math.min(mostBlocks, mostTypesAllowed + 1);
        split.makeRoomFor(mostBlocks);
        split.setBlockCount(mostBlocks);
        this.howManyHistogramsAreUsed = mostTypes * howManyContexts;
        this.histograms = BrotliHistogram.makeFreshRow(
                howManyHistogramsAreUsed, alphabetSize);
    }

    BrotliHistogram[] histograms() {
        return histograms;
    }

    int countHistogramsUsed() {
        return howManyHistogramsAreUsed;
    }

    void add(int symbol, int context) {
        histograms[currentHistogram + context].add(symbol);
        blockSize++;
        if (blockSize == targetBlockSize) {
            finishBlock(false);
        }
    }

    void finishBlock(boolean isFinal) {
        if (blockSize < smallestBlock) {
            blockSize = smallestBlock;
        }
        if (howManyBlocks == 0) {
            startFirstBlock();
        } else if (blockSize > 0) {
            decideWhatToDoWithTheBatch();
        }
        if (isFinal) {
            howManyHistogramsAreUsed = split.countTypes() * howManyContexts;
            split.setBlockCount(howManyBlocks);
        }
    }

    private void startFirstBlock() {
        split.setLength(0, blockSize);
        split.setType(0, 0);
        for (int context = 0; context < howManyContexts; context++) {
            lastEntropy[context] = BrotliCodes.measureBitsEntropy(
                    histograms[context].counts(), alphabetSize);
            lastEntropy[howManyContexts + context] = lastEntropy[context];
        }
        howManyBlocks++;
        split.addType();
        currentHistogram += howManyContexts;
        clearCurrentHistograms();
        blockSize = 0;
    }

    private void clearCurrentHistograms() {
        if (currentHistogram >= howManyHistogramsAreUsed) {
            return;
        }
        for (int context = 0; context < howManyContexts; context++) {
            histograms[currentHistogram + context].clear();
        }
    }

    private void decideWhatToDoWithTheBatch() {
        double[] onItsOwn = new double[howManyContexts];
        double[] mergedEntropy = new double[2 * howManyContexts];
        double[] costOfMerging = new double[2];
        for (int context = 0; context < howManyContexts; context++) {
            int mine = currentHistogram + context;
            onItsOwn[context] = BrotliCodes.measureBitsEntropy(
                    histograms[mine].counts(), alphabetSize);
            for (int which = 0; which < 2; which++) {
                int slot = which * howManyContexts + context;
                merged[slot].copyFrom(histograms[mine]);
                merged[slot].addAll(histograms[lastHistogram[which] + context]);
                mergedEntropy[slot] = BrotliCodes.measureBitsEntropy(
                        merged[slot].counts(), alphabetSize);
                costOfMerging[which] +=
                        mergedEntropy[slot] - onItsOwn[context] - lastEntropy[slot];
            }
        }

        if (split.countTypes() < mostTypesAllowed
                && costOfMerging[0] > splitThreshold
                && costOfMerging[1] > splitThreshold) {
            startNewType(onItsOwn);
        } else if (costOfMerging[1]
                < costOfMerging[0] - SECOND_LAST_MUST_BEAT_LAST_BY) {
            giveTheBatchToTheTypeBeforeLast(mergedEntropy);
        } else {
            foldBatchIntoLastType(mergedEntropy);
        }
    }

    private void startNewType(double[] onItsOwn) {
        split.setLength(howManyBlocks, blockSize);
        split.setType(howManyBlocks, split.countTypes());
        lastHistogram[1] = lastHistogram[0];
        lastHistogram[0] = split.countTypes() * howManyContexts;
        for (int context = 0; context < howManyContexts; context++) {
            lastEntropy[howManyContexts + context] = lastEntropy[context];
            lastEntropy[context] = onItsOwn[context];
        }
        howManyBlocks++;
        split.addType();
        currentHistogram += howManyContexts;
        clearCurrentHistograms();
        blockSize = 0;
        mergesInARow = 0;
        targetBlockSize = smallestBlock;
    }

    private void giveTheBatchToTheTypeBeforeLast(double[] mergedEntropy) {
        split.setLength(howManyBlocks, blockSize);
        split.setType(howManyBlocks, split.readTypeAt(howManyBlocks - 2));
        int swapped = lastHistogram[0];
        lastHistogram[0] = lastHistogram[1];
        lastHistogram[1] = swapped;
        for (int context = 0; context < howManyContexts; context++) {
            histograms[lastHistogram[0] + context]
                    .copyFrom(merged[howManyContexts + context]);
            lastEntropy[howManyContexts + context] = lastEntropy[context];
            lastEntropy[context] = mergedEntropy[howManyContexts + context];
            histograms[currentHistogram + context].clear();
        }
        howManyBlocks++;
        blockSize = 0;
        mergesInARow = 0;
        targetBlockSize = smallestBlock;
    }

    private void foldBatchIntoLastType(double[] mergedEntropy) {
        split.growLength(howManyBlocks - 1, blockSize);
        for (int context = 0; context < howManyContexts; context++) {
            histograms[lastHistogram[0] + context].copyFrom(merged[context]);
            lastEntropy[context] = mergedEntropy[context];
            if (split.countTypes() == 1) {
                lastEntropy[howManyContexts + context] = lastEntropy[context];
            }
            histograms[currentHistogram + context].clear();
        }
        blockSize = 0;
        if (++mergesInARow > 1) {
            targetBlockSize += smallestBlock;
        }
    }
}
