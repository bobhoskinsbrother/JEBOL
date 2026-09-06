package org.jebol.domain.eval;

import java.util.Arrays;

/**
 * Building a prefix code and writing it down, which every Brotli quality does
 * and none of them does differently.
 *
 * <p>{@code entropy_encode.c} and the code-storing half of
 * {@code brotli_bit_stream.c}. A code is built from a histogram by merging the
 * two cheapest nodes until one is left, then read off as a depth per symbol;
 * it is written down either as up to four symbols named outright or as the
 * depths themselves, run-length coded under a second, fixed code.
 *
 * <p>Two builders, and the difference between them is one line. The one the
 * literal codes use breaks no tie between two symbols of equal count; the one
 * the command and distance codes use puts the later symbol first. Each file in
 * the C has a static comparator of its own, and using the wrong one gives a
 * code of the same shape with two of its symbols swapped -- valid, decodes
 * perfectly, and not the bytes a real 3.22.5 writes.
 */
final class BrotliCodes {

    private BrotliCodes() {
    }

    static final int COMMAND_SYMBOLS = 704;
    private static final int CODE_LENGTH_CODES = 18;
    private static final int REPEAT_PREVIOUS_CODE_LENGTH = 16;
    private static final int REPEAT_ZERO_CODE_LENGTH = 17;
    private static final int INITIAL_REPEATED_CODE_LENGTH = 8;
    private static final int MAX_HUFFMAN_BITS = 16;

    /** The fixed code the code lengths are themselves written under. */
    private static final int[] CODE_LENGTH_DEPTH =
            {4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 0, 4, 4};
    private static final int[] CODE_LENGTH_BITS =
            {0, 8, 4, 12, 2, 10, 6, 14, 1, 9, 5, 13, 3, 15, 31, 0, 11, 7};

    private static final int[] CODE_LENGTH_STORAGE_ORDER =
            {1, 2, 3, 4, 0, 5, 17, 6, 16, 7, 8, 9, 10, 11, 12, 13, 14, 15};
    private static final int[] LENGTH_CODE_SYMBOLS = {0, 7, 3, 2, 1, 15};
    private static final int[] LENGTH_CODE_WIDTHS = {2, 4, 3, 2, 2, 4};

    private static final int[] SHELL_GAPS = {132, 57, 23, 10, 4, 1};

    /** The forty bits that say "the code lengths use the fixed code". */
    private static final long STATIC_CODE_LENGTH_CODE = 0x000000FF55555554L;

    /**
     * How long a run of code lengths costs, and what it is.
     *
     * <p>The C ships two pairs of generated tables, seven hundred and four
     * numbers each. They are the same thing worked out here: a run of zeros is
     * symbol seventeen with three bits of count, a run of anything else is
     * symbol sixteen with two, and both come out most significant piece first
     * because the writer builds them backwards and reverses.
     */
    private static final int[] ZERO_RUN_WIDTH = new int[COMMAND_SYMBOLS];
    private static final long[] ZERO_RUN_BITS = new long[COMMAND_SYMBOLS];
    private static final int[] OTHER_RUN_WIDTH = new int[COMMAND_SYMBOLS];
    private static final long[] OTHER_RUN_BITS = new long[COMMAND_SYMBOLS];

    static {
        for (int reps = 1; reps < COMMAND_SYMBOLS; reps++) {
            Run run = new Run();
            int remaining = reps;
            if (remaining == 11) {
                run.add(0, 0, 0);
                remaining--;
            }
            if (remaining < 3) {
                for (int each = 0; each < remaining; each++) {
                    run.add(0, 0, 0);
                }
            } else {
                int start = run.count;
                remaining -= 3;
                while (true) {
                    run.add(REPEAT_ZERO_CODE_LENGTH, remaining & 7, 3);
                    remaining >>= 3;
                    if (remaining == 0) {
                        break;
                    }
                    remaining--;
                }
                run.reverseFrom(start);
            }
            ZERO_RUN_WIDTH[reps] = run.totalWidth();
            ZERO_RUN_BITS[reps] = run.packed();
        }
        for (int reps = 0; reps < COMMAND_SYMBOLS; reps++) {
            Run run = new Run();
            int remaining = reps;
            while (true) {
                run.add(REPEAT_PREVIOUS_CODE_LENGTH, remaining & 3, 2);
                remaining >>= 2;
                if (remaining == 0) {
                    break;
                }
                remaining--;
            }
            run.reverseFrom(0);
            OTHER_RUN_WIDTH[reps] = run.totalWidth();
            OTHER_RUN_BITS[reps] = run.packed();
        }
    }

    /** One run of code-length symbols, each with its extra bits. */
    private static final class Run {

        private final int[] symbols = new int[64];
        private final int[] extra = new int[64];
        private final int[] extraWidth = new int[64];
        private int count;

        void add(int symbol, int extraValue, int howManyExtraBits) {
            symbols[count] = symbol;
            extra[count] = extraValue;
            extraWidth[count] = howManyExtraBits;
            count++;
        }

        void reverseFrom(int start) {
            for (int low = start, high = count - 1; low < high; low++, high--) {
                int symbol = symbols[low];
                symbols[low] = symbols[high];
                symbols[high] = symbol;
                int value = extra[low];
                extra[low] = extra[high];
                extra[high] = value;
                int width = extraWidth[low];
                extraWidth[low] = extraWidth[high];
                extraWidth[high] = width;
            }
        }

        int totalWidth() {
            int total = 0;
            for (int each = 0; each < count; each++) {
                total += CODE_LENGTH_DEPTH[symbols[each]] + extraWidth[each];
            }
            return total;
        }

        long packed() {
            long value = 0;
            int at = 0;
            for (int each = 0; each < count; each++) {
                value |= (long) CODE_LENGTH_BITS[symbols[each]] << at;
                at += CODE_LENGTH_DEPTH[symbols[each]];
                value |= (long) extra[each] << at;
                at += extraWidth[each];
            }
            return value;
        }
    }

    /**
     * Base two logarithms of the first 256 whole numbers, at the precision the
     * C actually holds them.
     *
     * <p>Which is not double precision, despite the C's table being an array of
     * double. Every one of its two hundred and fifty six literals is written
     * with an {@code f} on the end, so the compiler rounds each to float first
     * and only then widens it to double. Two hundred and forty seven of them
     * therefore differ from {@code log2} of the same number, not by a last bit
     * but by about one part in ten million.
     *
     * <p>Written here as the doubles those floats widen to. Computing them
     * instead -- which is what this file did at first -- makes every cost the
     * encoder works out slightly different from the C's, and at the two levels
     * that choose between matches by comparing costs that is enough to pick a
     * different match and write different bytes.
     */
    private static final double[] LOG_2 = {
            0.0, 0.0, 1.0, 1.5849624872207642,
            2.0, 2.321928024291992, 2.5849626064300537, 2.8073549270629883,
            3.0, 3.1699249744415283, 3.321928024291992, 3.4594316482543945,
            3.5849626064300537, 3.700439691543579, 3.8073549270629883, 3.906890630722046,
            4.0, 4.087462902069092, 4.169925212860107, 4.247927665710449,
            4.321928024291992, 4.392317295074463, 4.4594316482543945, 4.523561954498291,
            4.584962368011475, 4.643856048583984, 4.700439929962158, 4.754887580871582,
            4.807354927062988, 4.857981204986572, 4.906890392303467, 4.954196453094482,
            5.0, 5.044394016265869, 5.087462902069092, 5.1292829513549805,
            5.169925212860107, 5.209453582763672, 5.247927665710449, 5.285402297973633,
            5.321928024291992, 5.3575520515441895, 5.392317295074463, 5.426264762878418,
            5.4594316482543945, 5.4918532371521, 5.523561954498291, 5.554588794708252,
            5.584962368011475, 5.614709854125977, 5.643856048583984, 5.672425270080566,
            5.700439929962158, 5.7279205322265625, 5.754887580871582, 5.781359672546387,
            5.807354927062988, 5.832890033721924, 5.857981204986572, 5.882643222808838,
            5.906890392303467, 5.930737495422363, 5.954196453094482, 5.977280139923096,
            6.0, 6.02236795425415, 6.044394016265869, 6.066089153289795,
            6.087462902069092, 6.108524322509766, 6.1292829513549805, 6.149746894836426,
            6.169925212860107, 6.18982458114624, 6.209453582763672, 6.228818893432617,
            6.247927665710449, 6.266786575317383, 6.285402297973633, 6.303780555725098,
            6.321928024291992, 6.339849948883057, 6.3575520515441895, 6.375039577484131,
            6.392317295074463, 6.409390926361084, 6.426264762878418, 6.442943572998047,
            6.4594316482543945, 6.475733280181885, 6.4918532371521, 6.5077948570251465,
            6.523561954498291, 6.539158821105957, 6.554588794708252, 6.569855690002441,
            6.584962368011475, 6.599912643432617, 6.614709854125977, 6.629356384277344,
            6.643856048583984, 6.658211708068848, 6.672425270080566, 6.686500549316406,
            6.700439929962158, 6.714245319366455, 6.7279205322265625, 6.741466999053955,
            6.754887580871582, 6.768184185028076, 6.781359672546387, 6.7944159507751465,
            6.807354927062988, 6.820178985595703, 6.832890033721924, 6.845489978790283,
            6.857981204986572, 6.870364665985107, 6.882643222808838, 6.89481782913208,
            6.906890392303467, 6.918863296508789, 6.930737495422363, 6.942514419555664,
            6.954196453094482, 6.965784072875977, 6.977280139923096, 6.98868465423584,
            7.0, 7.011227130889893, 7.02236795425415, 7.033422946929932,
            7.044394016265869, 7.0552825927734375, 7.066089153289795, 7.076815605163574,
            7.087462902069092, 7.098031997680664, 7.108524322509766, 7.118941307067871,
            7.1292829513549805, 7.139551162719727, 7.149746894836426, 7.1598711013793945,
            7.169925212860107, 7.1799092292785645, 7.18982458114624, 7.199672222137451,
            7.209453582763672, 7.219168663024902, 7.228818893432617, 7.238404750823975,
            7.247927665710449, 7.257387638092041, 7.266786575317383, 7.276124477386475,
            7.285402297973633, 7.294620513916016, 7.303780555725098, 7.312882900238037,
            7.321928024291992, 7.330916881561279, 7.339849948883057, 7.348728179931641,
            7.3575520515441895, 7.366322040557861, 7.375039577484131, 7.38370418548584,
            7.392317295074463, 7.400879383087158, 7.409390926361084, 7.417852401733398,
            7.426264762878418, 7.434628009796143, 7.442943572998047, 7.451210975646973,
            7.4594316482543945, 7.4676055908203125, 7.475733280181885, 7.483815670013428,
            7.4918532371521, 7.4998459815979, 7.5077948570251465, 7.515699863433838,
            7.523561954498291, 7.531381607055664, 7.539158821105957, 7.546894550323486,
            7.554588794708252, 7.56224250793457, 7.569855690002441, 7.577428817749023,
            7.584962368011475, 7.592456817626953, 7.599912643432617, 7.607330322265625,
            7.614709854125977, 7.62205171585083, 7.629356384277344, 7.636624813079834,
            7.643856048583984, 7.6510515213012695, 7.658211708068848, 7.6653361320495605,
            7.672425270080566, 7.679480075836182, 7.686500549316406, 7.693487167358398,
            7.700439929962158, 7.707359313964844, 7.714245319366455, 7.721099376678467,
            7.7279205322265625, 7.734709739685059, 7.741466999053955, 7.74819278717041,
            7.754887580871582, 7.761551380157471, 7.768184185028076, 7.774786949157715,
            7.781359672546387, 7.787902355194092, 7.7944159507751465, 7.800899982452393,
            7.807354927062988, 7.813781261444092, 7.820178985595703, 7.8265485763549805,
            7.832890033721924, 7.839203834533691, 7.845489978790283, 7.851748943328857,
            7.857981204986572, 7.8641862869262695, 7.870364665985107, 7.876516819000244,
            7.882643222808838, 7.8887434005737305, 7.89481782913208, 7.900866985321045,
            7.906890392303467, 7.91288948059082, 7.918863296508789, 7.924812316894531,
            7.930737495422363, 7.936637878417969, 7.942514419555664, 7.948367118835449,
            7.954196453094482, 7.9600019454956055, 7.965784072875977, 7.971543788909912,
            7.977280139923096, 7.9829936027526855, 7.98868465423584, 7.994353294372559,
    };

    /**
     * {@code log2}, whose defining property here is that log2 of nought is
     * nought.
     *
     * <p>Small values come from the table the C prints out, which is not quite
     * the computed logarithm -- the printed values are one digit short of
     * round-tripping, so a hundred of the two hundred and fifty six differ from
     * {@code log2} by a bit. Larger values are computed, and computed carefully:
     * see {@link BrotliLog2} for why the obvious way is not close enough.
     */
    static double fastLog2(long value) {
        if (value < LOG_2.length) {
            return LOG_2[(int) value];
        }
        return BrotliLog2.of(value);
    }

    /**
     * How many bits the symbols would take if each cost exactly its own
     * surprise, floored at one bit each.
     *
     * <p>{@code BrotliBitsEntropy}. The floor is the C's, and its comment says
     * why: "at least one bit per literal is needed".
     */
    static double bitsEntropy(int[] population, int size) {
        long sum = 0;
        double answer = 0;
        for (int each = 0; each < size; each++) {
            long count = population[each] & 0xFFFFFFFFL;
            sum += count;
            answer -= (double) count * fastLog2(count);
        }
        if (sum != 0) {
            answer += (double) sum * fastLog2(sum);
        }
        return answer < (double) sum ? (double) sum : answer;
    }

    static int log2Floor(int value) {
        return 31 - Integer.numberOfLeadingZeros(value);
    }

    /**
     * Nudges near-equal counts to be exactly equal, so the code lengths they
     * produce can be written as a run rather than one by one.
     *
     * <p>{@code BrotliOptimizeHuffmanCountsForRle}. A prefix code is stored as a
     * list of lengths with runs of equal lengths abbreviated, so a stretch of
     * symbols whose counts are close enough to share a length costs almost
     * nothing to declare. Rounding a run of similar counts to their average
     * makes that happen, at the cost of a slightly worse code for the symbols
     * themselves. The C's own comment calls the arithmetic below fixed point
     * with eight fractional bits, which is where every multiplication by two
     * hundred and fifty six comes from.
     *
     * <p>It gives up early three times over: on fewer than sixteen used symbols,
     * on fewer than five once the trailing zeros are dropped, and on fewer than
     * twenty eight. A small alphabet is modelled well enough as it stands.
     */
    static void smoothCountsIntoRuns(int size, int[] counts) {
        int used = 0;
        for (int each = 0; each < size; each++) {
            if (counts[each] != 0) {
                used++;
            }
        }
        if (used < 16) {
            return;
        }
        int length = size;
        while (length != 0 && counts[length - 1] == 0) {
            length--;
        }
        if (length == 0) {
            return;
        }
        if (!worthSmoothing(counts, length)) {
            return;
        }
        boolean[] alreadyARun = runsWorthKeeping(counts, length);
        flattenTheRestIntoRuns(counts, length, alreadyARun);
    }

    private static boolean worthSmoothing(int[] counts, int length) {
        int used = 0;
        int smallest = 1 << 30;
        for (int each = 0; each < length; each++) {
            if (counts[each] != 0) {
                used++;
                smallest = Math.min(smallest, counts[each]);
            }
        }
        if (used < 5) {
            return false;
        }
        if (smallest < 4 && length - used < 6) {
            fillSingleGaps(counts, length);
        }
        return used >= 28;
    }

    /** A lone zero between two used symbols costs more as a gap than as a one. */
    private static void fillSingleGaps(int[] counts, int length) {
        for (int each = 1; each < length - 1; each++) {
            if (counts[each - 1] != 0 && counts[each] == 0
                    && counts[each + 1] != 0) {
                counts[each] = 1;
            }
        }
    }

    /** Runs already long enough to code cheaply, which must not be disturbed. */
    private static boolean[] runsWorthKeeping(int[] counts, int length) {
        boolean[] worthKeeping = new boolean[length];
        int value = counts[0];
        int run = 0;
        for (int each = 0; each <= length; each++) {
            if (each == length || counts[each] != value) {
                if ((value == 0 && run >= 5) || (value != 0 && run >= 7)) {
                    for (int back = 0; back < run; back++) {
                        worthKeeping[each - back - 1] = true;
                    }
                }
                run = 1;
                if (each != length) {
                    value = counts[each];
                }
            } else {
                run++;
            }
        }
        return worthKeeping;
    }

    /**
     * Whether a count differs from the running average by enough to end a
     * streak, in either direction.
     *
     * <p>The C writes this as one comparison of unsigned numbers, which reads
     * as a test for "too far above" and is also a test for "too far below":
     * subtracting a larger limit wraps the difference round to an enormous
     * positive number, and that clears the threshold too. Written with signed
     * numbers here, both halves have to be said.
     */
    private static boolean straysTooFarFrom(long limit, int count) {
        long difference = 256L * count - limit;
        return difference >= HOW_FAR_A_COUNT_MAY_STRAY
                || difference < -HOW_FAR_A_COUNT_MAY_STRAY;
    }

    private static final int HOW_FAR_A_COUNT_MAY_STRAY = 1240;

    private static void flattenTheRestIntoRuns(int[] counts, int length,
            boolean[] alreadyARun) {

        int run = 0;
        long limit = 256L * (counts[0] + counts[1] + counts[2]) / 3 + 420;
        long sum = 0;
        for (int each = 0; each <= length; each++) {
            boolean streakEnds = each == length
                    || alreadyARun[each]
                    || (each != 0 && alreadyARun[each - 1])
                    || straysTooFarFrom(limit, counts[each]);
            if (streakEnds) {
                if (run >= 4 || (run >= 3 && sum == 0)) {
                    long flattened = sum == 0 ? 0 : Math.max(1, (sum + run / 2) / run);
                    for (int back = 0; back < run; back++) {
                        counts[each - back - 1] = (int) flattened;
                    }
                }
                run = 0;
                sum = 0;
                if (each < length - 2) {
                    limit = 256L * (counts[each] + counts[each + 1]
                            + counts[each + 2]) / 3 + 420;
                } else if (each < length) {
                    limit = 256L * counts[each];
                } else {
                    limit = 0;
                }
            }
            run++;
            if (each != length) {
                sum += counts[each];
                if (run >= 4) {
                    limit = (256L * sum + run / 2) / run;
                }
                if (run == 4) {
                    limit += 120;
                }
            }
        }
    }

    static void convertBitDepthsToSymbols(int[] depth, int depthAt,
        int length, int[] bits, int bitsAt) {

    int[] countPerLength = new int[MAX_HUFFMAN_BITS];
    for (int each = 0; each < length; each++) {
        countPerLength[depth[depthAt + each]]++;
    }
    countPerLength[0] = 0;
    int[] nextCode = new int[MAX_HUFFMAN_BITS];
    int code = 0;
    for (int width = 1; width < MAX_HUFFMAN_BITS; width++) {
        code = (code + countPerLength[width - 1]) << 1;
        nextCode[width] = code;
    }
    for (int each = 0; each < length; each++) {
        int width = depth[depthAt + each];
        if (width != 0) {
            bits[bitsAt + each] = reversedBits(width, nextCode[width]++);
        }
    }
    }

    private static int reversedBits(int howManyBits, int value) {
    int reversed = 0;
    for (int each = 0; each < howManyBits; each++) {
        reversed = (reversed << 1) | ((value >> each) & 1);
    }
    return reversed;
    }

    /**
     * The Huffman builder the C uses: merge the leaves into a tree, and if it
     * comes out deeper than the limit allows, raise the floor under the rare
     * symbols and start again.
     */
    static final class Tree {

    private static final int ROOM = 2 * COMMAND_SYMBOLS + 4;
    private static final long SENTINEL = 0xFFFFFFFFL;

    private final long[] totalCount = new long[ROOM];
    private final int[] leftIndex = new int[ROOM];
    private final int[] valueIndex = new int[ROOM];

    private void put(int at, long count, int left, int value) {
        totalCount[at] = count;
        leftIndex[at] = left;
        valueIndex[at] = value;
    }

    /**
     * {@code BrotliCreateHuffmanTree}, whose sort breaks a tie in the
     * counts by putting the later symbol first.
     */
    void build(int[] counts, int countsAt, int length, int limit,
            int[] depth, int depthAt) {

        for (long floor = 1; ; floor *= 2) {
            int leaves = 0;
            for (int each = length; each != 0; ) {
                each--;
                long count = counts[countsAt + each] & 0xFFFFFFFFL;
                if (count != 0) {
                    put(leaves++, Math.max(count, floor), -1, each);
                }
            }
            if (leaves == 1) {
                depth[depthAt + valueIndex[0]] = 1;
                return;
            }
            if (assemble(leaves, depth, depthAt, limit, true)) {
                return;
            }
        }
    }

    /**
     * {@code BrotliBuildAndStoreHuffmanTreeFast}, whose sort does not.
     *
     * <p>The two are one line apart and the line matters. Each file has a
     * static {@code SortHuffmanTree} of its own, and the one in
     * brotli_bit_stream.c is the whole of
     * {@code v0->total_count_ < v1->total_count_} -- no tie-break -- so
     * two symbols of equal count keep whatever order the shell sort leaves
     * them in. Using the other file's comparator here gives a code of
     * exactly the same shape with two of its symbols swapped, which is
     * valid Brotli, decodes correctly, and is not the bytes a real 3.22.5
     * writes.
     */
    void buildFast(int[] counts, int length, int limit, int[] depth) {
        for (long floor = 1; ; floor *= 2) {
            int leaves = 0;
            for (int each = length; each != 0; ) {
                each--;
                long count = counts[each] & 0xFFFFFFFFL;
                if (count != 0) {
                    put(leaves++, count >= floor ? count : floor, -1, each);
                }
            }
            if (assemble(leaves, depth, 0, limit, false)) {
                return;
            }
        }
    }

    /**
     * Merges the leaves and reads the depths off the tree.
     *
     * <p>The leaves are sorted, a sentinel is placed after them, and new
     * parents are appended, which keeps the parents in ascending order and
     * lets the merge take its next-cheapest node from whichever of the two
     * runs has it without ever sorting again.
     */
    private boolean assemble(int leaves, int[] depth, int depthAt, int limit,
            boolean breakTiesBySymbol) {
        sort(leaves, breakTiesBySymbol);
        put(leaves, SENTINEL, -1, -1);
        put(leaves + 1, SENTINEL, -1, -1);
        int nextLeaf = 0;
        int nextParent = leaves + 1;
        for (int remaining = leaves - 1; remaining > 0; remaining--) {
            int left = totalCount[nextLeaf] <= totalCount[nextParent]
                    ? nextLeaf++ : nextParent++;
            int right = totalCount[nextLeaf] <= totalCount[nextParent]
                    ? nextLeaf++ : nextParent++;
            int at = 2 * leaves - remaining;
            totalCount[at] = totalCount[left] + totalCount[right];
            leftIndex[at] = left;
            valueIndex[at] = right;
            put(at + 1, SENTINEL, -1, -1);
        }
        return setDepth(2 * leaves - 1, depth, depthAt, limit);
    }

    /** Cheapest first, and among equals whatever the caller asks for. */
    private boolean sortsBefore(long count, int value, int other,
            boolean breakTiesBySymbol) {
        if (count != totalCount[other]) {
            return count < totalCount[other];
        }
        return breakTiesBySymbol && value > valueIndex[other];
    }

    private void sort(int leaves, boolean breakTiesBySymbol) {
        if (leaves < 13) {
            for (int each = 1; each < leaves; each++) {
                long count = totalCount[each];
                int left = leftIndex[each];
                int value = valueIndex[each];
                int to = each;
                int at = each - 1;
                while (sortsBefore(count, value, at, breakTiesBySymbol)) {
                    totalCount[to] = totalCount[at];
                    leftIndex[to] = leftIndex[at];
                    valueIndex[to] = valueIndex[at];
                    to = at;
                    if (at-- == 0) {
                        break;
                    }
                }
                totalCount[to] = count;
                leftIndex[to] = left;
                valueIndex[to] = value;
            }
            return;
        }
        for (int gapAt = leaves < 57 ? 2 : 0; gapAt < 6; gapAt++) {
            int gap = SHELL_GAPS[gapAt];
            for (int each = gap; each < leaves; each++) {
                long count = totalCount[each];
                int left = leftIndex[each];
                int value = valueIndex[each];
                int at = each;
                while (at >= gap
                        && sortsBefore(count, value, at - gap, breakTiesBySymbol)) {
                    totalCount[at] = totalCount[at - gap];
                    leftIndex[at] = leftIndex[at - gap];
                    valueIndex[at] = valueIndex[at - gap];
                    at -= gap;
                }
                totalCount[at] = count;
                leftIndex[at] = left;
                valueIndex[at] = value;
            }
        }
    }

    private boolean setDepth(int root, int[] depth, int depthAt, int limit) {
        int[] stack = new int[16];
        int level = 0;
        int at = root;
        stack[0] = -1;
        while (true) {
            if (leftIndex[at] >= 0) {
                level++;
                if (level > limit) {
                    return false;
                }
                stack[level] = valueIndex[at];
                at = leftIndex[at];
                continue;
            }
            depth[depthAt + valueIndex[at]] = level;
            while (level >= 0 && stack[level] == -1) {
                level--;
            }
            if (level < 0) {
                return true;
            }
            at = stack[level];
            stack[level] = -1;
        }
    }
    }

    /**
     * A prefix code from a histogram, written out as it is built.
     *
     * <p>{@code BrotliBuildAndStoreHuffmanTreeFast}. Four or fewer symbols are
     * named outright, which the format has a short form for; more than that
     * goes out as code lengths under the fixed code-length code, with runs of
     * equal lengths coded.
     */
    static void buildAndStoreHuffmanTreeFast(Tree tree, int[] counts,
            long total, int maxBits, int[] depth, int[] bits, BrotliBits into) {

        int count = 0;
        int[] symbols = new int[4];
        int length = 0;
        long left = total;
        while (left != 0) {
            if (counts[length] != 0) {
                if (count < 4) {
                    symbols[count] = length;
                }
                count++;
                left -= counts[length] & 0xFFFFFFFFL;
            }
            length++;
        }

        if (count <= 1) {
            into.write(4, 1);
            into.write(maxBits, symbols[0]);
            depth[symbols[0]] = 0;
            bits[symbols[0]] = 0;
            return;
        }

        Arrays.fill(depth, 0, length, 0);
        tree.buildFast(counts, length, 14, depth);
        convertBitDepthsToSymbols(depth, 0, length, bits, 0);

        if (count <= 4) {
            into.write(2, 1);
            into.write(2, count - 1);
            for (int each = 0; each < count; each++) {
                for (int other = each + 1; other < count; other++) {
                    if (depth[symbols[other]] < depth[symbols[each]]) {
                        int swapped = symbols[other];
                        symbols[other] = symbols[each];
                        symbols[each] = swapped;
                    }
                }
            }
            for (int each = 0; each < count; each++) {
                into.write(maxBits, symbols[each]);
            }
            if (count == 4) {
                into.write(1, depth[symbols[0]] == 1 ? 1 : 0);
            }
            return;
        }

        into.write(40, STATIC_CODE_LENGTH_CODE);
        int previousValue = INITIAL_REPEATED_CODE_LENGTH;
        int at = 0;
        while (at < length) {
            int value = depth[at];
            int reps = 1;
            while (at + reps < length && depth[at + reps] == value) {
                reps++;
            }
            at += reps;
            if (value == 0) {
                into.write(ZERO_RUN_WIDTH[reps], ZERO_RUN_BITS[reps]);
                continue;
            }
            if (previousValue != value) {
                into.write(CODE_LENGTH_DEPTH[value], CODE_LENGTH_BITS[value]);
                reps--;
            }
            if (reps < 3) {
                while (reps != 0) {
                    reps--;
                    into.write(CODE_LENGTH_DEPTH[value], CODE_LENGTH_BITS[value]);
                }
            } else {
                reps -= 3;
                into.write(OTHER_RUN_WIDTH[reps], OTHER_RUN_BITS[reps]);
            }
            previousValue = value;
        }
    }

    /**
     * A prefix code written out the long way: as code lengths, run-length
     * coded, under a second prefix code over the lengths.
     */
    static void storeHuffmanTree(Tree tree, int[] depths, int at, int length,
            BrotliBits into) {

        int[] symbols = new int[COMMAND_SYMBOLS];
        int[] extra = new int[COMMAND_SYMBOLS];
        int written = writeTreeAsCodeLengths(depths, at, length, symbols, extra);

        int[] counts = new int[CODE_LENGTH_CODES];
        for (int each = 0; each < written; each++) {
            counts[symbols[each]]++;
        }
        int distinct = 0;
        int onlyCode = 0;
        for (int each = 0; each < CODE_LENGTH_CODES; each++) {
            if (counts[each] != 0) {
                if (distinct == 0) {
                    onlyCode = each;
                    distinct = 1;
                } else {
                    distinct = 2;
                    break;
                }
            }
        }

        int[] lengthDepth = new int[CODE_LENGTH_CODES];
        tree.build(counts, 0, CODE_LENGTH_CODES, 5, lengthDepth, 0);
        int[] lengthBits = new int[CODE_LENGTH_CODES];
        convertBitDepthsToSymbols(lengthDepth, 0, CODE_LENGTH_CODES, lengthBits, 0);

        storeTheCodeOverCodeLengths(distinct, lengthDepth, into);
        if (distinct == 1) {
            lengthDepth[onlyCode] = 0;
        }
        for (int each = 0; each < written; each++) {
            int symbol = symbols[each];
            into.write(lengthDepth[symbol], lengthBits[symbol]);
            if (symbol == REPEAT_PREVIOUS_CODE_LENGTH) {
                into.write(2, extra[each]);
            } else if (symbol == REPEAT_ZERO_CODE_LENGTH) {
                into.write(3, extra[each]);
            }
        }
    }

    private static void storeTheCodeOverCodeLengths(int distinct,
            int[] lengthDepth, BrotliBits into) {

        int toStore = CODE_LENGTH_CODES;
        if (distinct > 1) {
            while (toStore > 0
                    && lengthDepth[CODE_LENGTH_STORAGE_ORDER[toStore - 1]] == 0) {
                toStore--;
            }
        }
        int skip = 0;
        if (lengthDepth[CODE_LENGTH_STORAGE_ORDER[0]] == 0
                && lengthDepth[CODE_LENGTH_STORAGE_ORDER[1]] == 0) {
            skip = lengthDepth[CODE_LENGTH_STORAGE_ORDER[2]] == 0 ? 3 : 2;
        }
        into.write(2, skip);
        for (int each = skip; each < toStore; each++) {
            int width = lengthDepth[CODE_LENGTH_STORAGE_ORDER[each]];
            into.write(LENGTH_CODE_WIDTHS[width], LENGTH_CODE_SYMBOLS[width]);
        }
    }

    /**
     * The code lengths of a prefix code, run-length coded.
     *
     * <p>Trailing zeros are dropped, and whether runs are coded at all is
     * decided by counting them first: for a short code the run markers cost
     * more than the lengths they save, so a code of fifty symbols or fewer
     * never uses them.
     */
    private static int writeTreeAsCodeLengths(int[] depth, int at, int length,
        int[] symbols, int[] extra) {

    int newLength = length;
    while (newLength > 0 && depth[at + newLength - 1] == 0) {
        newLength--;
    }
    boolean runsForNonZero = false;
    boolean runsForZero = false;
    if (length > 50) {
        long totalZeroRuns = 0;
        long totalOtherRuns = 0;
        long countZeroRuns = 1;
        long countOtherRuns = 1;
        for (int each = 0; each < newLength; ) {
            int value = depth[at + each];
            int reps = 1;
            while (each + reps < newLength && depth[at + each + reps] == value) {
                reps++;
            }
            if (reps >= 3 && value == 0) {
                totalZeroRuns += reps;
                countZeroRuns++;
            }
            if (reps >= 4 && value != 0) {
                totalOtherRuns += reps;
                countOtherRuns++;
            }
            each += reps;
        }
        runsForNonZero = totalOtherRuns > countOtherRuns * 2;
        runsForZero = totalZeroRuns > countZeroRuns * 2;
    }

    int written = 0;
    int previousValue = INITIAL_REPEATED_CODE_LENGTH;
    for (int each = 0; each < newLength; ) {
        int value = depth[at + each];
        int reps = 1;
        if ((value != 0 && runsForNonZero) || (value == 0 && runsForZero)) {
            while (each + reps < newLength && depth[at + each + reps] == value) {
                reps++;
            }
        }
        if (value == 0) {
            written = writeZeroRun(reps, symbols, extra, written);
        } else {
            written = writeOtherRun(previousValue, value, reps,
                    symbols, extra, written);
            previousValue = value;
        }
        each += reps;
    }
    return written;
    }

    private static int writeZeroRun(int howMany, int[] symbols, int[] extra,
        int written) {

    int at = written;
    int reps = howMany;
    if (reps == 11) {
        symbols[at] = 0;
        extra[at] = 0;
        at++;
        reps--;
    }
    if (reps < 3) {
        for (int each = 0; each < reps; each++) {
            symbols[at] = 0;
            extra[at] = 0;
            at++;
        }
        return at;
    }
    int start = at;
    reps -= 3;
    while (true) {
        symbols[at] = REPEAT_ZERO_CODE_LENGTH;
        extra[at] = reps & 7;
        at++;
        reps >>= 3;
        if (reps == 0) {
            break;
        }
        reps--;
    }
    reverse(symbols, start, at);
    reverse(extra, start, at);
    return at;
    }

    private static int writeOtherRun(int previousValue, int value, int howMany,
        int[] symbols, int[] extra, int written) {

    int at = written;
    int reps = howMany;
    if (previousValue != value) {
        symbols[at] = value;
        extra[at] = 0;
        at++;
        reps--;
    }
    if (reps == 7) {
        symbols[at] = value;
        extra[at] = 0;
        at++;
        reps--;
    }
    if (reps < 3) {
        for (int each = 0; each < reps; each++) {
            symbols[at] = value;
            extra[at] = 0;
            at++;
        }
        return at;
    }
    int start = at;
    reps -= 3;
    while (true) {
        symbols[at] = REPEAT_PREVIOUS_CODE_LENGTH;
        extra[at] = reps & 3;
        at++;
        reps >>= 2;
        if (reps == 0) {
            break;
        }
        reps--;
    }
    reverse(symbols, start, at);
    reverse(extra, start, at);
    return at;
    }

    private static void reverse(int[] values, int from, int to) {
    for (int low = from, high = to - 1; low < high; low++, high--) {
        int swapped = values[low];
        values[low] = values[high];
        values[high] = swapped;
    }
    }
}
