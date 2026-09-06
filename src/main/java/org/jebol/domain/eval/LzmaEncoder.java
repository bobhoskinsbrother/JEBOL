package org.jebol.domain.eval;

import java.util.Arrays;

/**
 * LZMA, the compressing half.
 *
 * <p>{@code LzmaEnc.c} of the LZMA SDK, dated 2018-04-29, as Rebol vendors it
 * in {@code u-lzma.c}. Everything Rebol can reach is here and nothing else is:
 * the end marker is not written, because {@code CompressLzma} passes zero for
 * it, and the stream-at-a-time interface is not ported, because Rebol always
 * hands over a whole buffer.
 *
 * <p>What the format is: an arithmetic coder over adaptive bit models. Each
 * step writes either a literal byte or a repeat of something earlier, and a
 * twelve-value state remembers what the last few steps were so the models can
 * be conditioned on it. A repeat is either one of the four most recent
 * distances, which are cheap to name, or a fresh distance written as a slot,
 * some direct bits and four aligned bits.
 *
 * <p>Two ways of choosing what to write, and the level picks one. Below level
 * five it is greedy with one byte of lookahead. From level five it prices
 * every choice over a window of up to four thousand positions and walks the
 * cheapest path back, which is what makes the same bytes come out as a real
 * 3.22.5 rather than merely something that reads back.
 *
 * <p>The prices are in sixteenths of a bit, from a table built once at
 * construction, and they are refreshed as the models drift: the length tables
 * on a counter, the distance and alignment tables when enough matches have
 * gone by.
 */
final class LzmaEncoder {

    private static final int NUMBER_OF_STATES = 12;
    private static final int NUMBER_OF_REPEATS = 4;
    private static final int TOP_VALUE = 1 << 24;
    private static final int BIT_MODEL_TOTAL_BITS = 11;
    private static final int BIT_MODEL_TOTAL = 1 << BIT_MODEL_TOTAL_BITS;
    private static final int MOVE_BITS = 5;
    private static final int PROBABILITY_INITIAL_VALUE = BIT_MODEL_TOTAL >> 1;
    private static final int MOVE_REDUCING_BITS = 4;
    private static final int BIT_PRICE_SHIFT_BITS = 4;

    private static final int LENGTH_LOW_BITS = 3;
    private static final int LENGTH_LOW_SYMBOLS = 1 << LENGTH_LOW_BITS;
    private static final int LENGTH_HIGH_SYMBOLS = 1 << 8;
    private static final int LENGTH_SYMBOLS_TOTAL =
            LENGTH_LOW_SYMBOLS * 2 + LENGTH_HIGH_SYMBOLS;
    static final int MATCH_LEN_MIN = 2;
    static final int MATCH_LEN_MAX = MATCH_LEN_MIN + LENGTH_SYMBOLS_TOTAL - 1;

    private static final int POSITION_STATES_MAX = 1 << 4;
    private static final int LENGTH_TO_POSITION_STATES = 4;
    private static final int POSITION_SLOT_BITS = 6;
    private static final int DISTANCE_TABLE_SIZE_MAX = 64;
    private static final int ALIGN_BITS = 4;
    private static final int ALIGN_TABLE_SIZE = 1 << ALIGN_BITS;
    private static final int ALIGN_MASK = ALIGN_TABLE_SIZE - 1;
    private static final int START_POSITION_MODEL_INDEX = 4;
    private static final int END_POSITION_MODEL_INDEX = 14;
    private static final int FULL_DISTANCES = 1 << (END_POSITION_MODEL_INDEX >> 1);

    private static final int CHOICES = 1 << 12;
    private static final int INFINITY_PRICE = 1 << 30;
    private static final int MARK_LITERAL = -1;
    private static final int LOG_BITS = 13;
    private static final int NOBODY_ASKED = -1;

    private static final int STATE_LITERAL_AFTER_MATCH = 4;
    private static final int STATE_LITERAL_AFTER_REPEAT = 5;
    private static final int STATE_MATCH_AFTER_LITERAL = 7;
    private static final int STATE_REPEAT_AFTER_LITERAL = 8;

    private static final byte[] AFTER_A_LITERAL =
            {0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 4, 5};
    private static final byte[] AFTER_A_MATCH =
            {7, 7, 7, 7, 7, 7, 7, 10, 10, 10, 10, 10};
    private static final byte[] AFTER_A_REPEAT =
            {8, 8, 8, 8, 8, 8, 8, 11, 11, 11, 11, 11};
    private static final byte[] AFTER_A_SHORT_REPEAT =
            {9, 9, 9, 9, 9, 9, 9, 11, 11, 11, 11, 11};

    private static final int[] PROBABILITY_PRICES = probabilityPrices();
    private static final byte[] SLOT_OF_A_SMALL_DISTANCE = slotTable();

    /**
     * The price in sixteenths of a bit of coding a bit whose model stands at
     * each of the 128 rounded probabilities.
     *
     * <p>Squaring the probability four times and counting how far it has to be
     * shifted back under sixteen bits is a base-two logarithm worked out in
     * integers, which is what the C does and why the table is not simply
     * {@code -log2(p)} rounded.
     */
    private static int[] probabilityPrices() {
        int[] prices = new int[BIT_MODEL_TOTAL >> MOVE_REDUCING_BITS];
        for (int each = 0; each < prices.length; each++) {
            int weight = (each << MOVE_REDUCING_BITS)
                    + (1 << (MOVE_REDUCING_BITS - 1));
            int bitCount = 0;
            for (int cycle = 0; cycle < BIT_PRICE_SHIFT_BITS; cycle++) {
                weight = weight * weight;
                bitCount <<= 1;
                while (Integer.compareUnsigned(weight, 1 << 16) >= 0) {
                    weight >>>= 1;
                    bitCount++;
                }
            }
            prices[each] =
                    (BIT_MODEL_TOTAL_BITS << BIT_PRICE_SHIFT_BITS) - 15 - bitCount;
        }
        return prices;
    }

    /** Which slot a distance below {@code 1 << 19} falls in, six bits down. */
    private static byte[] slotTable() {
        byte[] table = new byte[1 << LOG_BITS];
        table[0] = 0;
        table[1] = 1;
        int at = 2;
        for (int slot = 2; slot < LOG_BITS * 2; slot++) {
            int howMany = 1 << ((slot >> 1) - 1);
            for (int each = 0; each < howMany; each++) {
                table[at++] = (byte) slot;
            }
        }
        return table;
    }

    private static int slotOf(int distance) {
        if (Integer.compareUnsigned(distance, FULL_DISTANCES) < 0) {
            return SLOT_OF_A_SMALL_DISTANCE[distance & (FULL_DISTANCES - 1)];
        }
        return slotOfALargeDistance(distance);
    }

    private static int slotOfALargeDistance(int distance) {
        int shift = Integer.compareUnsigned(distance, 1 << (LOG_BITS + 6)) < 0
                ? 6
                : 6 + LOG_BITS - 1;
        return SLOT_OF_A_SMALL_DISTANCE[distance >>> shift] + shift * 2;
    }

    /**
     * The five bytes a stream opens with: how the literal context is split,
     * then the dictionary size.
     *
     * <p>The size written is not the size asked for. Below four megabytes it
     * is rounded up to two or three times a power of two, and above that to a
     * whole number of megabytes, so a reader can size its window from one byte
     * of exponent rather than from an arbitrary number.
     */
    static byte[] properties(int level) {
        Settings settings = Settings.forLevel(level);
        byte[] written = new byte[5];
        written[0] = (byte) ((settings.positionBits * 5 + settings.literalPositionBits) * 9
                + settings.literalContextBits);
        int size = roundedDictionarySize(settings.dictionarySize);
        for (int each = 0; each < 4; each++) {
            written[1 + each] = (byte) (size >>> (8 * each));
        }
        return written;
    }

    private static int roundedDictionarySize(int dictionarySize) {
        if (Integer.compareUnsigned(dictionarySize, 1 << 22) >= 0) {
            int mask = (1 << 20) - 1;
            return Integer.compareUnsigned(dictionarySize, 0xFFFFFFFF - mask) < 0
                    ? (dictionarySize + mask) & ~mask
                    : dictionarySize;
        }
        for (int power = 11; power <= 30; power++) {
            if (Integer.compareUnsigned(dictionarySize, 2 << power) <= 0) {
                return 2 << power;
            }
            if (Integer.compareUnsigned(dictionarySize, 3 << power) <= 0) {
                return 3 << power;
            }
        }
        return dictionarySize;
    }

    /**
     * Everything the level decides, worked out once.
     *
     * <p>{@code LzmaEncProps_Normalize}. Rebol asks for a level and leaves
     * every other field at its default, so this is the whole of what a level
     * means: how far back to look, how hard to look, and whether to price the
     * choices or take the first good one.
     */
    private record Settings(
            int dictionarySize,
            int literalContextBits,
            int literalPositionBits,
            int positionBits,
            int numFastBytes,
            boolean binaryTree,
            boolean pricing,
            int cutValue) {

        /**
         * {@code level = (level == UNKNOWN) ? 5 : MIN(9, level);} in
         * {@code CompressLzma}, over an unsigned level.
         *
         * <p>Which makes minus one the level nobody asked for and every other
         * negative the slowest, because it arrives as a number near four
         * thousand million and gets clamped down to nine. That is not a
         * reading of the C so much as a consequence of it, and
         * {@code compress/level x 'lzma -5} really does answer what level nine
         * answers.
         */
        static Settings forLevel(int asked) {
            int level = asked == NOBODY_ASKED
                    ? 5
                    : (Integer.compareUnsigned(asked, 9) > 0 ? 9 : asked);
            int dictionarySize = level <= 5
                    ? 1 << (level * 2 + 14)
                    : (level <= 7 ? 1 << 25 : 1 << 26);
            boolean pricing = level >= 5;
            int numFastBytes = level < 7 ? 32 : 64;
            int cutValue = (16 + (numFastBytes >> 1)) >> (pricing ? 0 : 1);
            return new Settings(dictionarySize, 3, 0, 2,
                    numFastBytes, pricing, pricing, cutValue);
        }
    }

    private final byte[] source;
    private final Settings settings;
    private final int positionMask;
    private final int literalPositionMask;
    private final int distanceTableSize;
    private final LzmaMatchFinder finder;
    private final RangeEncoder writer = new RangeEncoder();

    private final short[] literalProbabilities;
    private final short[] positionAlign = new short[ALIGN_TABLE_SIZE];
    private final short[] isMatch = new short[NUMBER_OF_STATES * POSITION_STATES_MAX];
    private final short[] isRepeat0Long =
            new short[NUMBER_OF_STATES * POSITION_STATES_MAX];
    private final short[] isRepeat = new short[NUMBER_OF_STATES];
    private final short[] isRepeatG0 = new short[NUMBER_OF_STATES];
    private final short[] isRepeatG1 = new short[NUMBER_OF_STATES];
    private final short[] isRepeatG2 = new short[NUMBER_OF_STATES];
    private final short[] slotEncoder =
            new short[LENGTH_TO_POSITION_STATES << POSITION_SLOT_BITS];
    private final short[] positionEncoders = new short[FULL_DISTANCES];
    private final LengthModel matchLengths = new LengthModel();
    private final LengthModel repeatLengths = new LengthModel();
    private final LengthPrices matchLengthPrices = new LengthPrices();
    private final LengthPrices repeatLengthPrices = new LengthPrices();

    private final int[] alignPrices = new int[ALIGN_TABLE_SIZE];
    private final int[] slotPrices =
            new int[LENGTH_TO_POSITION_STATES * DISTANCE_TABLE_SIZE_MAX];
    private final int[] distancePrices =
            new int[LENGTH_TO_POSITION_STATES * FULL_DISTANCES];

    private final int[] matches = new int[MATCH_LEN_MAX * 2 + 2 + 1];
    private final int[] repeats = new int[NUMBER_OF_REPEATS];

    private final int[] choicePrice = new int[CHOICES];
    private final int[] choiceState = new int[CHOICES];
    private final int[] choiceExtra = new int[CHOICES];
    private final int[] choiceLength = new int[CHOICES];
    private final int[] choiceDistance = new int[CHOICES];
    private final int[] choiceRepeats = new int[CHOICES * NUMBER_OF_REPEATS];

    private int state;
    private int additionalOffset;
    private int longestMatchLen;
    private int numPairs;
    private int freshPairCount;
    private int available;
    private int chosenDistance;
    private int choiceCursor;
    private int choiceEnd;
    private int matchPriceCount;
    private int alignPriceCount;

    private LzmaEncoder(byte[] source, int level) {
        this.source = source;
        this.settings = Settings.forLevel(level);
        this.positionMask = (1 << settings.positionBits) - 1;
        this.literalPositionMask = (0x100 << settings.literalPositionBits)
                - (0x100 >>> settings.literalContextBits);
        this.distanceTableSize = distanceTableSizeFor(settings.dictionarySize);
        this.literalProbabilities = new short[0x300
                << (settings.literalContextBits + settings.literalPositionBits)];
        this.finder = new LzmaMatchFinder(source, settings.dictionarySize,
                MATCH_LEN_MAX, settings.binaryTree, settings.cutValue);
    }

    private static int distanceTableSizeFor(int dictionarySize) {
        int power = END_POSITION_MODEL_INDEX / 2;
        while (power < 32
                && Integer.compareUnsigned(dictionarySize, 1 << power) > 0) {
            power++;
        }
        return power * 2;
    }

    static byte[] encoded(byte[] source, int level) {
        LzmaEncoder encoder = new LzmaEncoder(source, level);
        encoder.start();
        encoder.run();
        return encoder.writer.written();
    }

    private void start() {
        state = 0;
        Arrays.fill(repeats, 1);
        Arrays.fill(positionAlign, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(isMatch, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(isRepeat0Long, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(isRepeat, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(isRepeatG0, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(isRepeatG1, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(isRepeatG2, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(slotEncoder, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(positionEncoders, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(literalProbabilities, (short) PROBABILITY_INITIAL_VALUE);
        matchLengths.reset();
        repeatLengths.reset();
        choiceEnd = 0;
        choiceCursor = 0;
        additionalOffset = 0;
        if (settings.pricing) {
            fillDistancePrices();
            fillAlignPrices();
        }
        matchLengthPrices.tableSize = settings.numFastBytes + 1 - MATCH_LEN_MIN;
        repeatLengthPrices.tableSize = matchLengthPrices.tableSize;
        matchLengthPrices.updateEveryTable(matchLengths, 1 << settings.positionBits);
        repeatLengthPrices.updateEveryTable(repeatLengths, 1 << settings.positionBits);
    }

    private void run() {
        int at = 0;
        if (finder.availableBytes() == 0) {
            writer.flush();
            return;
        }
        readMatchDistances();
        writer.encodeBitZero(isMatch, 0);
        int firstByte = source[finder.currentPosition() - additionalOffset] & 0xFF;
        writer.encodeLiteral(literalProbabilities, 0, firstByte);
        additionalOffset--;
        at++;

        if (finder.availableBytes() != 0) {
            while (true) {
                int length = nextStep(at);
                int distance = chosenDistance;
                int positionState = at & positionMask;
                if (distance == MARK_LITERAL) {
                    writeLiteral(at, positionState);
                } else {
                    writeRepeatOrMatch(distance, length, positionState);
                }
                at += length;
                additionalOffset -= length;
                if (additionalOffset != 0) {
                    continue;
                }
                if (settings.pricing) {
                    if (matchPriceCount >= (1 << 7)) {
                        fillDistancePrices();
                    }
                    if (alignPriceCount >= ALIGN_TABLE_SIZE) {
                        fillAlignPrices();
                    }
                }
                if (finder.availableBytes() == 0) {
                    break;
                }
            }
        }
        writer.flush();
    }

    private int nextStep(int at) {
        if (!settings.pricing) {
            return greedyStep();
        }
        if (choiceEnd == choiceCursor) {
            return pricedStep(at);
        }
        int length = choiceLength[choiceCursor];
        chosenDistance = choiceDistance[choiceCursor];
        choiceCursor++;
        return length;
    }

    private void writeLiteral(int at, int positionState) {
        writer.encodeBit(isMatch, state * POSITION_STATES_MAX + positionState, 0);
        int data = finder.currentPosition() - additionalOffset;
        int probabilitiesAt = literalProbabilitiesAt(at, source[data - 1] & 0xFF);
        int wasState = state;
        state = AFTER_A_LITERAL[state];
        if (wasState < 7) {
            writer.encodeLiteral(literalProbabilities, probabilitiesAt,
                    source[data] & 0xFF);
        } else {
            writer.encodeMatchedLiteral(literalProbabilities, probabilitiesAt,
                    source[data] & 0xFF, source[data - repeats[0]] & 0xFF);
        }
    }

    private void writeRepeatOrMatch(int asked, int length, int positionState) {
        writer.encodeBit(isMatch, state * POSITION_STATES_MAX + positionState, 1);
        if (asked < NUMBER_OF_REPEATS) {
            writer.encodeBit(isRepeat, state, 1);
            writeRepeat(asked, length, positionState);
            return;
        }
        writer.encodeBit(isRepeat, state, 0);
        writeMatch(asked - NUMBER_OF_REPEATS, length, positionState);
    }

    private void writeRepeat(int which, int length, int positionState) {
        if (which == 0) {
            writer.encodeBit(isRepeatG0, state, 0);
            writer.encodeBit(isRepeat0Long,
                    state * POSITION_STATES_MAX + positionState, length != 1 ? 1 : 0);
            if (length == 1) {
                state = AFTER_A_SHORT_REPEAT[state];
                return;
            }
        } else {
            writer.encodeBit(isRepeatG0, state, 1);
            int distance;
            if (which == 1) {
                writer.encodeBit(isRepeatG1, state, 0);
                distance = repeats[1];
            } else {
                writer.encodeBit(isRepeatG1, state, 1);
                if (which == 2) {
                    writer.encodeBit(isRepeatG2, state, 0);
                    distance = repeats[2];
                } else {
                    writer.encodeBit(isRepeatG2, state, 1);
                    distance = repeats[3];
                    repeats[3] = repeats[2];
                }
                repeats[2] = repeats[1];
            }
            repeats[1] = repeats[0];
            repeats[0] = distance;
        }
        repeatLengths.encode(writer, length - MATCH_LEN_MIN, positionState);
        if (settings.pricing && --repeatLengthPrices.counters[positionState] == 0) {
            repeatLengthPrices.updateTable(repeatLengths, positionState);
        }
        state = AFTER_A_REPEAT[state];
    }

    private void writeMatch(int distance, int length, int positionState) {
        state = AFTER_A_MATCH[state];
        matchLengths.encode(writer, length - MATCH_LEN_MIN, positionState);
        if (settings.pricing && --matchLengthPrices.counters[positionState] == 0) {
            matchLengthPrices.updateTable(matchLengths, positionState);
        }
        repeats[3] = repeats[2];
        repeats[2] = repeats[1];
        repeats[1] = repeats[0];
        repeats[0] = distance + 1;
        matchPriceCount++;
        int slot = slotOf(distance);
        writer.encodeSlot(slotEncoder,
                lengthToPositionState(length) << POSITION_SLOT_BITS, slot);
        if (distance < START_POSITION_MODEL_INDEX) {
            return;
        }
        int footerBits = (slot >> 1) - 1;
        if (Integer.compareUnsigned(distance, FULL_DISTANCES) < 0) {
            int base = (2 | (slot & 1)) << footerBits;
            writer.encodeReversed(positionEncoders, base, footerBits, distance - base);
            return;
        }
        writer.encodeDirectBits(distance >>> ALIGN_BITS, footerBits - ALIGN_BITS);
        writer.encodeReversed(positionAlign, 0, ALIGN_BITS, distance & ALIGN_MASK);
        alignPriceCount++;
    }

    private int literalProbabilitiesAt(int at, int previousByte) {
        return 3 * ((((at << 8) + previousByte) & literalPositionMask)
                << settings.literalContextBits);
    }

    private static int lengthToPositionState(int length) {
        return length < LENGTH_TO_POSITION_STATES + 1
                ? length - 2
                : LENGTH_TO_POSITION_STATES - 1;
    }

    private static int lengthToPositionStateFromSpan(int span) {
        return Math.min(span, LENGTH_TO_POSITION_STATES - 1);
    }

    /**
     * Asks the finder about this position and leaves the pair count in
     * {@link #freshPairCount}.
     *
     * <p>The C passes the count out through a pointer that the caller aims at
     * either a local or at {@code p->numPairs}, and the difference matters:
     * {@code GetOptimumFast} shortens its own copy while looking for a nearer
     * match of the same length, and that shortening must not be visible the
     * next time the field is read.
     */
    private int readMatchDistances() {
        additionalOffset++;
        available = finder.availableBytes();
        freshPairCount = finder.matches(matches);
        if (freshPairCount == 0) {
            return 0;
        }
        int length = matches[freshPairCount - 2];
        if (length != settings.numFastBytes) {
            return length;
        }
        int reach = Math.min(available, MATCH_LEN_MAX);
        int data = finder.currentPosition() - 1;
        int distance = matches[freshPairCount - 1] + 1;
        int grown = length;
        while (grown != reach && source[data + grown] == source[data + grown - distance]) {
            grown++;
        }
        return grown;
    }

    private void movePosition(int howMany) {
        additionalOffset += howMany;
        finder.skip(howMany);
    }

    private int greedyStep() {
        int mainLength;
        int pairs;
        if (additionalOffset == 0) {
            mainLength = readMatchDistances();
            pairs = freshPairCount;
        } else {
            mainLength = longestMatchLen;
            pairs = numPairs;
        }
        int reach = available;
        chosenDistance = MARK_LITERAL;
        if (reach < 2) {
            return 1;
        }
        reach = Math.min(reach, MATCH_LEN_MAX);
        int data = finder.currentPosition() - 1;
        int repeatLength = 0;
        int repeatIndex = 0;
        for (int which = 0; which < NUMBER_OF_REPEATS; which++) {
            int earlier = data - repeats[which];
            if (source[data] != source[earlier]
                    || source[data + 1] != source[earlier + 1]) {
                continue;
            }
            int length = 2;
            while (length < reach && source[data + length] == source[earlier + length]) {
                length++;
            }
            if (length >= settings.numFastBytes) {
                chosenDistance = which;
                movePosition(length - 1);
                return length;
            }
            if (length > repeatLength) {
                repeatIndex = which;
                repeatLength = length;
            }
        }
        if (mainLength >= settings.numFastBytes) {
            chosenDistance = matches[pairs - 1] + NUMBER_OF_REPEATS;
            movePosition(mainLength - 1);
            return mainLength;
        }
        int mainDistance = 0;
        if (mainLength >= 2) {
            mainDistance = matches[pairs - 1];
            while (pairs > 2) {
                if (mainLength != matches[pairs - 4] + 1) {
                    break;
                }
                int shorter = matches[pairs - 3];
                if (!worthTheExtraDistance(shorter, mainDistance)) {
                    break;
                }
                pairs -= 2;
                mainLength--;
                mainDistance = shorter;
            }
            if (mainLength == 2 && mainDistance >= 0x80) {
                mainLength = 1;
            }
        }
        if (repeatLength >= 2
                && (repeatLength + 1 >= mainLength
                        || (repeatLength + 2 >= mainLength && mainDistance >= (1 << 9))
                        || (repeatLength + 3 >= mainLength && mainDistance >= (1 << 15)))) {
            chosenDistance = repeatIndex;
            movePosition(repeatLength - 1);
            return repeatLength;
        }
        if (mainLength < 2 || reach <= 2) {
            return 1;
        }
        longestMatchLen = readMatchDistances();
        numPairs = freshPairCount;
        if (longestMatchLen >= 2) {
            int newDistance = matches[numPairs - 1];
            if ((longestMatchLen >= mainLength && newDistance < mainDistance)
                    || (longestMatchLen == mainLength + 1
                            && !worthTheExtraDistance(mainDistance, newDistance))
                    || longestMatchLen > mainLength + 1
                    || (longestMatchLen + 1 >= mainLength && mainLength >= 3
                            && worthTheExtraDistance(newDistance, mainDistance))) {
                return 1;
            }
        }
        data = finder.currentPosition() - 1;
        for (int which = 0; which < NUMBER_OF_REPEATS; which++) {
            int earlier = data - repeats[which];
            if (source[data] != source[earlier]
                    || source[data + 1] != source[earlier + 1]) {
                continue;
            }
            int limit = mainLength - 1;
            for (int length = 2; ; length++) {
                if (length >= limit) {
                    return 1;
                }
                if (source[data + length] != source[earlier + length]) {
                    break;
                }
            }
        }
        chosenDistance = mainDistance + NUMBER_OF_REPEATS;
        if (mainLength != 2) {
            movePosition(mainLength - 2);
        }
        return mainLength;
    }

    private static boolean worthTheExtraDistance(int nearer, int further) {
        return (further >>> 7) > nearer;
    }

    private static int priceOfABitIn(short[] models, int at, int bit) {
        return PROBABILITY_PRICES[
                (models[at] ^ (-bit & (BIT_MODEL_TOTAL - 1))) >>> MOVE_REDUCING_BITS];
    }

    private static int priceOfAZeroIn(short[] models, int at) {
        return PROBABILITY_PRICES[models[at] >>> MOVE_REDUCING_BITS];
    }

    private static int priceOfAOneIn(short[] models, int at) {
        return PROBABILITY_PRICES[
                (models[at] ^ (BIT_MODEL_TOTAL - 1)) >>> MOVE_REDUCING_BITS];
    }

    private int priceOfALiteral(int probabilitiesAt, int symbol) {
        int price = 0;
        int running = symbol | 0x100;
        do {
            int bit = running & 1;
            running >>= 1;
            price += priceOfABitIn(literalProbabilities, probabilitiesAt + running, bit);
        } while (running >= 2);
        return price;
    }

    private int priceOfAMatchedLiteral(
            int probabilitiesAt, int symbol, int matchByte) {

        int price = 0;
        int offsets = 0x100;
        int running = symbol | 0x100;
        int matched = matchByte;
        do {
            matched <<= 1;
            price += priceOfABitIn(literalProbabilities,
                    probabilitiesAt + offsets + (matched & offsets) + (running >> 8),
                    (running >> 7) & 1);
            running <<= 1;
            offsets &= ~(matched ^ running);
        } while (running < 0x10000);
        return price;
    }

    private int priceOfAShortRepeat(int forState, int positionState) {
        return priceOfAZeroIn(isRepeatG0, forState)
                + priceOfAZeroIn(isRepeat0Long,
                        forState * POSITION_STATES_MAX + positionState);
    }

    private int priceOfRepeatZero(int forState, int positionState) {
        return priceOfAOneIn(isMatch, forState * POSITION_STATES_MAX + positionState)
                + priceOfAOneIn(isRepeat0Long,
                        forState * POSITION_STATES_MAX + positionState)
                + priceOfAOneIn(isRepeat, forState)
                + priceOfAZeroIn(isRepeatG0, forState);
    }

    private int priceOfNamingARepeat(int which, int forState, int positionState) {
        if (which == 0) {
            return priceOfAZeroIn(isRepeatG0, forState)
                    + priceOfAOneIn(isRepeat0Long,
                            forState * POSITION_STATES_MAX + positionState);
        }
        int price = priceOfAOneIn(isRepeatG0, forState);
        if (which == 1) {
            return price + priceOfAZeroIn(isRepeatG1, forState);
        }
        return price + priceOfAOneIn(isRepeatG1, forState)
                + priceOfABitIn(isRepeatG2, forState, which - 2);
    }

    private void fillAlignPrices() {
        alignPriceCount = 0;
        for (int each = 0; each < ALIGN_TABLE_SIZE / 2; each++) {
            int price = 0;
            int symbol = each;
            int at = 1;
            for (int step = 0; step < 3; step++) {
                int bit = symbol & 1;
                symbol >>= 1;
                price += priceOfABitIn(positionAlign, at, bit);
                at = (at << 1) + bit;
            }
            alignPrices[each] = price + priceOfAZeroIn(positionAlign, at);
            alignPrices[each + 8] = price + priceOfAOneIn(positionAlign, at);
        }
    }

    private void fillDistancePrices() {
        int[] footerPrices = new int[FULL_DISTANCES];
        matchPriceCount = 0;
        for (int distance = START_POSITION_MODEL_INDEX;
                distance < FULL_DISTANCES; distance++) {
            int slot = SLOT_OF_A_SMALL_DISTANCE[distance];
            int footerBits = (slot >> 1) - 1;
            int base = (2 | (slot & 1)) << footerBits;
            int price = 0;
            int at = 1;
            int symbol = distance - base;
            int remaining = footerBits;
            do {
                int bit = symbol & 1;
                symbol >>= 1;
                price += priceOfABitIn(positionEncoders, base + at, bit);
                at = (at << 1) + bit;
            } while (--remaining != 0);
            footerPrices[distance] = price;
        }

        for (int span = 0; span < LENGTH_TO_POSITION_STATES; span++) {
            int slotBase = span << POSITION_SLOT_BITS;
            int priceBase = span * DISTANCE_TABLE_SIZE_MAX;
            for (int slot = 0; slot < distanceTableSize; slot += 2) {
                int price = 0;
                int symbol = (slot >> 1) + (1 << (POSITION_SLOT_BITS - 1));
                for (int step = 0; step < 5; step++) {
                    int bit = symbol & 1;
                    symbol >>= 1;
                    price += priceOfABitIn(slotEncoder, slotBase + symbol, bit);
                }
                int model = slotBase + (slot >> 1) + (1 << (POSITION_SLOT_BITS - 1));
                slotPrices[priceBase + slot] =
                        price + priceOfAZeroIn(slotEncoder, model);
                slotPrices[priceBase + slot + 1] =
                        price + priceOfAOneIn(slotEncoder, model);
            }
            for (int slot = END_POSITION_MODEL_INDEX; slot < distanceTableSize; slot++) {
                slotPrices[priceBase + slot] +=
                        (((slot >> 1) - 1) - ALIGN_BITS) << BIT_PRICE_SHIFT_BITS;
            }
            int distanceBase = span * FULL_DISTANCES;
            for (int each = 0; each < 4; each++) {
                distancePrices[distanceBase + each] = slotPrices[priceBase + each];
            }
            for (int distance = 4; distance < FULL_DISTANCES; distance += 2) {
                int slotPrice =
                        slotPrices[priceBase + SLOT_OF_A_SMALL_DISTANCE[distance]];
                distancePrices[distanceBase + distance] =
                        slotPrice + footerPrices[distance];
                distancePrices[distanceBase + distance + 1] =
                        slotPrice + footerPrices[distance + 1];
            }
        }
    }

    private void makeLiteral(int at) {
        choiceDistance[at] = MARK_LITERAL;
        choiceExtra[at] = 0;
    }

    private void makeShortRepeat(int at) {
        choiceDistance[at] = 0;
        choiceExtra[at] = 0;
    }

    /**
     * Walks the cheapest path back from where the search stopped.
     *
     * <p>{@code Backward}. The forward pass left each position holding what it
     * cost to arrive there and what the last step was, so the answer is read
     * out by following those steps back to the start and writing them into the
     * same array in order. An {@code extra} on a step means the step was a
     * match or repeat followed by a literal and then a repeat of distance
     * zero, which is stored as one choice and unpacks into two or three.
     */
    private int walkBack(int from) {
        int at = from;
        int writeAt = from + 1;
        choiceEnd = writeAt;
        while (true) {
            int distance = choiceDistance[at];
            int length = choiceLength[at];
            int extra = choiceExtra[at];
            at -= length;
            if (extra != 0) {
                writeAt--;
                choiceLength[writeAt] = length;
                at -= extra;
                length = extra;
                if (extra == 1) {
                    choiceDistance[writeAt] = distance;
                    distance = MARK_LITERAL;
                } else {
                    choiceDistance[writeAt] = 0;
                    length--;
                    writeAt--;
                    choiceDistance[writeAt] = MARK_LITERAL;
                    choiceLength[writeAt] = 1;
                }
            }
            if (at == 0) {
                chosenDistance = distance;
                choiceCursor = writeAt;
                return length;
            }
            writeAt--;
            choiceDistance[writeAt] = distance;
            choiceLength[writeAt] = length;
        }
    }

    private int pricedStep(int startPosition) {
        int position = startPosition;
        int last;
        choiceCursor = 0;
        choiceEnd = 0;

        int mainLength;
        int pairs;
        if (additionalOffset == 0) {
            mainLength = readMatchDistances();
            pairs = freshPairCount;
        } else {
            mainLength = longestMatchLen;
            pairs = numPairs;
        }

        int reach = available;
        if (reach < 2) {
            chosenDistance = MARK_LITERAL;
            return 1;
        }
        reach = Math.min(reach, MATCH_LEN_MAX);

        int data = finder.currentPosition() - 1;
        int[] repeatLengths = new int[NUMBER_OF_REPEATS];
        int[] current = new int[NUMBER_OF_REPEATS];
        int longestRepeat = 0;
        for (int which = 0; which < NUMBER_OF_REPEATS; which++) {
            current[which] = repeats[which];
            int earlier = data - current[which];
            if (source[data] != source[earlier]
                    || source[data + 1] != source[earlier + 1]) {
                repeatLengths[which] = 0;
                continue;
            }
            int length = 2;
            while (length < reach && source[data + length] == source[earlier + length]) {
                length++;
            }
            repeatLengths[which] = length;
            if (length > repeatLengths[longestRepeat]) {
                longestRepeat = which;
            }
        }

        if (repeatLengths[longestRepeat] >= settings.numFastBytes) {
            chosenDistance = longestRepeat;
            int length = repeatLengths[longestRepeat];
            movePosition(length - 1);
            return length;
        }
        if (mainLength >= settings.numFastBytes) {
            chosenDistance = matches[pairs - 1] + NUMBER_OF_REPEATS;
            movePosition(mainLength - 1);
            return mainLength;
        }

        int currentByte = source[data] & 0xFF;
        int matchByte = source[data - current[0]] & 0xFF;

        if (mainLength < 2 && currentByte != matchByte
                && repeatLengths[longestRepeat] < 2) {
            chosenDistance = MARK_LITERAL;
            return 1;
        }

        choiceState[0] = state;
        int positionState = position & positionMask;
        int matchModel = state * POSITION_STATES_MAX + positionState;
        int probabilitiesAt = literalProbabilitiesAt(position, source[data - 1] & 0xFF);
        choicePrice[1] = priceOfAZeroIn(isMatch, matchModel)
                + (state < 7
                        ? priceOfALiteral(probabilitiesAt, currentByte)
                        : priceOfAMatchedLiteral(probabilitiesAt, currentByte, matchByte));
        makeLiteral(1);

        int matchPrice = priceOfAOneIn(isMatch, matchModel);
        int repeatMatchPrice = matchPrice + priceOfAOneIn(isRepeat, state);

        if (matchByte == currentByte) {
            int shortRepeatPrice =
                    repeatMatchPrice + priceOfAShortRepeat(state, positionState);
            if (shortRepeatPrice < choicePrice[1]) {
                choicePrice[1] = shortRepeatPrice;
                makeShortRepeat(1);
            }
        }

        last = Math.max(mainLength, repeatLengths[longestRepeat]);
        if (last < 2) {
            chosenDistance = choiceDistance[1];
            return 1;
        }
        choiceLength[1] = 1;
        for (int which = 0; which < NUMBER_OF_REPEATS; which++) {
            choiceRepeats[which] = current[which];
        }
        for (int at = last; at >= 2; at--) {
            choicePrice[at] = INFINITY_PRICE;
        }

        for (int which = 0; which < NUMBER_OF_REPEATS; which++) {
            int repeatLength = repeatLengths[which];
            if (repeatLength < 2) {
                continue;
            }
            int price = repeatMatchPrice
                    + priceOfNamingARepeat(which, state, positionState);
            for (int length = repeatLength; length >= 2; length--) {
                int total = price
                        + repeatLengthPrices.prices[positionState][length - 2];
                if (total < choicePrice[length]) {
                    choicePrice[length] = total;
                    choiceLength[length] = length;
                    choiceDistance[length] = which;
                    choiceExtra[length] = 0;
                }
            }
        }

        int startLength = repeatLengths[0] >= 2 ? repeatLengths[0] + 1 : 2;
        if (startLength <= mainLength) {
            int offset = 0;
            int normalMatchPrice = matchPrice + priceOfAZeroIn(isRepeat, state);
            while (startLength > matches[offset]) {
                offset += 2;
            }
            for (int length = startLength; ; length++) {
                int distance = matches[offset + 1];
                int price = normalMatchPrice
                        + matchLengthPrices.prices[positionState][length - MATCH_LEN_MIN];
                int span = lengthToPositionState(length);
                if (Integer.compareUnsigned(distance, FULL_DISTANCES) < 0) {
                    price += distancePrices[span * FULL_DISTANCES
                            + (distance & (FULL_DISTANCES - 1))];
                } else {
                    price += alignPrices[distance & ALIGN_MASK]
                            + slotPrices[span * DISTANCE_TABLE_SIZE_MAX
                                    + slotOfALargeDistance(distance)];
                }
                if (price < choicePrice[length]) {
                    choicePrice[length] = price;
                    choiceLength[length] = length;
                    choiceDistance[length] = distance + NUMBER_OF_REPEATS;
                    choiceExtra[length] = 0;
                }
                if (length == matches[offset]) {
                    offset += 2;
                    if (offset == pairs) {
                        break;
                    }
                }
            }
        }

        int at = 0;
        while (true) {
            if (++at == last) {
                return walkBack(at);
            }
            int newLength = readMatchDistances();
            int freshPairs = freshPairCount;
            if (newLength >= settings.numFastBytes) {
                numPairs = freshPairs;
                longestMatchLen = newLength;
                return walkBack(at);
            }

            int previous = at - choiceLength[at];
            int stateHere;
            if (choiceLength[at] == 1) {
                stateHere = choiceState[previous];
                stateHere = choiceDistance[at] == 0
                        ? AFTER_A_SHORT_REPEAT[stateHere]
                        : AFTER_A_LITERAL[stateHere];
            } else {
                int distance = choiceDistance[at];
                if (choiceExtra[at] != 0) {
                    previous -= choiceExtra[at];
                    stateHere = choiceExtra[at] == 1
                            ? (distance < NUMBER_OF_REPEATS
                                    ? STATE_REPEAT_AFTER_LITERAL
                                    : STATE_MATCH_AFTER_LITERAL)
                            : STATE_REPEAT_AFTER_LITERAL;
                } else {
                    stateHere = distance < NUMBER_OF_REPEATS
                            ? AFTER_A_REPEAT[choiceState[previous]]
                            : AFTER_A_MATCH[choiceState[previous]];
                }
                int previousRepeats = previous * NUMBER_OF_REPEATS;
                int first = choiceRepeats[previousRepeats];
                if (distance < NUMBER_OF_REPEATS) {
                    if (distance == 0) {
                        current[0] = first;
                        current[1] = choiceRepeats[previousRepeats + 1];
                        current[2] = choiceRepeats[previousRepeats + 2];
                        current[3] = choiceRepeats[previousRepeats + 3];
                    } else {
                        current[1] = first;
                        first = choiceRepeats[previousRepeats + 1];
                        if (distance == 1) {
                            current[0] = first;
                            current[2] = choiceRepeats[previousRepeats + 2];
                            current[3] = choiceRepeats[previousRepeats + 3];
                        } else {
                            current[2] = first;
                            current[0] = choiceRepeats[previousRepeats + distance];
                            current[3] = choiceRepeats[previousRepeats + (distance ^ 1)];
                        }
                    }
                } else {
                    current[0] = distance - NUMBER_OF_REPEATS + 1;
                    current[1] = first;
                    current[2] = choiceRepeats[previousRepeats + 1];
                    current[3] = choiceRepeats[previousRepeats + 2];
                }
            }

            choiceState[at] = stateHere;
            int repeatsHere = at * NUMBER_OF_REPEATS;
            choiceRepeats[repeatsHere] = current[0];
            choiceRepeats[repeatsHere + 1] = current[1];
            choiceRepeats[repeatsHere + 2] = current[2];
            choiceRepeats[repeatsHere + 3] = current[3];

            data = finder.currentPosition() - 1;
            currentByte = source[data] & 0xFF;
            matchByte = source[data - current[0]] & 0xFF;

            position++;
            positionState = position & positionMask;

            int priceHere = choicePrice[at];
            int stateModel = stateHere * POSITION_STATES_MAX + positionState;
            int literalPrice = priceHere + priceOfAZeroIn(isMatch, stateModel);
            boolean nextIsLiteral = false;
            probabilitiesAt =
                    literalProbabilitiesAt(position, source[data - 1] & 0xFF);
            literalPrice += stateHere < 7
                    ? priceOfALiteral(probabilitiesAt, currentByte)
                    : priceOfAMatchedLiteral(probabilitiesAt, currentByte, matchByte);
            if (literalPrice < choicePrice[at + 1]) {
                choicePrice[at + 1] = literalPrice;
                choiceLength[at + 1] = 1;
                makeLiteral(at + 1);
                nextIsLiteral = true;
            }

            matchPrice = priceHere + priceOfAOneIn(isMatch, stateModel);
            repeatMatchPrice = matchPrice + priceOfAOneIn(isRepeat, stateHere);

            if (matchByte == currentByte
                    && (choiceLength[at + 1] < 2
                            || (choiceDistance[at + 1] != 0
                                    && choiceExtra[at + 1] <= 1))) {
                int shortRepeatPrice = repeatMatchPrice
                        + priceOfAShortRepeat(stateHere, positionState);
                if (shortRepeatPrice <= choicePrice[at + 1]) {
                    choicePrice[at + 1] = shortRepeatPrice;
                    choiceLength[at + 1] = 1;
                    makeShortRepeat(at + 1);
                    nextIsLiteral = false;
                }
            }

            int reachFull = Math.min(available, CHOICES - 1 - at);
            if (reachFull < 2) {
                continue;
            }
            reach = Math.min(reachFull, settings.numFastBytes);

            if (!nextIsLiteral && matchByte != currentByte && reachFull > 2) {
                int earlier = data - current[0];
                if (source[data + 1] == source[earlier + 1]
                        && source[data + 2] == source[earlier + 2]) {
                    int limit = Math.min(settings.numFastBytes + 1, reachFull);
                    int length = 3;
                    while (length < limit
                            && source[data + length] == source[earlier + length]) {
                        length++;
                    }
                    int stateAfter = AFTER_A_LITERAL[stateHere];
                    int positionAfter = (position + 1) & positionMask;
                    int price = literalPrice
                            + priceOfRepeatZero(stateAfter, positionAfter);
                    int offset = at + length;
                    while (last < offset) {
                        choicePrice[++last] = INFINITY_PRICE;
                    }
                    length--;
                    int total = price
                            + repeatLengthPrices.prices[positionAfter][length - MATCH_LEN_MIN];
                    if (total < choicePrice[offset]) {
                        choicePrice[offset] = total;
                        choiceLength[offset] = length;
                        choiceDistance[offset] = 0;
                        choiceExtra[offset] = 1;
                    }
                }
            }

            startLength = 2;
            for (int which = 0; which < NUMBER_OF_REPEATS; which++) {
                int earlier = data - current[which];
                if (source[data] != source[earlier]
                        || source[data + 1] != source[earlier + 1]) {
                    continue;
                }
                int length = 2;
                while (length < reach
                        && source[data + length] == source[earlier + length]) {
                    length++;
                }
                while (last < at + length) {
                    choicePrice[++last] = INFINITY_PRICE;
                }
                int price = repeatMatchPrice
                        + priceOfNamingARepeat(which, stateHere, positionState);
                for (int span = length; span >= 2; span--) {
                    int total = price
                            + repeatLengthPrices.prices[positionState][span - 2];
                    if (total < choicePrice[at + span]) {
                        choicePrice[at + span] = total;
                        choiceLength[at + span] = span;
                        choiceDistance[at + span] = which;
                        choiceExtra[at + span] = 0;
                    }
                }
                if (which == 0) {
                    startLength = length + 1;
                }

                int beyond = length + 1;
                int limit = Math.min(beyond + settings.numFastBytes, reachFull);
                while (beyond < limit && source[data + beyond] == source[earlier + beyond]) {
                    beyond++;
                }
                beyond -= length;
                if (beyond >= 3) {
                    int stateAfter = AFTER_A_REPEAT[stateHere];
                    int positionAfter = (position + length) & positionMask;
                    price += repeatLengthPrices.prices[positionState][length - 2]
                            + priceOfAZeroIn(isMatch,
                                    stateAfter * POSITION_STATES_MAX + positionAfter)
                            + priceOfAMatchedLiteral(
                                    literalProbabilitiesAt(position + length,
                                            source[data + length - 1] & 0xFF),
                                    source[data + length] & 0xFF,
                                    source[earlier + length] & 0xFF);
                    stateAfter = STATE_LITERAL_AFTER_REPEAT;
                    positionAfter = (positionAfter + 1) & positionMask;
                    price += priceOfRepeatZero(stateAfter, positionAfter);
                    int offset = at + length + beyond;
                    while (last < offset) {
                        choicePrice[++last] = INFINITY_PRICE;
                    }
                    beyond--;
                    int total = price
                            + repeatLengthPrices.prices[positionAfter][beyond - MATCH_LEN_MIN];
                    if (total < choicePrice[offset]) {
                        choicePrice[offset] = total;
                        choiceLength[offset] = beyond;
                        choiceExtra[offset] = length + 1;
                        choiceDistance[offset] = which;
                    }
                }
            }

            if (newLength > reach) {
                newLength = reach;
                freshPairs = 0;
                while (newLength > matches[freshPairs]) {
                    freshPairs += 2;
                }
                matches[freshPairs] = newLength;
                freshPairs += 2;
            }

            if (newLength >= startLength) {
                int normalMatchPrice = matchPrice + priceOfAZeroIn(isRepeat, stateHere);
                while (last < at + newLength) {
                    choicePrice[++last] = INFINITY_PRICE;
                }
                int offset = 0;
                while (startLength > matches[offset]) {
                    offset += 2;
                }
                int distance = matches[offset + 1];
                int slot = slotOfALargeDistance(distance);
                for (int length = startLength; ; length++) {
                    int price = normalMatchPrice
                            + matchLengthPrices.prices[positionState][length - MATCH_LEN_MIN];
                    int span = lengthToPositionStateFromSpan(length - 2);
                    if (Integer.compareUnsigned(distance, FULL_DISTANCES) < 0) {
                        price += distancePrices[span * FULL_DISTANCES
                                + (distance & (FULL_DISTANCES - 1))];
                    } else {
                        price += slotPrices[span * DISTANCE_TABLE_SIZE_MAX + slot]
                                + alignPrices[distance & ALIGN_MASK];
                    }
                    if (price < choicePrice[at + length]) {
                        choicePrice[at + length] = price;
                        choiceLength[at + length] = length;
                        choiceDistance[at + length] = distance + NUMBER_OF_REPEATS;
                        choiceExtra[at + length] = 0;
                    }

                    if (length == matches[offset]) {
                        int earlier = data - distance - 1;
                        int beyond = length + 1;
                        int limit = Math.min(beyond + settings.numFastBytes, reachFull);
                        while (beyond < limit
                                && source[data + beyond] == source[earlier + beyond]) {
                            beyond++;
                        }
                        beyond -= length;
                        if (beyond >= 3) {
                            int stateAfter = AFTER_A_MATCH[stateHere];
                            int positionAfter = (position + length) & positionMask;
                            price += priceOfAZeroIn(isMatch,
                                    stateAfter * POSITION_STATES_MAX + positionAfter);
                            price += priceOfAMatchedLiteral(
                                    literalProbabilitiesAt(position + length,
                                            source[data + length - 1] & 0xFF),
                                    source[data + length] & 0xFF,
                                    source[earlier + length] & 0xFF);
                            stateAfter = STATE_LITERAL_AFTER_MATCH;
                            positionAfter = (positionAfter + 1) & positionMask;
                            price += priceOfRepeatZero(stateAfter, positionAfter);
                            int target = at + length + beyond;
                            while (last < target) {
                                choicePrice[++last] = INFINITY_PRICE;
                            }
                            beyond--;
                            int total = price + repeatLengthPrices
                                    .prices[positionAfter][beyond - MATCH_LEN_MIN];
                            if (total < choicePrice[target]) {
                                choicePrice[target] = total;
                                choiceLength[target] = beyond;
                                choiceExtra[target] = length + 1;
                                choiceDistance[target] = distance + NUMBER_OF_REPEATS;
                            }
                        }
                        offset += 2;
                        if (offset == freshPairs) {
                            break;
                        }
                        distance = matches[offset + 1];
                        slot = slotOfALargeDistance(distance);
                    }
                }
            }
        }
    }

    /**
     * The length of a match, written as one of three brackets: eight short
     * lengths per position state, eight more, and then 256 long ones shared
     * between every position state.
     */
    private static final class LengthModel {

        private final short[] low =
                new short[POSITION_STATES_MAX << (LENGTH_LOW_BITS + 1)];
        private final short[] high = new short[LENGTH_HIGH_SYMBOLS];

        void reset() {
            Arrays.fill(low, (short) PROBABILITY_INITIAL_VALUE);
            Arrays.fill(high, (short) PROBABILITY_INITIAL_VALUE);
        }

        void encode(RangeEncoder writer, int symbol, int positionState) {
            int at = 0;
            int remaining = symbol;
            if (remaining >= LENGTH_LOW_SYMBOLS) {
                writer.encodeBit(low, 0, 1);
                at = LENGTH_LOW_SYMBOLS;
                if (remaining >= LENGTH_LOW_SYMBOLS * 2) {
                    writer.encodeBit(low, at, 1);
                    writer.encodeLiteral(high, 0,
                            remaining - LENGTH_LOW_SYMBOLS * 2);
                    return;
                }
                remaining -= LENGTH_LOW_SYMBOLS;
            }
            writer.encodeBit(low, at, 0);
            int base = at + (positionState << (1 + LENGTH_LOW_BITS));
            int bit = remaining >> 2;
            writer.encodeBit(low, base + 1, bit);
            int slot = 2 + bit;
            bit = (remaining >> 1) & 1;
            writer.encodeBit(low, base + slot, bit);
            slot = (slot << 1) + bit;
            writer.encodeBit(low, base + slot, remaining & 1);
        }
    }

    /** What each length costs, per position state, refreshed on a counter. */
    private static final class LengthPrices {

        private int tableSize;
        private final int[] counters = new int[POSITION_STATES_MAX];
        private final int[][] prices =
                new int[POSITION_STATES_MAX][LENGTH_SYMBOLS_TOTAL];

        void updateEveryTable(LengthModel model, int howManyPositionStates) {
            for (int each = 0; each < howManyPositionStates; each++) {
                updateTable(model, each);
            }
        }

        void updateTable(LengthModel model, int positionState) {
            int[] row = prices[positionState];
            int base = positionState << (1 + LENGTH_LOW_BITS);
            setEightPrices(model.low, base, priceOfAZeroIn(model.low, 0), row, 0);
            int afterTheFirstOne = priceOfAOneIn(model.low, 0);
            setEightPrices(model.low, base + LENGTH_LOW_SYMBOLS,
                    afterTheFirstOne + priceOfAZeroIn(model.low, LENGTH_LOW_SYMBOLS),
                    row, LENGTH_LOW_SYMBOLS);
            int afterBothOnes = afterTheFirstOne
                    + priceOfAOneIn(model.low, LENGTH_LOW_SYMBOLS);
            counters[positionState] = tableSize;
            for (int symbol = LENGTH_LOW_SYMBOLS * 2; symbol < tableSize; symbol++) {
                row[symbol] = afterBothOnes + priceOfAShortTree(model.high,
                        symbol - LENGTH_LOW_SYMBOLS * 2);
            }
        }

        private static int priceOfAShortTree(short[] models, int symbol) {
            int price = 0;
            int running = symbol | 0x100;
            do {
                int bit = running & 1;
                running >>= 1;
                price += priceOfABitIn(models, running, bit);
            } while (running >= 2);
            return price;
        }

        private static void setEightPrices(
                short[] models, int base, int startPrice, int[] row, int at) {

            for (int each = 0; each < 8; each += 2) {
                int price = startPrice
                        + priceOfABitIn(models, base + 1, each >> 2)
                        + priceOfABitIn(models, base + 2 + (each >> 2), (each >> 1) & 1);
                int model = base + 4 + (each >> 1);
                row[at + each] = price + priceOfAZeroIn(models, model);
                row[at + each + 1] = price + priceOfAOneIn(models, model);
            }
        }
    }

    /**
     * The arithmetic coder.
     *
     * <p>A range and a low bound, both notionally thirty-two bits wide. Each
     * bit written splits the range in proportion to its model, and whenever the
     * range gets too narrow a byte of the low bound is settled and shifted out.
     * The carry is why a byte cannot simply be written: a run of {@code FF}
     * bytes is held back in {@code pendingOnes} until something below them
     * decides whether they carry.
     */
    private static final class RangeEncoder {

        private long range = 0xFFFFFFFFL;
        private long low;
        private int cache;
        private long pendingOnes;
        private byte[] written = new byte[1 << 12];
        private int at;

        byte[] written() {
            return Arrays.copyOf(written, at);
        }

        private void write(int octet) {
            if (at == written.length) {
                written = Arrays.copyOf(written, written.length * 2);
            }
            written[at++] = (byte) octet;
        }

        private void shiftLow() {
            long settled = low & 0xFFFFFFFFL;
            int carry = (int) (low >>> 32);
            low = (settled << 8) & 0xFFFFFFFFL;
            if (settled < 0xFF000000L || carry != 0) {
                write(cache + carry);
                cache = (int) (settled >>> 24);
                for (; pendingOnes != 0; pendingOnes--) {
                    write(carry + 0xFF);
                }
            } else {
                pendingOnes++;
            }
        }

        private void normalize() {
            if (range < TOP_VALUE) {
                range = (range << 8) & 0xFFFFFFFFL;
                shiftLow();
            }
        }

        void encodeBit(short[] models, int at, int bit) {
            int model = models[at];
            long bound = (range >>> BIT_MODEL_TOTAL_BITS) * model;
            if (bit == 0) {
                range = bound;
                models[at] = (short) (model + ((BIT_MODEL_TOTAL - model) >>> MOVE_BITS));
            } else {
                low += bound;
                range -= bound;
                models[at] = (short) (model - (model >>> MOVE_BITS));
            }
            normalize();
        }

        void encodeBitZero(short[] models, int at) {
            encodeBit(models, at, 0);
        }

        void encodeLiteral(short[] models, int base, int symbol) {
            int running = symbol | 0x100;
            do {
                int bit = (running >> 7) & 1;
                encodeBit(models, base + (running >> 8), bit);
                running <<= 1;
            } while (running < 0x10000);
        }

        void encodeMatchedLiteral(
                short[] models, int base, int symbol, int matchByte) {

            int offsets = 0x100;
            int running = symbol | 0x100;
            int matched = matchByte;
            do {
                matched <<= 1;
                int bit = (running >> 7) & 1;
                encodeBit(models, base + offsets + (matched & offsets) + (running >> 8),
                        bit);
                running <<= 1;
                offsets &= ~(matched ^ running);
            } while (running < 0x10000);
        }

        void encodeSlot(short[] models, int base, int slot) {
            int running = slot + (1 << POSITION_SLOT_BITS);
            do {
                encodeBit(models, base + (running >> POSITION_SLOT_BITS),
                        (running >> (POSITION_SLOT_BITS - 1)) & 1);
                running <<= 1;
            } while (running < (1 << (POSITION_SLOT_BITS * 2)));
        }

        void encodeReversed(short[] models, int base, int howManyBits, int symbol) {
            int at = 1;
            int running = symbol;
            for (int each = 0; each < howManyBits; each++) {
                int bit = running & 1;
                running >>= 1;
                encodeBit(models, base + at, bit);
                at = (at << 1) | bit;
            }
        }

        void encodeDirectBits(int value, int howManyBits) {
            for (int each = howManyBits - 1; each >= 0; each--) {
                range >>>= 1;
                low += range & -((value >>> each) & 1);
                normalize();
            }
        }

        void flush() {
            for (int each = 0; each < 5; each++) {
                shiftLow();
            }
        }
    }
}
