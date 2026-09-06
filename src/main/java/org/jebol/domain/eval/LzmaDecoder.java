package org.jebol.domain.eval;

import java.util.Arrays;

/**
 * LZMA, the reading half.
 *
 * <p>{@code LzmaDec.c} of the LZMA SDK, dated 2018-02-28, as Rebol vendors it
 * in {@code u-lzma.c}. The window is the answer itself: {@code LzmaDecode}
 * points the dictionary straight at the caller's output buffer and sizes it to
 * how much output was asked for, so nothing here wraps and nothing is copied
 * twice.
 *
 * <p>The stream is read one symbol at a time until the output is as long as
 * was asked for. A symbol is a literal byte, a repeat of one of the four most
 * recent distances, or a fresh distance and a length. Running out of input
 * part way through a symbol is an error rather than a short answer, which is
 * what {@code LzmaDec_TryDummy} arranges in the C by refusing to start a
 * symbol it cannot finish.
 */
final class LzmaDecoder {

    private static final int TOP_VALUE = 1 << 24;
    private static final int BIT_MODEL_TOTAL_BITS = 11;
    private static final int BIT_MODEL_TOTAL = 1 << BIT_MODEL_TOTAL_BITS;
    private static final int MOVE_BITS = 5;
    private static final int PROBABILITY_INITIAL_VALUE = BIT_MODEL_TOTAL >> 1;
    private static final int RANGE_CODER_INITIAL_BYTES = 5;

    private static final int STATES = 12;
    private static final int LITERAL_STATES = 7;
    private static final int STATES_WITH_ROOM = 16;
    private static final int POSITION_BITS_MAX = 4;
    private static final int POSITION_STATES_MAX = 1 << POSITION_BITS_MAX;
    private static final int LENGTH_LOW_BITS = 3;
    private static final int LENGTH_LOW_SYMBOLS = 1 << LENGTH_LOW_BITS;
    private static final int LENGTH_HIGH_SYMBOLS = 1 << 8;
    private static final int WHERE_THE_LONG_LENGTHS_START =
            2 * (POSITION_STATES_MAX << LENGTH_LOW_BITS);
    private static final int LENGTH_PROBABILITIES =
            WHERE_THE_LONG_LENGTHS_START + LENGTH_HIGH_SYMBOLS;
    private static final int START_POSITION_MODEL_INDEX = 4;
    private static final int END_POSITION_MODEL_INDEX = 14;
    private static final int FULL_DISTANCES = 1 << (END_POSITION_MODEL_INDEX >> 1);
    private static final int POSITION_SLOT_BITS = 6;
    private static final int LENGTH_TO_POSITION_STATES = 4;
    private static final int ALIGN_BITS = 4;
    private static final int ALIGN_TABLE_SIZE = 1 << ALIGN_BITS;
    private static final int MATCH_MIN_LEN = 2;
    private static final int LITERAL_MODELS = 0x300;
    private static final int SMALLEST_DICTIONARY = 1 << 12;

    /**
     * The code a stream cannot open with.
     *
     * <p>{@code kBadRepCode}. Before a single byte has been produced there is
     * nothing to repeat, so a first symbol that would decode as a repeat is
     * data that was never LZMA. The constant is what the range coder's first
     * two decisions come to when both go that way.
     */
    private static final long BAD_REPEAT_CODE = 0xC0000000L - 0x400;

    /**
     * The distance that means "the stream stops here".
     *
     * <p>Rebol never writes one -- {@code CompressLzma} passes zero for the
     * end mark and states the length in four bytes of its own instead -- but a
     * stream from anywhere else may, and the C treats it as the end rather
     * than as damage: it stops and reports how much it made.
     */
    private static final int END_OF_STREAM = -1;

    private final int literalContextBits;
    private final int literalPositionMask;
    private final int positionMask;
    private final int dictionarySize;

    private final short[] specialPositions = new short[FULL_DISTANCES];
    private final short[] isRepeat0Long =
            new short[STATES_WITH_ROOM << POSITION_BITS_MAX];
    private final short[] repeatLengths = new short[LENGTH_PROBABILITIES];
    private final short[] matchLengths = new short[LENGTH_PROBABILITIES];
    private final short[] isMatch = new short[STATES_WITH_ROOM << POSITION_BITS_MAX];
    private final short[] align = new short[ALIGN_TABLE_SIZE];
    private final short[] isRepeat = new short[STATES];
    private final short[] isRepeatG0 = new short[STATES];
    private final short[] isRepeatG1 = new short[STATES];
    private final short[] isRepeatG2 = new short[STATES];
    private final short[] positionSlots =
            new short[LENGTH_TO_POSITION_STATES << POSITION_SLOT_BITS];
    private final short[] literals;

    private final byte[] input;
    private int inputAt;
    private final int inputEnd;
    private final byte[] output;

    private long range;
    private long code;
    private int state;
    private int outputAt;
    private int processed;
    private int checkedDictionarySize;
    private boolean finished;
    private final int[] recentDistances = {1, 1, 1, 1};

    /**
     * Reads the five property bytes and builds a decoder from them.
     *
     * <p>The first byte packs three numbers into one: how many bits of the
     * previous byte condition a literal, how many bits of the position do, and
     * how many bits of the position condition everything else. The other four
     * are the dictionary size, little endian, floored at four kilobytes.
     */
    private LzmaDecoder(byte[] properties, byte[] input, int from, int length,
            int wanted) {

        int packed = properties[0] & 0xFF;
        if (packed >= 9 * 5 * 5) {
            throw new IllegalArgumentException("not LZMA properties");
        }
        this.literalContextBits = packed % 9;
        int rest = packed / 9;
        int literalPositionBits = rest % 5;
        this.positionMask = (1 << (rest / 5)) - 1;
        this.literalPositionMask = (0x100 << literalPositionBits)
                - (0x100 >>> literalContextBits);
        int asked = 0;
        for (int each = 0; each < 4; each++) {
            asked |= (properties[1 + each] & 0xFF) << (8 * each);
        }
        this.dictionarySize =
                Integer.compareUnsigned(asked, SMALLEST_DICTIONARY) < 0
                        ? SMALLEST_DICTIONARY
                        : asked;
        this.literals = new short[LITERAL_MODELS
                << (literalContextBits + literalPositionBits)];
        this.input = input;
        this.inputAt = from;
        this.inputEnd = from + length;
        this.output = new byte[wanted];
    }

    /**
     * Reads {@code wanted} bytes of answer out of a stream.
     *
     * <p>Rebol always knows how long the answer is before it starts: either
     * DECOMPRESS/SIZE said so or the four bytes Rebol appends to the stream
     * did, so the buffer is made once and the decoder stops when it is full.
     */
    static byte[] decoded(byte[] properties, byte[] input, int from, int length,
            int wanted) {

        LzmaDecoder decoder =
                new LzmaDecoder(properties, input, from, length, wanted);
        decoder.readTheOpeningCode();
        decoder.run();
        return decoder.outputAt == wanted
                ? decoder.output
                : Arrays.copyOf(decoder.output, decoder.outputAt);
    }

    private void readTheOpeningCode() {
        if (inputEnd - inputAt < RANGE_CODER_INITIAL_BYTES) {
            throw new IllegalArgumentException("LZMA data ends before it starts");
        }
        if (input[inputAt] != 0) {
            throw new IllegalArgumentException("not an LZMA stream");
        }
        inputAt++;
        code = 0;
        for (int each = 0; each < 4; each++) {
            code = (code << 8) | (input[inputAt++] & 0xFF);
        }
        range = 0xFFFFFFFFL;
        Arrays.fill(specialPositions, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(isRepeat0Long, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(repeatLengths, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(matchLengths, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(isMatch, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(align, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(isRepeat, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(isRepeatG0, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(isRepeatG1, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(isRepeatG2, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(positionSlots, (short) PROBABILITY_INITIAL_VALUE);
        Arrays.fill(literals, (short) PROBABILITY_INITIAL_VALUE);
    }

    private void run() {
        if (output.length == 0) {
            return;
        }
        if (Long.compareUnsigned(code, BAD_REPEAT_CODE) >= 0) {
            throw new IllegalArgumentException("LZMA stream opens with a repeat");
        }
        while (outputAt < output.length && !finished) {
            decodeOneSymbol();
        }
    }

    private void normalize() {
        if (Long.compareUnsigned(range, TOP_VALUE) < 0) {
            range = (range << 8) & 0xFFFFFFFFL;
            code = ((code << 8) | (nextByte() & 0xFFL)) & 0xFFFFFFFFL;
        }
    }

    private int nextByte() {
        if (inputAt >= inputEnd) {
            throw new IllegalArgumentException("LZMA data ends part way through");
        }
        return input[inputAt++] & 0xFF;
    }

    private int decodeBit(short[] models, int at) {
        int model = models[at];
        normalize();
        long bound = (range >>> BIT_MODEL_TOTAL_BITS) * model;
        if (Long.compareUnsigned(code, bound) < 0) {
            range = bound;
            models[at] = (short) (model + ((BIT_MODEL_TOTAL - model) >>> MOVE_BITS));
            return 0;
        }
        range -= bound;
        code -= bound;
        models[at] = (short) (model - (model >>> MOVE_BITS));
        return 1;
    }

    private int decodeTree(short[] models, int base, int howManyBits) {
        int at = 1;
        for (int each = 0; each < howManyBits; each++) {
            at = (at << 1) + decodeBit(models, base + at);
        }
        return at - (1 << howManyBits);
    }

    private int positionStateFor(int howManyDone) {
        return (howManyDone & positionMask) << POSITION_BITS_MAX;
    }

    /**
     * Once the answer is longer than the dictionary, the dictionary is what
     * bounds a distance rather than how much has been made.
     *
     * <p>{@code checkDicSize}. Until then a distance may not reach before the
     * start of the answer; after it, it may not reach further back than the
     * window the stream was written with. The C sets it between calls to the
     * inner loop and caps that loop so the crossing lands on a boundary; here
     * the crossing is noticed at the next symbol, which is the same boundary.
     */
    private void noteWhetherTheDictionaryIsFull() {
        if (checkedDictionarySize == 0
                && Integer.compareUnsigned(processed, dictionarySize) >= 0) {
            checkedDictionarySize = dictionarySize;
        }
    }

    private void decodeOneSymbol() {
        noteWhetherTheDictionaryIsFull();
        int positionState = positionStateFor(processed);
        if (decodeBit(isMatch, positionState + state) == 0) {
            decodeLiteral();
            return;
        }
        int length;
        if (decodeBit(isRepeat, state) == 0) {
            state += STATES;
            length = decodeLength(matchLengths, positionState);
            decodeDistanceAndCopy(length);
            return;
        }
        length = decodeRepeat(positionState);
        if (length == THE_REPEAT_WAS_A_SINGLE_BYTE) {
            return;
        }
        copyFromTheDistance(length);
    }

    private void decodeLiteral() {
        int base = 0;
        if (processed != 0 || checkedDictionarySize != 0) {
            base = 3 * (((((processed << 8) + (output[outputAt - 1] & 0xFF))
                    & literalPositionMask)) << literalContextBits);
        }
        processed++;
        int symbol = 1;
        if (state < LITERAL_STATES) {
            state -= state < 4 ? state : 3;
            while (symbol < 0x100) {
                symbol = (symbol << 1) + decodeBit(literals, base + symbol);
            }
        } else {
            int matchByte = output[outputAt - recentDistances[0]] & 0xFF;
            int offsets = 0x100;
            state -= state < 10 ? 3 : 6;
            while (symbol < 0x100) {
                matchByte += matchByte;
                int wasOffsets = offsets;
                offsets &= matchByte;
                int bit = decodeBit(literals, base + offsets + wasOffsets + symbol);
                symbol = (symbol << 1) + bit;
                if (bit == 0) {
                    offsets ^= wasOffsets;
                }
            }
        }
        output[outputAt++] = (byte) symbol;
    }

    /**
     * The single-byte repeat writes itself and has no length to report.
     *
     * <p>It cannot be reported as a length of zero, because zero is the
     * shortest length a real repeat has: the symbol zero means two bytes,
     * lengths being written with two subtracted. Conflating the two lost the
     * last two bytes of anything that ended on a two-byte repeat, and only
     * the last two, which is exactly the kind of fault a round trip through
     * short data never shows.
     */
    private static final int THE_REPEAT_WAS_A_SINGLE_BYTE = -1;

    /** The length symbol of the repeat, or {@link #THE_REPEAT_WAS_A_SINGLE_BYTE}. */
    private int decodeRepeat(int positionState) {
        if (decodeBit(isRepeatG0, state) == 0) {
            if (decodeBit(isRepeat0Long, positionState + state) == 0) {
                output[outputAt] = output[outputAt - recentDistances[0]];
                outputAt++;
                processed++;
                state = state < LITERAL_STATES ? 9 : 11;
                return THE_REPEAT_WAS_A_SINGLE_BYTE;
            }
        } else {
            int distance;
            if (decodeBit(isRepeatG1, state) == 0) {
                distance = recentDistances[1];
            } else {
                if (decodeBit(isRepeatG2, state) == 0) {
                    distance = recentDistances[2];
                } else {
                    distance = recentDistances[3];
                    recentDistances[3] = recentDistances[2];
                }
                recentDistances[2] = recentDistances[1];
            }
            recentDistances[1] = recentDistances[0];
            recentDistances[0] = distance;
        }
        state = state < LITERAL_STATES ? 8 : 11;
        return decodeLength(repeatLengths, positionState);
    }

    private int decodeLength(short[] models, int positionState) {
        if (decodeBit(models, 0) == 0) {
            return decodeTree(models, positionState, LENGTH_LOW_BITS);
        }
        if (decodeBit(models, LENGTH_LOW_SYMBOLS) == 0) {
            return LENGTH_LOW_SYMBOLS
                    + decodeTree(models, positionState + LENGTH_LOW_SYMBOLS,
                            LENGTH_LOW_BITS);
        }
        return LENGTH_LOW_SYMBOLS * 2
                + decodeTree(models, WHERE_THE_LONG_LENGTHS_START, 8);
    }

    private void decodeDistanceAndCopy(int length) {
        int slot = decodeTree(positionSlots,
                Math.min(length, LENGTH_TO_POSITION_STATES - 1)
                        << POSITION_SLOT_BITS,
                POSITION_SLOT_BITS);
        int distance = slot;
        if (slot >= START_POSITION_MODEL_INDEX) {
            int directBits = (slot >> 1) - 1;
            distance = 2 | (slot & 1);
            if (slot < END_POSITION_MODEL_INDEX) {
                distance <<= directBits;
                distance += readReversed(specialPositions, distance, directBits);
            } else {
                distance = readTheDirectBits(distance, directBits - ALIGN_BITS);
                distance = (distance << ALIGN_BITS)
                        | readReversed(align, 0, ALIGN_BITS);
                if (distance == END_OF_STREAM) {
                    finished = true;
                    return;
                }
            }
        }
        recentDistances[3] = recentDistances[2];
        recentDistances[2] = recentDistances[1];
        recentDistances[1] = recentDistances[0];
        recentDistances[0] = distance + 1;
        state = state < STATES + LITERAL_STATES
                ? LITERAL_STATES
                : LITERAL_STATES + 3;
        int reachable =
                checkedDictionarySize == 0 ? processed : checkedDictionarySize;
        if (Integer.compareUnsigned(distance, reachable) >= 0) {
            throw new IllegalArgumentException("LZMA distance reaches before the start");
        }
        copyFromTheDistance(length);
    }

    /**
     * A bit tree read least significant bit first, which is how both the low
     * bits of a near distance and the four aligned bits of a far one are
     * written.
     *
     * <p>The node walked to is {@code (node &lt;&lt; 1) | bit} and the answer is
     * built up the other way round, one bit at a time from the bottom. That is
     * the exact inverse of {@code RcTree_ReverseEncode}, and it has to be
     * spelled that way round: the encoder and the decoder must arrive at the
     * same node for the same run of bits, or they adapt different models and
     * the second run of three bits already disagrees.
     */
    private int readReversed(short[] models, int base, int howManyBits) {
        int at = 1;
        int symbol = 0;
        for (int each = 0; each < howManyBits; each++) {
            int bit = decodeBit(models, base + at);
            at = (at << 1) | bit;
            symbol |= bit << each;
        }
        return symbol;
    }

    /**
     * Bits with no model behind them, which is how the middle of a large
     * distance is written.
     *
     * <p>Subtracting the halved range and looking at whether it went below
     * zero is the comparison and the update in one, which is what the C does
     * to keep the branch out of the loop. Java has no thirty-two bit unsigned
     * type, so the subtraction is masked back before its top bit is read.
     */
    private int readTheDirectBits(int startingFrom, int howManyBits) {
        int distance = startingFrom;
        for (int each = 0; each < howManyBits; each++) {
            normalize();
            range >>>= 1;
            code = (code - range) & 0xFFFFFFFFL;
            if ((code >>> 31) != 0) {
                code = (code + range) & 0xFFFFFFFFL;
                distance = distance << 1;
            } else {
                distance = (distance << 1) + 1;
            }
        }
        return distance;
    }


    /**
     * Copies the run, and stops short when the answer is already as long as
     * was asked for.
     *
     * <p>A run may reach past the end of what was wanted, which is not an
     * error: DECOMPRESS/SIZE asks for the front of something and the last run
     * before that point is allowed to be longer than the room left.
     */
    private void copyFromTheDistance(int length) {
        int wholeLength = length + MATCH_MIN_LEN;
        int room = output.length - outputAt;
        if (room == 0) {
            throw new IllegalArgumentException("LZMA run past the end of the answer");
        }
        int howMany = Math.min(room, wholeLength);
        processed += howMany;
        int from = outputAt - recentDistances[0];
        for (int each = 0; each < howMany; each++) {
            output[outputAt++] = output[from++];
        }
    }
}
