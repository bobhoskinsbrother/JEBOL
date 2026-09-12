package org.jebol.domain.eval.brotli;

final class BrotliMetaBlock {

    private static final int LITERAL_SYMBOLS = 256;
    private static final int LITERAL_CONTEXT_BITS = 6;
    private static final int DISTANCE_SYMBOLS = 64;

    private static final int SMALLEST_LITERAL_BLOCK = 512;
    private static final int SMALLEST_COMMAND_BLOCK = 1024;
    private static final int SMALLEST_DISTANCE_BLOCK = 512;
    private static final double LITERAL_SPLIT_THRESHOLD = 400.0;
    private static final double COMMAND_SPLIT_THRESHOLD = 500.0;
    private static final double DISTANCE_SPLIT_THRESHOLD = 100.0;

    private static final int SHORTEST_INPUT_WORTH_CONTEXT_MODELLING = 64;
    private static final int QUALITY_THAT_FIRST_MODELS_CONTEXT = 5;
    private static final int QUALITY_THAT_FIRST_ALLOWS_THREE_CONTEXTS = 7;
    private static final double SAVING_THAT_MAKES_CONTEXTS_WORTH_IT = 0.2;
    private static final int HOW_OFTEN_THE_INPUT_IS_SAMPLED = 4096;
    private static final int HOW_MUCH_OF_IT_IS_LOOKED_AT = 64;

    private static final int[] SPACE_THEN_ANYTHING_ELSE_THEN_A_CONTINUATION_BYTE =
            {0, 0, 1, 2};

    private static final int[] TWO_CONTEXTS = contextMapOf(0, 0, 1, 1);
    private static final int[] THREE_CONTEXTS = contextMapOf(1, 1, 2, 2);

    private static final int INPUT_LARGE_ENOUGH_FOR_THIRTEEN_CONTEXTS = 1 << 20;
    private static final int THIRTEEN_CONTEXTS_COUNT = 13;

    private static final int[] THIRTEEN_CONTEXTS = {
            11, 11, 12, 12,
            0, 0, 0, 0,
            1, 1, 9, 9,
            2, 2, 2, 2,
            1, 1, 1, 1,
            8, 3, 3, 3,
            1, 1, 1, 1,
            2, 2, 2, 2,
            8, 4, 4, 4,
            8, 7, 4, 4,
            8, 0, 0, 0,
            3, 3, 3, 3,
            5, 5, 10, 5,
            5, 5, 10, 5,
            6, 6, 6, 6,
            6, 6, 6, 6,
    };

    private BrotliMetaBlock() {
    }

    private static int[] contextMapOf(int first, int second, int third,
            int fourth) {

        int[] map = new int[1 << LITERAL_CONTEXT_BITS];
        map[0] = first;
        map[1] = second;
        map[2] = third;
        map[3] = fourth;
        return map;
    }

    static int[] contextMapFor(byte[] data, int mask, int from, int length,
            int quality, int howMuchInputThereIs) {

        if (quality < QUALITY_THAT_FIRST_MODELS_CONTEXT
                || length < SHORTEST_INPUT_WORTH_CONTEXT_MODELLING) {
            return null;
        }
        if (howMuchInputThereIs >= INPUT_LARGE_ENOUGH_FOR_THIRTEEN_CONTEXTS
                && thirteenContextsAreWorthIt(data, mask, from, length)) {
            return THIRTEEN_CONTEXTS;
        }
        int[] pairs = new int[9];
        int end = from + length;
        for (int at = from; at + HOW_MUCH_OF_IT_IS_LOOKED_AT <= end;
                at += HOW_OFTEN_THE_INPUT_IS_SAMPLED) {
            int stretchEnd = at + HOW_MUCH_OF_IT_IS_LOOKED_AT;
            int before = SPACE_THEN_ANYTHING_ELSE_THEN_A_CONTINUATION_BYTE[(data[at & mask] & 0xFF) >> 6] * 3;
            for (int pos = at + 1; pos < stretchEnd; pos++) {
                int kind = SPACE_THEN_ANYTHING_ELSE_THEN_A_CONTINUATION_BYTE[(data[pos & mask] & 0xFF) >> 6];
                pairs[before + kind]++;
                before = kind * 3;
            }
        }
        return chooseContextMap(quality, pairs);
    }

    private static boolean thirteenContextsAreWorthIt(byte[] data, int mask,
            int from, int length) {

        int[] regardless = new int[32];
        int[] byContext = new int[THIRTEEN_CONTEXTS_COUNT << 5];
        long total = 0;
        int end = from + length;
        for (int at = from; at + HOW_MUCH_OF_IT_IS_LOOKED_AT <= end;
                at += HOW_OFTEN_THE_INPUT_IS_SAMPLED) {
            int stretchEnd = at + HOW_MUCH_OF_IT_IS_LOOKED_AT;
            int beforeThat = data[at & mask] & 0xFF;
            int previous = data[(at + 1) & mask] & 0xFF;
            for (int pos = at + 2; pos < stretchEnd; pos++) {
                int literal = data[pos & mask] & 0xFF;
                int context = THIRTEEN_CONTEXTS[
                        BrotliContext.of(BrotliContext.UTF8, previous, beforeThat)];
                total++;
                regardless[literal >> 3]++;
                byContext[(context << 5) + (literal >> 3)]++;
                beforeThat = previous;
                previous = literal;
            }
        }
        if (total == 0) {
            return false;
        }
        double withNoContext = estimatedEntropy(regardless, 0, 32);
        double withContexts = 0;
        for (int context = 0; context < THIRTEEN_CONTEXTS_COUNT; context++) {
            withContexts += estimatedEntropy(byContext, context << 5, 32);
        }
        double perSymbol = 1.0 / (double) total;
        withNoContext *= perSymbol;
        withContexts *= perSymbol;
        return withContexts <= 3.0
                && withNoContext - withContexts >= SAVING_THAT_MAKES_CONTEXTS_WORTH_IT;
    }

    private static int[] chooseContextMap(int quality, int[] pairs) {
        int[] alone = new int[3];
        int[] byWhatCameBefore = new int[6];
        for (int which = 0; which < 9; which++) {
            alone[which % 3] += pairs[which];
            byWhatCameBefore[which % 6] += pairs[which];
        }
        double withNoContext = estimatedEntropy(alone, 0, 3);
        double withTwoContexts = estimatedEntropy(byWhatCameBefore, 0, 3)
                + estimatedEntropy(byWhatCameBefore, 3, 3);
        double withThreeContexts = 0;
        for (int which = 0; which < 3; which++) {
            withThreeContexts += estimatedEntropy(pairs, 3 * which, 3);
        }

        long total = alone[0] + alone[1] + alone[2];
        double perSymbol = 1.0 / (double) total;
        withNoContext *= perSymbol;
        withTwoContexts *= perSymbol;
        withThreeContexts *= perSymbol;

        if (quality < QUALITY_THAT_FIRST_ALLOWS_THREE_CONTEXTS) {
            withThreeContexts = withNoContext * 10;
        }
        if (withNoContext - withTwoContexts < SAVING_THAT_MAKES_CONTEXTS_WORTH_IT
                && withNoContext - withThreeContexts
                        < SAVING_THAT_MAKES_CONTEXTS_WORTH_IT) {
            return null;
        }
        if (withTwoContexts - withThreeContexts < 0.02) {
            return TWO_CONTEXTS;
        }
        return THREE_CONTEXTS;
    }

    private static double estimatedEntropy(int[] population, int at, int size) {
        long total = 0;
        double answer = 0;
        for (int each = 0; each < size; each++) {
            int count = population[at + each];
            total += count;
            answer += (double) count * BrotliCodes.fastLog2(count);
        }
        return (double) total * BrotliCodes.fastLog2(total) - answer;
    }

    static BrotliMetaBlockSplit builtGreedily(byte[] ringBuffer, int mask,
                                              int from, int previousByte, int theByteBeforeThat,
                                              int[] contextMap, BrotliCommand commands, BrotliDistances distances) {

        BrotliMetaBlockSplit built = new BrotliMetaBlockSplit();
        int howManyContexts = contextMap == null
                ? 1
                : highestIn(contextMap) + 1;
        int howManyLiterals = 0;
        for (int which = 0; which < commands.count(); which++) {
            howManyLiterals += commands.insertLengthAt(which);
        }

        BrotliBlockSplitter plainLiterals = null;
        BrotliContextBlockSplitter literalsByContext = null;
        if (howManyContexts == 1) {
            plainLiterals = new BrotliBlockSplitter(LITERAL_SYMBOLS,
                    SMALLEST_LITERAL_BLOCK, LITERAL_SPLIT_THRESHOLD,
                    howManyLiterals, built.literals);
        } else {
            literalsByContext = new BrotliContextBlockSplitter(LITERAL_SYMBOLS,
                    howManyContexts, SMALLEST_LITERAL_BLOCK,
                    LITERAL_SPLIT_THRESHOLD, howManyLiterals, built.literals);
        }
        BrotliBlockSplitter commandSymbols = new BrotliBlockSplitter(
                BrotliCodes.COMMAND_SYMBOLS, SMALLEST_COMMAND_BLOCK,
                COMMAND_SPLIT_THRESHOLD, commands.count(), built.commands);
        BrotliBlockSplitter distanceSymbols = new BrotliBlockSplitter(
                DISTANCE_SYMBOLS, SMALLEST_DISTANCE_BLOCK,
                DISTANCE_SPLIT_THRESHOLD, commands.count(), built.distances);

        int at = from;
        int previous = previousByte;
        int beforeThat = theByteBeforeThat;
        for (int which = 0; which < commands.count(); which++) {
            commandSymbols.add(commands.commandPrefixAt(which));
            for (int left = commands.insertLengthAt(which); left != 0; left--) {
                int literal = ringBuffer[at & mask] & 0xFF;
                if (howManyContexts == 1) {
                    plainLiterals.add(literal);
                } else {
                    int context = BrotliContext.of(BrotliContext.UTF8, previous,
                            beforeThat);
                    literalsByContext.add(literal, contextMap[context]);
                }
                beforeThat = previous;
                previous = literal;
                at++;
            }
            int copyLength = commands.copyLengthAt(which);
            at += copyLength;
            if (copyLength != 0) {
                beforeThat = ringBuffer[(at - 2) & mask] & 0xFF;
                previous = ringBuffer[(at - 1) & mask] & 0xFF;
                if (commands.commandPrefixAt(which) >= 128) {
                    distanceSymbols.add(commands.distancePrefixAt(which) & 0x3FF);
                }
            }
        }

        if (howManyContexts == 1) {
            plainLiterals.finishBlock(true);
            built.literalHistograms = plainLiterals.histograms();
            built.howManyLiteralHistograms = plainLiterals.howManyHistogramsAreUsed();
        } else {
            literalsByContext.finishBlock(true);
            built.literalHistograms = literalsByContext.histograms();
            built.howManyLiteralHistograms =
                    literalsByContext.howManyHistogramsAreUsed();
            built.literalContextMap = spreadOverBlockTypes(contextMap,
                    howManyContexts, built.literals.howManyTypes());
        }
        commandSymbols.finishBlock(true);
        built.commandHistograms = commandSymbols.histograms();
        built.howManyCommandHistograms = commandSymbols.howManyHistogramsAreUsed();
        distanceSymbols.finishBlock(true);
        built.distanceHistograms = distanceSymbols.histograms();
        built.howManyDistanceHistograms =
                distanceSymbols.howManyHistogramsAreUsed();

        optimiseHistogramsForRuns(built, distances.alphabetSize());
        return built;
    }

    private static int highestIn(int[] map) {
        int highest = 0;
        for (int each : map) {
            highest = Math.max(highest, each);
        }
        return highest;
    }

    private static int[] spreadOverBlockTypes(int[] contextMap,
            int howManyContexts, int howManyTypes) {

        int[] spread = new int[howManyTypes << LITERAL_CONTEXT_BITS];
        for (int type = 0; type < howManyTypes; type++) {
            int offset = type * howManyContexts;
            for (int context = 0; context < (1 << LITERAL_CONTEXT_BITS); context++) {
                spread[(type << LITERAL_CONTEXT_BITS) + context] =
                        offset + contextMap[context];
            }
        }
        return spread;
    }

    private static void optimiseHistogramsForRuns(BrotliMetaBlockSplit built,
            int howManyDistanceCodes) {

        for (int which = 0; which < built.howManyLiteralHistograms; which++) {
            BrotliCodes.smoothCountsIntoRuns(LITERAL_SYMBOLS,
                    built.literalHistograms[which].counts());
        }
        for (int which = 0; which < built.howManyCommandHistograms; which++) {
            BrotliCodes.smoothCountsIntoRuns(BrotliCodes.COMMAND_SYMBOLS,
                    built.commandHistograms[which].counts());
        }
        for (int which = 0; which < built.howManyDistanceHistograms; which++) {
            BrotliCodes.smoothCountsIntoRuns(howManyDistanceCodes,
                    built.distanceHistograms[which].counts());
        }
    }
}
