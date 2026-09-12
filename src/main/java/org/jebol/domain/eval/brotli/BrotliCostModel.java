package org.jebol.domain.eval.brotli;

final class BrotliCostModel {

    static final float UNREACHABLE = 1.7e38f;

    private static final int LITERAL_SYMBOLS = 256;

    private final int howManyBytes;
    private final int distanceSymbols;
    private final float[] commandCost = new float[BrotliCodes.COMMAND_SYMBOLS];
    private final float[] distanceCost;

    private final float[] literalCostUpTo;

    private float cheapestCommand;

    BrotliCostModel(int howManyBytes, int distanceSymbols) {
        this.howManyBytes = howManyBytes;
        this.distanceSymbols = distanceSymbols;
        this.distanceCost = new float[distanceSymbols];
        this.literalCostUpTo = new float[howManyBytes + 2];
    }

    float commandCost(int symbol) {
        return commandCost[symbol];
    }

    float distanceCost(int symbol) {
        return distanceCost[symbol];
    }

    float literalCost(int from, int to) {
        return literalCostUpTo[to] - literalCostUpTo[from];
    }

    float cheapestCommand() {
        return cheapestCommand;
    }

    void guessFromTheDataAlone(byte[] data, int from, int mask) {
        float[] perByte = new float[howManyBytes];
        BrotliLiteralCosts.estimate(data, from, howManyBytes, mask, perByte);
        accumulateCarryingWhatFloatAdditionWouldLose(perByte);
        for (int symbol = 0; symbol < BrotliCodes.COMMAND_SYMBOLS; symbol++) {
            commandCost[symbol] = (float) BrotliCodes.fastLog2(11 + symbol);
        }
        for (int symbol = 0; symbol < distanceSymbols; symbol++) {
            distanceCost[symbol] = (float) BrotliCodes.fastLog2(20 + symbol);
        }
        cheapestCommand = (float) BrotliCodes.fastLog2(11);
    }

    void measureFromTheCommands(byte[] data, int from, int mask,
                                BrotliCommand commands, int firstCommand, int howManyCommands,
                                int insertLengthCarriedIn) {

        int[] literals = new int[LITERAL_SYMBOLS];
        int[] commandSymbols = new int[BrotliCodes.COMMAND_SYMBOLS];
        int[] distances = new int[distanceSymbols];

        int at = from - insertLengthCarriedIn;
        for (int step = 0; step < howManyCommands; step++) {
            int which = firstCommand + step;
            int insertLength = commands.insertLengthAt(which);
            int copyLength = commands.copyLengthAt(which);
            int commandSymbol = commands.commandPrefixAt(which);
            commandSymbols[commandSymbol]++;
            if (commandSymbol >= 128) {
                distances[commands.distancePrefixAt(which) & 0x3FF]++;
            }
            for (int each = 0; each < insertLength; each++) {
                literals[data[(at + each) & mask] & 0xFF]++;
            }
            at += insertLength + copyLength;
        }

        float[] literalCost = new float[LITERAL_SYMBOLS];
        setCost(literals, LITERAL_SYMBOLS, true, literalCost);
        setCost(commandSymbols, BrotliCodes.COMMAND_SYMBOLS, false, commandCost);
        setCost(distances, distanceSymbols, false, distanceCost);

        float cheapest = UNREACHABLE;
        for (int symbol = 0; symbol < BrotliCodes.COMMAND_SYMBOLS; symbol++) {
            cheapest = Math.min(cheapest, commandCost[symbol]);
        }
        cheapestCommand = cheapest;

        float[] perByte = new float[howManyBytes];
        for (int each = 0; each < howManyBytes; each++) {
            perByte[each] = literalCost[data[(from + each) & mask] & 0xFF];
        }
        accumulateCarryingWhatFloatAdditionWouldLose(perByte);
    }

    private void accumulateCarryingWhatFloatAdditionWouldLose(float[] perByte) {
        float carried = 0.0f;
        literalCostUpTo[0] = 0.0f;
        for (int each = 0; each < howManyBytes; each++) {
            carried += perByte[each];
            literalCostUpTo[each + 1] = literalCostUpTo[each] + carried;
            carried -= literalCostUpTo[each + 1] - literalCostUpTo[each];
        }
    }

    private static void setCost(int[] histogram, int size, boolean literals,
            float[] cost) {

        long total = 0;
        for (int each = 0; each < size; each++) {
            total += histogram[each];
        }
        float logOfTotal = (float) BrotliCodes.fastLog2(total);
        long totalWithTheMissingOnes = total;
        if (!literals) {
            for (int each = 0; each < size; each++) {
                if (histogram[each] == 0) {
                    totalWithTheMissingOnes++;
                }
            }
        }
        float costOfSomethingUnseenPricedAsIfItAppearedOncePlusTwoBits =
                (float) BrotliCodes.fastLog2(totalWithTheMissingOnes) + 2;
        for (int each = 0; each < size; each++) {
            if (histogram[each] == 0) {
                cost[each] = costOfSomethingUnseenPricedAsIfItAppearedOncePlusTwoBits;
                continue;
            }
            cost[each] = logOfTotal - (float) BrotliCodes.fastLog2(histogram[each]);
            if (cost[each] < 1) {
                cost[each] = 1;
            }
        }
    }
}
