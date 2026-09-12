package org.jebol.domain.eval.brotli;

import java.util.Arrays;

final class BrotliBlockSplitting {

    private static final int SHORTEST_WORTH_SPLITTING = 128;
    private static final int SAMPLES_PER_HISTOGRAM_MULTIPLIER = 2;
    private static final int FEWEST_SAMPLES = 100;
    private static final int HISTOGRAMS_PER_BATCH = 64;
    private static final int MOST_TYPES_ALLOWED = 256;
    private static final int ROUNDS_AT_TEN = 3;
    private static final int ROUNDS_AT_ELEVEN = 10;

    private static final int LITERAL_SYMBOLS = 256;
    private static final int MOST_LITERAL_HISTOGRAMS = 100;
    private static final int MOST_COMMAND_HISTOGRAMS = 50;
    private static final double LITERAL_SWITCH_COST = 28.1;
    private static final double COMMAND_SWITCH_COST = 13.5;
    private static final double DISTANCE_SWITCH_COST = 14.6;
    private static final int LITERAL_STRIDE = 70;
    private static final int COMMAND_STRIDE = 40;
    private static final int DISTANCE_STRIDE = 40;
    private static final int SYMBOLS_PER_LITERAL_HISTOGRAM = 544;
    private static final int SYMBOLS_PER_COMMAND_HISTOGRAM = 530;
    private static final int SYMBOLS_PER_DISTANCE_HISTOGRAM = 544;

    private static final int DISTANCE_SYMBOLS_A_HISTOGRAM_HOLDS = 544;

    private BrotliBlockSplitting() {
    }

    private static final class TheCsOwnGeneratorWhoseSequenceTheAnswerDependsOn {
        private int seed = 7;

        int next() {
            seed *= 16807;
            return seed;
        }
    }

    private static double bitCostPricingAnAbsentSymbolAtMinusTwoBits(int count) {
        return count == 0 ? -2.0 : BrotliCodes.fastLog2(count);
    }

    static void splitEverything(BrotliCommand commands, byte[] data, int from,
                                int mask, int quality, BrotliBlockSplit literals,
                                BrotliBlockSplit commandSymbols, BrotliBlockSplit distances) {

        int rounds = quality >= 11 ? ROUNDS_AT_ELEVEN : ROUNDS_AT_TEN;

        int howManyLiterals = 0;
        for (int which = 0; which < commands.count(); which++) {
            howManyLiterals += commands.insertLengthAt(which);
        }
        int[] literalRun = new int[howManyLiterals];
        gatherLiterals(commands, data, from, mask, literalRun);
        split(literalRun, howManyLiterals, LITERAL_SYMBOLS,
                SYMBOLS_PER_LITERAL_HISTOGRAM, MOST_LITERAL_HISTOGRAMS,
                LITERAL_STRIDE, LITERAL_SWITCH_COST, rounds, literals);

        int[] commandRun = new int[commands.count()];
        for (int which = 0; which < commands.count(); which++) {
            commandRun[which] = commands.commandPrefixAt(which);
        }
        split(commandRun, commands.count(), BrotliCodes.COMMAND_SYMBOLS,
                SYMBOLS_PER_COMMAND_HISTOGRAM, MOST_COMMAND_HISTOGRAMS,
                COMMAND_STRIDE, COMMAND_SWITCH_COST, rounds, commandSymbols);

        int[] distanceRun = new int[commands.count()];
        int howManyDistances = 0;
        for (int which = 0; which < commands.count(); which++) {
            if (commands.copyLengthAt(which) != 0
                    && commands.commandPrefixAt(which) >= 128) {
                distanceRun[howManyDistances++] =
                        commands.distancePrefixAt(which) & 0x3FF;
            }
        }
        split(distanceRun, howManyDistances, DISTANCE_SYMBOLS_A_HISTOGRAM_HOLDS,
                SYMBOLS_PER_DISTANCE_HISTOGRAM, MOST_COMMAND_HISTOGRAMS,
                DISTANCE_STRIDE, DISTANCE_SWITCH_COST, rounds, distances);
    }

    private static void gatherLiterals(BrotliCommand commands, byte[] data,
            int from, int mask, int[] into) {

        int filled = 0;
        int at = from & mask;
        for (int which = 0; which < commands.count(); which++) {
            int insertLength = commands.insertLengthAt(which);
            for (int each = 0; each < insertLength; each++) {
                into[filled++] = data[(at + each) & mask] & 0xFF;
            }
            at = (at + insertLength + commands.copyLengthAt(which)) & mask;
        }
    }

    private static void split(int[] symbols, int howMany, int alphabetSize,
            int symbolsPerHistogram, int mostHistograms, int stride,
            double switchCost, int rounds, BrotliBlockSplit split) {

        int howManyHistograms = Math.min(howMany / symbolsPerHistogram + 1,
                mostHistograms);
        if (howMany == 0) {
            split.oneMoreType();
            return;
        }
        if (howMany < SHORTEST_WORTH_SPLITTING) {
            split.roomFor(split.howManyBlocks() + 1);
            split.oneMoreType();
            split.typeIs(split.howManyBlocks(), 0);
            split.lengthIs(split.howManyBlocks(), howMany);
            split.howManyBlocksIs(split.howManyBlocks() + 1);
            return;
        }

        BrotliHistogram[] histograms =
                BrotliHistogram.freshRow(howManyHistograms + 1, alphabetSize);
        seedFromSamples(symbols, howMany, stride, howManyHistograms, histograms);
        refineFromSamples(symbols, howMany, stride, howManyHistograms, histograms);

        int[] whichHistogram = new int[howMany];
        int howManyBlocks = 0;
        int howManyLeft = howManyHistograms;
        for (int round = 0; round < rounds; round++) {
            howManyBlocks = assign(symbols, howMany, switchCost, howManyLeft,
                    histograms, whichHistogram, alphabetSize);
            howManyLeft = renumber(whichHistogram, howMany, howManyLeft);
            rebuild(symbols, howMany, whichHistogram, howManyLeft, histograms);
        }
        clusterTheBlocks(symbols, howMany, howManyBlocks, whichHistogram, split,
                alphabetSize);
    }

    private static void seedFromSamples(int[] symbols, int howMany, int stride,
            int howManyHistograms, BrotliHistogram[] histograms) {

        TheCsOwnGeneratorWhoseSequenceTheAnswerDependsOn rolling =
                new TheCsOwnGeneratorWhoseSequenceTheAnswerDependsOn();
        int blockLength = howMany / howManyHistograms;
        for (int which = 0; which < howManyHistograms; which++) {
            histograms[which].clear();
            long at = (long) howMany * which / howManyHistograms;
            if (which != 0) {
                at += Integer.toUnsignedLong(rolling.next()) % blockLength;
            }
            if (at + stride >= howMany) {
                at = howMany - stride - 1L;
            }
            for (int each = 0; each < stride; each++) {
                histograms[which].add(symbols[(int) at + each]);
            }
        }
    }

    private static void refineFromSamples(int[] symbols, int howMany, int stride,
            int howManyHistograms, BrotliHistogram[] histograms) {

        long howManySamples = (long) SAMPLES_PER_HISTOGRAM_MULTIPLIER * howMany
                / stride + FEWEST_SAMPLES;
        howManySamples = (howManySamples + howManyHistograms - 1)
                / howManyHistograms * howManyHistograms;
        TheCsOwnGeneratorWhoseSequenceTheAnswerDependsOn rolling =
                new TheCsOwnGeneratorWhoseSequenceTheAnswerDependsOn();
        BrotliHistogram sample = histograms[howManyHistograms];
        for (long each = 0; each < howManySamples; each++) {
            sample.clear();
            int here = stride;
            int at = 0;
            if (here >= howMany) {
                here = howMany;
            } else {
                at = (int) (Integer.toUnsignedLong(rolling.next())
                        % (howMany - here + 1));
            }
            for (int step = 0; step < here; step++) {
                sample.add(symbols[at + step]);
            }
            histograms[(int) (each % howManyHistograms)].addAll(sample);
        }
    }

    private static int assign(int[] symbols, int howMany, double switchCost,
            int howManyHistograms, BrotliHistogram[] histograms,
            int[] whichHistogram, int alphabetSize) {

        if (howManyHistograms <= 1) {
            Arrays.fill(whichHistogram, 0, howMany, 0);
            return 1;
        }
        int bitmapWidth = (howManyHistograms + 7) >> 3;
        double[] costOfASymbol = new double[alphabetSize * howManyHistograms];
        for (int which = 0; which < howManyHistograms; which++) {
            costOfASymbol[which] =
                    BrotliCodes.fastLog2(histograms[which].total());
        }
        for (int symbol = alphabetSize; symbol != 0; ) {
            symbol--;
            for (int which = 0; which < howManyHistograms; which++) {
                costOfASymbol[symbol * howManyHistograms + which] =
                        costOfASymbol[which]
                                - bitCostPricingAnAbsentSymbolAtMinusTwoBits(
                                        histograms[which].counts()[symbol]);
            }
        }

        double[] extraCost = new double[howManyHistograms];
        byte[] wouldSwitchHere = new byte[howMany * bitmapWidth];
        int howManyBlocks = 1;
        for (int at = 0; at < howMany; at++) {
            int bitmapAt = at * bitmapWidth;
            int costAt = symbols[at] * howManyHistograms;
            double cheapest = 1e99;
            double switchCostHere = switchCost;
            for (int which = 0; which < howManyHistograms; which++) {
                extraCost[which] += costOfASymbol[costAt + which];
                if (extraCost[which] < cheapest) {
                    cheapest = extraCost[which];
                    whichHistogram[at] = which;
                }
            }
            if (at < 2000) {
                switchCostHere *= 0.77 + 0.07 / 2000 * (double) at;
            }
            for (int which = 0; which < howManyHistograms; which++) {
                extraCost[which] -= cheapest;
                if (extraCost[which] >= switchCostHere) {
                    extraCost[which] = switchCostHere;
                    wouldSwitchHere[bitmapAt + (which >> 3)] |=
                            (byte) (1 << (which & 7));
                }
            }
        }

        int at = howMany - 1;
        int bitmapAt = at * bitmapWidth;
        int using = whichHistogram[at];
        while (at > 0) {
            byte wanted = (byte) (1 << (using & 7));
            at--;
            bitmapAt -= bitmapWidth;
            if ((wouldSwitchHere[bitmapAt + (using >> 3)] & wanted) != 0
                    && using != whichHistogram[at]) {
                using = whichHistogram[at];
                howManyBlocks++;
            }
            whichHistogram[at] = using;
        }
        return howManyBlocks;
    }

    private static int renumber(int[] whichHistogram, int howMany,
            int howManyHistograms) {

        int[] newName = new int[howManyHistograms];
        Arrays.fill(newName, -1);
        int nextName = 0;
        for (int at = 0; at < howMany; at++) {
            if (newName[whichHistogram[at]] == -1) {
                newName[whichHistogram[at]] = nextName;
                nextName++;
            }
        }
        for (int at = 0; at < howMany; at++) {
            whichHistogram[at] = newName[whichHistogram[at]];
        }
        return nextName;
    }

    private static void rebuild(int[] symbols, int howMany, int[] whichHistogram,
            int howManyHistograms, BrotliHistogram[] histograms) {

        for (int which = 0; which < howManyHistograms; which++) {
            histograms[which].clear();
        }
        for (int at = 0; at < howMany; at++) {
            histograms[whichHistogram[at]].add(symbols[at]);
        }
    }

    private static void clusterTheBlocks(int[] symbols, int howMany,
            int howManyBlocks, int[] whichHistogram, BrotliBlockSplit split,
            int alphabetSize) {

        int[] blockLengths = new int[howManyBlocks];
        int block = 0;
        for (int at = 0; at < howMany; at++) {
            blockLengths[block]++;
            if (at + 1 == howMany || whichHistogram[at] != whichHistogram[at + 1]) {
                block++;
            }
        }

        int[] usedBy = new int[howManyBlocks];
        BrotliHistogram scratch = new BrotliHistogram(alphabetSize);
        BrotliHistogram batchScratch = new BrotliHistogram(alphabetSize);

        int roomForPairs = HISTOGRAMS_PER_BATCH * HISTOGRAMS_PER_BATCH / 2;
        BrotliClustering.Queue queue = new BrotliClustering.Queue(roomForPairs);
        BrotliHistogram[] batch =
                BrotliHistogram.freshRow(Math.min(howManyBlocks,
                        HISTOGRAMS_PER_BATCH), alphabetSize);
        int[] sizes = new int[HISTOGRAMS_PER_BATCH];
        int[] survivorsOfTheBatch = new int[HISTOGRAMS_PER_BATCH];
        int[] namesInTheBatch = new int[HISTOGRAMS_PER_BATCH];
        int[] renamed = new int[HISTOGRAMS_PER_BATCH];

        BrotliHistogram[] survivors =
                BrotliHistogram.freshRow(Math.max(1, howManyBlocks), alphabetSize);
        int[] survivorSizes = new int[survivors.length];
        int howManySurvivors = 0;
        int at = 0;

        for (int first = 0; first < howManyBlocks; first += HISTOGRAMS_PER_BATCH) {
            int howManyHere = Math.min(howManyBlocks - first, HISTOGRAMS_PER_BATCH);
            for (int each = 0; each < howManyHere; each++) {
                batch[each].clear();
                for (int step = 0; step < blockLengths[first + each]; step++) {
                    batch[each].add(symbols[at++]);
                }
                batch[each].costIs(BrotliHistogramCost.of(batch[each], alphabetSize));
                survivorsOfTheBatch[each] = each;
                namesInTheBatch[each] = each;
                sizes[each] = 1;
            }
            int left = BrotliClustering.combine(batch, batchScratch, sizes,
                    namesInTheBatch, 0, survivorsOfTheBatch, 0, queue,
                    howManyHere, howManyHere, HISTOGRAMS_PER_BATCH,
                    roomForPairs, alphabetSize);
            for (int each = 0; each < left; each++) {
                survivors[howManySurvivors + each]
                        .copyFrom(batch[survivorsOfTheBatch[each]]);
                survivorSizes[howManySurvivors + each] =
                        sizes[survivorsOfTheBatch[each]];
                renamed[survivorsOfTheBatch[each]] = each;
            }
            for (int each = 0; each < howManyHere; each++) {
                usedBy[first + each] =
                        howManySurvivors + renamed[namesInTheBatch[each]];
            }
            howManySurvivors += left;
        }

        int roomForMorePairs = Math.min(64 * howManySurvivors,
                (howManySurvivors / 2) * howManySurvivors);
        BrotliClustering.Queue wider = new BrotliClustering.Queue(
                Math.max(roomForMorePairs, roomForPairs));
        int[] whichSurvive = new int[howManySurvivors];
        for (int each = 0; each < howManySurvivors; each++) {
            whichSurvive[each] = each;
        }
        int howManyFinal = BrotliClustering.combine(survivors, scratch,
                survivorSizes, usedBy, 0, whichSurvive, 0, wider,
                howManySurvivors, howManyBlocks, MOST_TYPES_ALLOWED,
                roomForMorePairs, alphabetSize);

        int[] newName = new int[howManySurvivors];
        Arrays.fill(newName, -1);
        int nextName = 0;
        at = 0;
        for (int which = 0; which < howManyBlocks; which++) {
            scratch.clear();
            for (int step = 0; step < blockLengths[which]; step++) {
                scratch.add(symbols[at++]);
            }
            int best = which == 0 ? usedBy[0] : usedBy[which - 1];
            double bestCost = BrotliClustering.costOfMoving(scratch,
                    survivors[best], batchScratch, alphabetSize);
            for (int each = 0; each < howManyFinal; each++) {
                double cost = BrotliClustering.costOfMoving(scratch,
                        survivors[whichSurvive[each]], batchScratch, alphabetSize);
                if (cost < bestCost) {
                    bestCost = cost;
                    best = whichSurvive[each];
                }
            }
            usedBy[which] = best;
            if (newName[best] == -1) {
                newName[best] = nextName;
                nextName++;
            }
        }

        split.roomFor(howManyBlocks);
        int runLength = 0;
        int written = 0;
        int highestType = 0;
        for (int which = 0; which < howManyBlocks; which++) {
            runLength += blockLengths[which];
            if (which + 1 == howManyBlocks || usedBy[which] != usedBy[which + 1]) {
                int type = newName[usedBy[which]];
                split.typeIs(written, type);
                split.lengthIs(written, runLength);
                highestType = Math.max(highestType, type);
                runLength = 0;
                written++;
            }
        }
        split.howManyBlocksIs(written);
        split.howManyTypesIs(highestType + 1);
    }
}
