package org.jebol.domain.eval;

import java.util.Arrays;

/**
 * LZW, in the variant {@code u-lzw.c} carries: David Bryant's, with a
 * recycling dictionary and adjusted-binary codes.
 *
 * <p>Two things make it unlike the textbook algorithm, and both change the
 * bytes.
 *
 * <p>The codes are written in adjusted binary. A dictionary holding 257
 * strings would normally spend nine bits on every code; here the codes below
 * a threshold spend eight and only the ones above it spend nine, the
 * threshold moving as the dictionary grows. So the width of a code depends on
 * how many strings exist when it is written, and a decoder that counted
 * differently would read the whole stream wrong from that point on.
 *
 * <p>The dictionary is never simply cleared when it fills. Entries that
 * nothing longer is built on are recycled one at a time, and the encoder
 * keeps a count of how many remain; it starts over only when too few are left
 * or when a decaying average of the compression ratio says it has stopped
 * paying. That average is the reason for the two counters seeded at 65536 and
 * shaved by a two-hundred-and-fifty-sixth each round.
 *
 * <p>The first byte of the stream is the maximum symbol width less nine, so
 * the decoder can size its own tables. COMPRESS/LEVEL picks that width: level
 * one to seven give nine to fifteen bits, and anything else gives sixteen --
 * so level zero is the narrowest and level eight and above the widest, which
 * reads backwards until you notice level zero is spelled as "less than one".
 */
final class Lzw {

    private Lzw() {
    }

    private static final int NULL_CODE = 65535;
    private static final int CLEAR_CODE = 256;
    private static final int FIRST_STRING = 257;
    private static final int NO_RESULT = -1;

    /** The counters the compression-ratio average is measured in. */
    private static final int COUNTER_SEED = 65536;

    private static int codeBits(int number) {
        return 31 - Integer.numberOfLeadingZeros(number);
    }

    /** A run of bytes being written, growing as it goes. */
    private static final class Written {

        private byte[] held = new byte[64];
        private int used;

        void write(int octet) {
            if (used == held.length) {
                held = Arrays.copyOf(held, held.length * 2);
            }
            held[used++] = (byte) octet;
        }

        byte[] toArray() {
            return Arrays.copyOf(held, used);
        }
    }

    /**
     * How wide the symbols may get, from the level a caller asked for.
     *
     * <p>{@code if (level >= 1 && level <= 7) maxbits = 8 + level; else if
     * (level < 1) maxbits = 9;} and sixteen otherwise -- where the level is
     * an unsigned number. That last word is the whole of it: COMPRESS with no
     * /LEVEL passes {@code UNKNOWN}, which is minus one written into an
     * unsigned, so the comparison sees four thousand million and the answer is
     * sixteen. Reading the level as signed made the default the narrowest
     * width instead of the widest, and every byte after the first came out
     * differently.
     */
    static int widestSymbolFor(int level) {
        long asked = Integer.toUnsignedLong(level);
        if (asked >= 1 && asked <= 7) {
            return 8 + (int) asked;
        }
        return asked < 1 ? 9 : 16;
    }

    /** One string in the encoder's dictionary, and the chain it sits in. */
    private static final class Encoding {
        private int firstReference;
        private int nextReference;
        private int backReference;
        private int terminator;

        void clear() {
            firstReference = 0;
            nextReference = 0;
            backReference = 0;
            terminator = 0;
        }
    }

    /** The bit shifter both directions push codes through, low bits first. */
    private static final class Shifter {
        private int held;
        private int bits;
    }

    static byte[] compressed(byte[] source, int level) {
        int widest = widestSymbolFor(level);
        int totalCodes = 1 << widest;
        int mostEntriesAvailable = totalCodes - FIRST_STRING - 1;
        int highestCode = totalCodes - 2;

        Encoding[] dictionary = new Encoding[totalCodes];
        for (int at = 0; at < totalCodes; at++) {
            dictionary[at] = new Encoding();
        }

        Written into = new Written();
        Shifter shifter = new Shifter();
        into.write(widest - 9);

        int highestSoFar = FIRST_STRING;
        int nextString = FIRST_STRING;
        int prefix = NULL_CODE;
        boolean dictionaryFull = false;
        int entriesAvailable = mostEntriesAvailable;
        int inputBytes = COUNTER_SEED;
        int outputBytes = COUNTER_SEED;

        for (int at = 0; at <= source.length; at++) {
            int octet = at < source.length ? source[at] & 0xFF : NO_RESULT;
            if (octet == NO_RESULT) {
                break;
            }
            inputBytes += 256;
            if (prefix == NULL_CODE) {
                prefix = octet;
                continue;
            }
            dictionary[nextString].clear();

            int chained = dictionary[prefix].firstReference;
            if (chained != 0) {
                while (true) {
                    if (dictionary[chained].terminator == octet) {
                        prefix = chained;
                        break;
                    }
                    if (dictionary[chained].nextReference == 0) {
                        dictionary[chained].nextReference = nextString;
                        dictionary[nextString].backReference = chained;
                        chained = 0;
                        break;
                    }
                    chained = dictionary[chained].nextReference;
                }
            } else {
                dictionary[prefix].firstReference = nextString;
                dictionary[nextString].backReference = prefix;
                if (prefix >= FIRST_STRING) {
                    entriesAvailable--;
                }
            }
            if (chained != 0) {
                continue;
            }

            outputBytes = writeCode(into, shifter, prefix, highestSoFar, outputBytes);
            dictionary[nextString].terminator = octet;
            prefix = octet;

            if (!dictionaryFull) {
                dictionaryFull = ++nextString > highestCode;
                highestSoFar++;
            }

            if (dictionaryFull) {
                nextString = nextUnreferencedFrom(dictionary, nextString, highestCode);
                int holder = dictionary[nextString].backReference;
                if (dictionary[holder].firstReference == nextString) {
                    dictionary[holder].firstReference = dictionary[nextString].nextReference;
                    if (dictionary[holder].firstReference == 0 && holder >= FIRST_STRING) {
                        entriesAvailable++;
                    }
                } else if (dictionary[holder].nextReference == nextString) {
                    dictionary[holder].nextReference = dictionary[nextString].nextReference;
                }
                if (dictionary[nextString].nextReference != 0) {
                    dictionary[dictionary[nextString].nextReference].backReference = holder;
                }
                if (entriesAvailable < 16
                        || entriesAvailable * 100 < mostEntriesAvailable) {
                    outputBytes = writeCode(
                            into, shifter, CLEAR_CODE, highestSoFar, outputBytes);
                    clearTheFirstTwoHundredAndFiftySix(dictionary);
                    entriesAvailable = mostEntriesAvailable;
                    nextString = FIRST_STRING;
                    highestSoFar = FIRST_STRING;
                    inputBytes = COUNTER_SEED;
                    outputBytes = COUNTER_SEED;
                    dictionaryFull = false;
                }
            }

            if (outputBytes > inputBytes + (inputBytes >> 4)) {
                outputBytes = writeCode(
                        into, shifter, CLEAR_CODE, highestSoFar, outputBytes);
                clearTheFirstTwoHundredAndFiftySix(dictionary);
                entriesAvailable = mostEntriesAvailable;
                nextString = FIRST_STRING;
                highestSoFar = FIRST_STRING;
                inputBytes = COUNTER_SEED;
                outputBytes = COUNTER_SEED;
                dictionaryFull = false;
            } else {
                outputBytes -= outputBytes >> 8;
                inputBytes -= inputBytes >> 8;
            }
        }

        if (prefix != NULL_CODE) {
            outputBytes = writeCode(into, shifter, prefix, highestSoFar, outputBytes);
            if (!dictionaryFull) {
                highestSoFar++;
            }
        }
        writeCode(into, shifter, highestSoFar, highestSoFar, outputBytes);
        if (shifter.bits != 0) {
            into.write(shifter.held);
        }
        return into.toArray();
    }

    private static void clearTheFirstTwoHundredAndFiftySix(Encoding[] dictionary) {
        for (int at = 0; at < 256; at++) {
            dictionary[at].clear();
        }
    }

    private static int nextUnreferencedFrom(
            Encoding[] dictionary, int from, int highestCode) {

        int at = from + 1;
        while (true) {
            if (at > highestCode) {
                at = FIRST_STRING;
            }
            if (dictionary[at].firstReference == 0) {
                return at;
            }
            at++;
        }
    }

    /**
     * One code in adjusted binary, and the bytes it completes.
     *
     * <p>{@code extras} is how many codes fit in the narrower width; below it
     * a code goes out in {@code codeBits} bits and at or above it in one more,
     * the extra bit written after the rest so a reader can take the narrow
     * form first and widen only when it has to.
     */
    private static int writeCode(
            Written into, Shifter shifter, int code, int highest, int outputBytes) {

        int width = codeBits(highest);
        int extras = (2 << width) - highest - 1;
        int written = outputBytes;
        if (code < extras) {
            shifter.held |= code << shifter.bits;
            shifter.bits += width;
        } else {
            shifter.held |= ((code + extras) >> 1) << shifter.bits;
            shifter.bits += width;
            shifter.held |= ((code + extras) & 1) << shifter.bits++;
        }
        do {
            into.write(shifter.held);
            shifter.held >>>= 8;
            written += 256;
            shifter.bits -= 8;
        } while (shifter.bits >= 8);
        return written;
    }

    /** One string in the decoder's dictionary. */
    private static final class Decoding {
        private int terminator;
        private int extraReferences;
        private int prefix;
    }

    static byte[] decompressed(byte[] source, int limit) {
        if (source.length == 0 || (source[0] & 0xF8) != 0) {
            throw new IllegalArgumentException("lzw data does not open with a symbol width");
        }
        int totalCodes = 512 << (source[0] & 0x7);
        int highestCode = totalCodes - 2;

        Decoding[] dictionary = new Decoding[totalCodes];
        for (int at = 0; at < totalCodes; at++) {
            dictionary[at] = new Decoding();
            dictionary[at].prefix = NULL_CODE;
        }
        for (int at = 0; at < 256; at++) {
            dictionary[at].terminator = at;
        }
        boolean[] referenced = new boolean[totalCodes];
        int[] reversed = new int[totalCodes - 256];

        Written into = new Written();
        Shifter shifter = new Shifter();
        int reading = 1;
        int highestSoFar = FIRST_STRING;
        int nextString = FIRST_STRING - 1;
        int prefix = CLEAR_CODE;
        boolean dictionaryFull = false;

        while (true) {
            int width = codeBits(highestSoFar);
            int extras = (2 << width) - highestSoFar - 1;
            while (shifter.bits < width) {
                if (reading >= source.length) {
                    throw new IllegalArgumentException("lzw data ends before its end code");
                }
                shifter.held |= (source[reading++] & 0xFF) << shifter.bits;
                shifter.bits += 8;
            }
            int code = shifter.held & ((1 << width) - 1);
            shifter.held >>>= width;
            shifter.bits -= width;
            if (code >= extras) {
                if (shifter.bits == 0) {
                    if (reading >= source.length) {
                        throw new IllegalArgumentException(
                                "lzw data ends before its end code");
                    }
                    shifter.held = source[reading++] & 0xFF;
                    shifter.bits = 8;
                }
                code = (code << 1) - extras + (shifter.held & 1);
                shifter.held >>>= 1;
                shifter.bits--;
            }

            if (code == highestSoFar) {
                break;
            }
            if (code == CLEAR_CODE) {
                nextString = FIRST_STRING - 1;
                highestSoFar = FIRST_STRING;
                dictionaryFull = false;
            } else if (prefix == CLEAR_CODE) {
                into.write(code);
                nextString++;
                highestSoFar++;
            } else {
                int walking = code == nextString ? prefix : code;
                int depth = 0;
                do {
                    reversed[depth++] = dictionary[walking].terminator;
                    if (depth == reversed.length) {
                        throw new IllegalArgumentException(
                                "lzw data names a string longer than the dictionary");
                    }
                    walking = dictionary[walking].prefix;
                } while (walking != NULL_CODE);

                int terminator = reversed[depth - 1];
                for (int at = depth - 1; at >= 0; at--) {
                    into.write(reversed[at]);
                }
                if (code == nextString) {
                    into.write(terminator);
                }

                if (nextString >= FIRST_STRING && nextString < totalCodes) {
                    if (referenced[prefix]) {
                        dictionary[prefix].extraReferences++;
                    } else {
                        referenced[prefix] = true;
                    }
                    dictionary[nextString].prefix = prefix;
                    dictionary[nextString].terminator = terminator;
                    dictionary[nextString].extraReferences = 0;
                    referenced[nextString] = false;
                }

                if (!dictionaryFull) {
                    highestSoFar++;
                    if (++nextString > highestCode) {
                        dictionaryFull = true;
                        highestSoFar--;
                    }
                }
                if (dictionaryFull) {
                    nextString = nextUnreferencedAfter(referenced, nextString, highestCode);
                    int holder = dictionary[nextString].prefix;
                    if (dictionary[holder].extraReferences != 0) {
                        dictionary[holder].extraReferences--;
                    } else {
                        referenced[holder] = false;
                    }
                }
            }
            prefix = code;
            if (limit > 0 && into.used >= limit) {
                break;
            }
        }
        byte[] whole = into.toArray();
        return limit > 0 && whole.length > limit
                ? Arrays.copyOf(whole, limit)
                : whole;
    }

    private static int nextUnreferencedAfter(
            boolean[] referenced, int from, int highestCode) {

        int at = from + 1;
        while (true) {
            if (at > highestCode) {
                at = FIRST_STRING;
            }
            if (!referenced[at]) {
                return at;
            }
            at++;
        }
    }
}
