package org.jebol.domain.eval.compression.brotli;

final class BrotliClusteredMetaBlock {

    private static final int LITERAL_SYMBOLS = 256;
    private static final int LITERAL_CONTEXT_BITS = 6;
    private static final int DISTANCE_CONTEXT_BITS = 2;
    private static final int DISTANCE_SYMBOLS_A_HISTOGRAM_HOLDS = 544;
    private static final int MOST_POSTFIX_BITS = 3;

    private BrotliClusteredMetaBlock() {
    }

    record Built(BrotliMetaBlockSplit split, BrotliDistances distances) {
    }

    static Built build(byte[] data, int mask, int from, int previousByte,
            int theByteBeforeThat, int contextMode, int quality,
            BrotliCommand commands, BrotliDistances startingDistances) {

        BrotliDistances chosen = findTheCheapestDistanceParameters(commands,
                startingDistances);
        recodeDistances(commands, startingDistances, chosen);

        BrotliMetaBlockSplit built = new BrotliMetaBlockSplit();
        BrotliBlockSplitting.splitEverything(commands, data, from, mask, quality,
                built.literals, built.commands, built.distances);

        int literalHistograms =
                built.literals.countTheTypes() << LITERAL_CONTEXT_BITS;
        int distanceHistograms =
                built.distances.countTheTypes() << DISTANCE_CONTEXT_BITS;
        BrotliHistogram[] byContext =
                BrotliHistogram.makeAFreshRow(literalHistograms, LITERAL_SYMBOLS);
        BrotliHistogram[] distanceByContext = BrotliHistogram.makeAFreshRow(
                distanceHistograms, DISTANCE_SYMBOLS_A_HISTOGRAM_HOLDS);
        built.commandHistograms = BrotliHistogram.makeAFreshRow(
                built.commands.countTheTypes(), BrotliCodes.COMMAND_SYMBOLS);
        built.howManyCommandHistograms = built.commands.countTheTypes();

        countEverything(commands, data, from, mask, previousByte,
                theByteBeforeThat, contextMode, built, byContext,
                distanceByContext);

        built.literalContextMap = new int[literalHistograms];
        BrotliHistogram[] literalSurvivors =
                BrotliHistogram.makeAFreshRow(literalHistograms, LITERAL_SYMBOLS);
        built.howManyLiteralHistograms = BrotliClustering.clusterInto(byContext,
                literalHistograms, literalSurvivors, built.literalContextMap,
                LITERAL_SYMBOLS);
        built.literalHistograms = literalSurvivors;

        built.distanceContextMap = new int[distanceHistograms];
        BrotliHistogram[] distanceSurvivors = BrotliHistogram.makeAFreshRow(
                distanceHistograms, DISTANCE_SYMBOLS_A_HISTOGRAM_HOLDS);
        built.howManyDistanceHistograms = BrotliClustering.clusterInto(
                distanceByContext, distanceHistograms, distanceSurvivors,
                built.distanceContextMap, DISTANCE_SYMBOLS_A_HISTOGRAM_HOLDS);
        built.distanceHistograms = distanceSurvivors;

        return new Built(built, chosen);
    }

    private static BrotliDistances findTheCheapestDistanceParameters(
            BrotliCommand commands, BrotliDistances startingFrom) {

        BrotliHistogram scratch =
                new BrotliHistogram(DISTANCE_SYMBOLS_A_HISTOGRAM_HOLDS);
        double cheapest = 1e99;
        BrotliDistances best = startingFrom;
        boolean stillWorthCheckingTheOriginal = true;
        int directCodesTop = 0;
        for (int postfix = 0; postfix <= MOST_POSTFIX_BITS; postfix++) {
            for (; directCodesTop < 16; directCodesTop++) {
                int direct = directCodesTop << postfix;
                BrotliDistances candidate = new BrotliDistances(direct, postfix);
                if (postfix == startingFrom.postfixBits()
                        && direct == startingFrom.directCodes()) {
                    stillWorthCheckingTheOriginal = false;
                }
                double cost = costOfDistancesUnder(commands, startingFrom,
                        candidate, scratch);
                if (Double.isNaN(cost) || cost > cheapest) {
                    break;
                }
                cheapest = cost;
                best = candidate;
            }
            if (directCodesTop > 0) {
                directCodesTop--;
            }
            directCodesTop /= 2;
        }
        if (stillWorthCheckingTheOriginal) {
            double cost = costOfDistancesUnder(commands, startingFrom,
                    startingFrom, scratch);
            if (!Double.isNaN(cost) && cost < cheapest) {
                best = startingFrom;
            }
        }
        return best;
    }

    private static double costOfDistancesUnder(BrotliCommand commands,
            BrotliDistances asWritten, BrotliDistances candidate,
            BrotliHistogram scratch) {

        scratch.clear();
        double extraBits = 0.0;
        boolean unchanged = asWritten.postfixBits() == candidate.postfixBits()
                && asWritten.directCodes() == candidate.directCodes();
        for (int which = 0; which < commands.count(); which++) {
            if (commands.copyLengthAt(which) == 0
                    || commands.readCommandPrefixAt(which) < 128) {
                continue;
            }
            int prefix;
            if (unchanged) {
                prefix = commands.readDistancePrefixAt(which);
            } else {
                int code = commands.restoreTheDistanceCodeAt(which,
                        asWritten.directCodes(), asWritten.postfixBits());
                if (Integer.toUnsignedLong(code) > BrotliDistances.FURTHEST) {
                    return Double.NaN;
                }
                prefix = (int) (BrotliCommand.encodeTheDistance(code,
                        candidate.directCodes(), candidate.postfixBits()) >>> 32);
            }
            scratch.add(prefix & 0x3FF);
            extraBits += prefix >>> 10;
        }
        return BrotliHistogramCost.of(scratch, DISTANCE_SYMBOLS_A_HISTOGRAM_HOLDS)
                + extraBits;
    }

    private static void recodeDistances(BrotliCommand commands,
            BrotliDistances asWritten, BrotliDistances chosen) {

        if (asWritten.postfixBits() == chosen.postfixBits()
                && asWritten.directCodes() == chosen.directCodes()) {
            return;
        }
        for (int which = 0; which < commands.count(); which++) {
            if (commands.copyLengthAt(which) == 0
                    || commands.readCommandPrefixAt(which) < 128) {
                continue;
            }
            int code = commands.restoreTheDistanceCodeAt(which,
                    asWritten.directCodes(), asWritten.postfixBits());
            long encoded = BrotliCommand.encodeTheDistance(code,
                    chosen.directCodes(), chosen.postfixBits());
            commands.setTheDistancePrefix(which, (int) (encoded >>> 32));
            commands.setTheDistanceExtra(which, (int) encoded);
        }
    }

    private static final class Walk {

        private final BrotliBlockSplit split;
        private int whichBlock;
        private int leftInTheBlock;
        private int type;

        Walk(BrotliBlockSplit split) {
            this.split = split;
        }

        int readNextType() {
            if (leftInTheBlock == 0) {
                leftInTheBlock = split.readLengthAt(whichBlock);
                type = split.readTypeAt(whichBlock);
                whichBlock++;
            }
            leftInTheBlock--;
            return type;
        }
    }

    private static void countEverything(BrotliCommand commands, byte[] data,
            int from, int mask, int previousByteGiven, int theByteBeforeThatGiven,
            int contextMode, BrotliMetaBlockSplit built,
            BrotliHistogram[] byContext, BrotliHistogram[] distanceByContext) {

        Walk literalWalk = new Walk(built.literals);
        Walk commandWalk = new Walk(built.commands);
        Walk distanceWalk = new Walk(built.distances);
        int at = from;
        int previous = previousByteGiven;
        int beforeThat = theByteBeforeThatGiven;

        for (int which = 0; which < commands.count(); which++) {
            built.commandHistograms[commandWalk.readNextType()]
                    .add(commands.readCommandPrefixAt(which));
            for (int left = commands.insertLengthAt(which); left != 0; left--) {
                int type = literalWalk.readNextType();
                int context = (type << LITERAL_CONTEXT_BITS)
                        + BrotliContext.of(contextMode, previous, beforeThat);
                int literal = data[at & mask] & 0xFF;
                byContext[context].add(literal);
                beforeThat = previous;
                previous = literal;
                at++;
            }
            int copyLength = commands.copyLengthAt(which);
            at += copyLength;
            if (copyLength == 0) {
                continue;
            }
            beforeThat = data[(at - 2) & mask] & 0xFF;
            previous = data[(at - 1) & mask] & 0xFF;
            if (commands.readCommandPrefixAt(which) >= 128) {
                int context = (distanceWalk.readNextType() << DISTANCE_CONTEXT_BITS)
                        + commands.readDistanceContextAt(which);
                distanceByContext[context]
                        .add(commands.readDistancePrefixAt(which) & 0x3FF);
            }
        }
    }

    static void smoothForRuns(BrotliMetaBlockSplit built, int distanceCodes) {
        for (int which = 0; which < built.howManyLiteralHistograms; which++) {
            BrotliCodes.smoothCountsIntoRuns(LITERAL_SYMBOLS,
                    built.literalHistograms[which].counts());
        }
        for (int which = 0; which < built.howManyCommandHistograms; which++) {
            BrotliCodes.smoothCountsIntoRuns(BrotliCodes.COMMAND_SYMBOLS,
                    built.commandHistograms[which].counts());
        }
        for (int which = 0; which < built.howManyDistanceHistograms; which++) {
            BrotliCodes.smoothCountsIntoRuns(distanceCodes,
                    built.distanceHistograms[which].counts());
        }
    }
}
