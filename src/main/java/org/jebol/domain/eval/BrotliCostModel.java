package org.jebol.domain.eval;

/**
 * What each symbol would cost in bits, so that matches can be compared rather
 * than merely ranked.
 *
 * <p>{@code ZopfliCostModel} in {@code backward_references_hq.c}. The levels
 * below ten score a match with a rough number that says "longer and nearer is
 * better"; these two want the real thing, because they are choosing between
 * whole sequences of commands and a shorter match now may leave a better one
 * available later.
 *
 * <p>Two ways of filling it in. Before anything is known, every command symbol
 * costs the logarithm of its index and literals cost what the sliding window
 * guessed. Level eleven then does the whole parse a second time with the costs
 * measured from the commands the first parse produced, which is the only
 * difference between the two levels' parses.
 *
 * <p>Costs are held as float, not double. That is the C's type and it is
 * load-bearing: the parse compares costs for less-than, and the two types
 * disagree about which of two nearly equal matches wins.
 */
final class BrotliCostModel {

    /** About two to the hundred and twenty seventh; the C's own value. */
    static final float UNREACHABLE = 1.7e38f;

    private static final int LITERAL_SYMBOLS = 256;

    private final int howManyBytes;
    private final int distanceSymbols;
    private final float[] commandCost = new float[BrotliCodes.COMMAND_SYMBOLS];
    private final float[] distanceCost;

    /** Running total, so the cost of a stretch is one subtraction. */
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

    /**
     * The first guess: literals cost what the window says, and every command
     * symbol costs a little more than the one before it.
     */
    void guessFromTheDataAlone(byte[] data, int from, int mask) {
        float[] perByte = new float[howManyBytes];
        BrotliLiteralCosts.estimate(data, from, howManyBytes, mask, perByte);
        accumulate(perByte);
        for (int symbol = 0; symbol < BrotliCodes.COMMAND_SYMBOLS; symbol++) {
            commandCost[symbol] = (float) BrotliCodes.fastLog2(11 + symbol);
        }
        for (int symbol = 0; symbol < distanceSymbols; symbol++) {
            distanceCost[symbol] = (float) BrotliCodes.fastLog2(20 + symbol);
        }
        cheapestCommand = (float) BrotliCodes.fastLog2(11);
    }

    /**
     * The second guess, from what the first parse actually wrote.
     *
     * <p>{@code ZopfliCostModelSetFromCommands}. A symbol that the first parse
     * used often is cheap the second time round, which is what lets level eleven
     * find a better answer than level ten on the same input.
     */
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
        accumulate(perByte);
    }

    /**
     * Turns per-byte costs into a running total, keeping the part that float
     * addition would otherwise lose.
     *
     * <p>Adding several thousand small floats into one large one loses the low
     * bits of every addition. The C carries the lost part forward into the next
     * one, which keeps the total close to what double arithmetic would give
     * without using double. The three lines are the C's and the order of
     * operations matters.
     */
    private void accumulate(float[] perByte) {
        float carried = 0.0f;
        literalCostUpTo[0] = 0.0f;
        for (int each = 0; each < howManyBytes; each++) {
            carried += perByte[each];
            literalCostUpTo[each + 1] = literalCostUpTo[each] + carried;
            carried -= literalCostUpTo[each + 1] - literalCostUpTo[each];
        }
    }

    /**
     * The Shannon cost of each symbol, and something plausible for the ones
     * that never appeared.
     *
     * <p>A symbol that was never used still has to be affordable, because the
     * parse may want it; it is priced as if it had appeared once, plus two bits.
     * For literals the unused ones are not counted into the total, which makes
     * the used ones look commoner and so cheaper.
     */
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
        float costOfSomethingUnseen =
                (float) BrotliCodes.fastLog2(totalWithTheMissingOnes) + 2;
        for (int each = 0; each < size; each++) {
            if (histogram[each] == 0) {
                cost[each] = costOfSomethingUnseen;
                continue;
            }
            cost[each] = logOfTotal - (float) BrotliCodes.fastLog2(histogram[each]);
            if (cost[each] < 1) {
                cost[each] = 1;
            }
        }
    }
}
