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
     * Base two logarithms of the first 256 whole numbers, as the C wrote them
     * down.
     *
     * <p>Not computed, because they cannot be. The table was generated once
     * and printed to sixteen digits, which is one short of what a double needs
     * to round-trip, so a hundred of the 256 entries are a last-bit away from
     * what {@code log2} answers today. What the C uses is the table, so what
     * this uses is the table.
     *
     * <p>It matters because these feed a comparison that decides whether a
     * block is stored or compressed, and a block stored where a real 3.22.5
     * compressed it is a different answer, not a slightly worse one.
     */
    private static final double[] LOG_2 = {
            0.0000000000000000, 0.0000000000000000, 1.0000000000000000, 1.5849625007211563,
            2.0000000000000000, 2.3219280948873622, 2.5849625007211561, 2.8073549220576042,
            3.0000000000000000, 3.1699250014423126, 3.3219280948873626, 3.4594316186372978,
            3.5849625007211565, 3.7004397181410922, 3.8073549220576037, 3.9068905956085187,
            4.0000000000000000, 4.0874628412503400, 4.1699250014423122, 4.2479275134435852,
            4.3219280948873626, 4.3923174227787607, 4.4594316186372973, 4.5235619560570131,
            4.5849625007211570, 4.6438561897747244, 4.7004397181410926, 4.7548875021634691,
            4.8073549220576037, 4.8579809951275728, 4.9068905956085187, 4.9541963103868758,
            5.0000000000000000, 5.0443941193584534, 5.0874628412503400, 5.1292830169449664,
            5.1699250014423122, 5.2094533656289501, 5.2479275134435852, 5.2854022188622487,
            5.3219280948873626, 5.3575520046180838, 5.3923174227787607, 5.4262647547020979,
            5.4594316186372973, 5.4918530963296748, 5.5235619560570131, 5.5545888516776376,
            5.5849625007211570, 5.6147098441152083, 5.6438561897747244, 5.6724253419714961,
            5.7004397181410926, 5.7279204545631996, 5.7548875021634691, 5.7813597135246599,
            5.8073549220576046, 5.8328900141647422, 5.8579809951275719, 5.8826430493618416,
            5.9068905956085187, 5.9307373375628867, 5.9541963103868758, 5.9772799234999168,
            6.0000000000000000, 6.0223678130284544, 6.0443941193584534, 6.0660891904577721,
            6.0874628412503400, 6.1085244567781700, 6.1292830169449672, 6.1497471195046822,
            6.1699250014423122, 6.1898245588800176, 6.2094533656289510, 6.2288186904958804,
            6.2479275134435861, 6.2667865406949019, 6.2854022188622487, 6.3037807481771031,
            6.3219280948873617, 6.3398500028846252, 6.3575520046180847, 6.3750394313469254,
            6.3923174227787598, 6.4093909361377026, 6.4262647547020979, 6.4429434958487288,
            6.4594316186372982, 6.4757334309663976, 6.4918530963296748, 6.5077946401986964,
            6.5235619560570131, 6.5391588111080319, 6.5545888516776376, 6.5698556083309478,
            6.5849625007211561, 6.5999128421871278, 6.6147098441152092, 6.6293566200796095,
            6.6438561897747253, 6.6582114827517955, 6.6724253419714952, 6.6865005271832185,
            6.7004397181410917, 6.7142455176661224, 6.7279204545631988, 6.7414669864011465,
            6.7548875021634691, 6.7681843247769260, 6.7813597135246599, 6.7944158663501062,
            6.8073549220576037, 6.8201789624151887, 6.8328900141647422, 6.8454900509443757,
            6.8579809951275719, 6.8703647195834048, 6.8826430493618416, 6.8948177633079437,
            6.9068905956085187, 6.9188632372745955, 6.9307373375628867, 6.9425145053392399,
            6.9541963103868758, 6.9657842846620879, 6.9772799234999168, 6.9886846867721664,
            7.0000000000000000, 7.0112272554232540, 7.0223678130284544, 7.0334230015374501,
            7.0443941193584534, 7.0552824355011898, 7.0660891904577721, 7.0768155970508317,
            7.0874628412503400, 7.0980320829605272, 7.1085244567781700, 7.1189410727235076,
            7.1292830169449664, 7.1395513523987937, 7.1497471195046822, 7.1598713367783891,
            7.1699250014423130, 7.1799090900149345, 7.1898245588800176, 7.1996723448363644,
            7.2094533656289492, 7.2191685204621621, 7.2288186904958804, 7.2384047393250794,
            7.2479275134435861, 7.2573878426926521, 7.2667865406949019, 7.2761244052742384,
            7.2854022188622487, 7.2946207488916270, 7.3037807481771031, 7.3128829552843557,
            7.3219280948873617, 7.3309168781146177, 7.3398500028846243, 7.3487281542310781,
            7.3575520046180847, 7.3663222142458151, 7.3750394313469254, 7.3837042924740528,
            7.3923174227787607, 7.4008794362821844, 7.4093909361377026, 7.4178525148858991,
            7.4262647547020979, 7.4346282276367255, 7.4429434958487288, 7.4512111118323299,
            7.4594316186372973, 7.4676055500829976, 7.4757334309663976, 7.4838157772642564,
            7.4918530963296748, 7.4998458870832057, 7.5077946401986964, 7.5156998382840436,
            7.5235619560570131, 7.5313814605163119, 7.5391588111080319, 7.5468944598876373,
            7.5545888516776376, 7.5622424242210728, 7.5698556083309478, 7.5774288280357487,
            7.5849625007211561, 7.5924570372680806, 7.5999128421871278, 7.6073303137496113,
            7.6147098441152075, 7.6220518194563764, 7.6293566200796095, 7.6366246205436488,
            7.6438561897747244, 7.6510516911789290, 7.6582114827517955, 7.6653359171851765,
            7.6724253419714952, 7.6794800995054464, 7.6865005271832185, 7.6934869574993252,
            7.7004397181410926, 7.7073591320808825, 7.7142455176661224, 7.7210991887071856,
            7.7279204545631996, 7.7347096202258392, 7.7414669864011465, 7.7481928495894596,
            7.7548875021634691, 7.7615512324444795, 7.7681843247769260, 7.7747870596011737,
            7.7813597135246608, 7.7879025593914317, 7.7944158663501062, 7.8008998999203047,
            7.8073549220576037, 7.8137811912170374, 7.8201789624151887, 7.8265484872909159,
            7.8328900141647422, 7.8392037880969445, 7.8454900509443757, 7.8517490414160571,
            7.8579809951275719, 7.8641861446542798, 7.8703647195834048, 7.8765169465650002,
            7.8826430493618425, 7.8887432488982601, 7.8948177633079446, 7.9008668079807496,
            7.9068905956085187, 7.9128893362299619, 7.9188632372745955, 7.9248125036057813,
            7.9307373375628867, 7.9366379390025719, 7.9425145053392399, 7.9483672315846778,
            7.9541963103868758, 7.9600019320680806, 7.9657842846620870, 7.9715435539507720,
            7.9772799234999168, 7.9829935746943104, 7.9886846867721664, 7.9943534368588578,
    };

    /** {@code log2}, whose defining property here is that log2 of nought is nought. */
    static double fastLog2(long value) {
        if (value < LOG_2.length) {
            return LOG_2[(int) value];
        }
        return Math.log((double) value) / Math.log(2.0);
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
