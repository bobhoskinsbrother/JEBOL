package org.jebol.domain.eval;

import java.util.Arrays;

/**
 * CRUSH, which is Rebol's own compressor rather than a standard one.
 *
 * <p>{@code u-crush.c}, ported from Ilya Muravyov's public-domain original.
 * LZ77 with the matches written out in a bit code of its own: a length in one
 * of six brackets and then a distance in one of sixteen slots, packed least
 * significant bit first. The first four bytes are the uncompressed length,
 * little endian, which is how DECOMPRESS knows how much to make before it
 * starts.
 *
 * <p>Rebol builds it with the constants Red uses rather than the ones the
 * original shipped with -- a smaller window and smaller hash tables, which
 * the comment beside them says give better results on Rebol-like data. The
 * two sets are not compatible, so this uses the same ones the C compiles
 * with and nothing else would read the bytes back.
 *
 * <p>The three levels differ in one number: how far down a hash chain the
 * compressor is willing to look for a longer match, and whether it looks
 * ahead one byte to see if waiting would pay. Level two is the only one that
 * looks ahead.
 */
final class Crush {

    private Crush() {
    }

    private static final int SLOT_BITS = 4;
    private static final int NUM_SLOTS = 16;
    private static final int A_BITS = 2;
    private static final int B_BITS = 2;
    private static final int C_BITS = 2;
    private static final int D_BITS = 3;
    private static final int E_BITS = 5;
    private static final int F_BITS = 9;
    private static final int A = 4;
    private static final int B = 8;
    private static final int C = 12;
    private static final int D = 20;
    private static final int E = 52;
    private static final int MIN_MATCH = 3;
    private static final int MAX_MATCH = 566;
    private static final int TOO_FAR = 65536;
    private static final int HASH1_LEN = 3;
    private static final int HASH2_LEN = 4;

    private static final int W_BITS = 18;
    private static final int W_SIZE = 1 << W_BITS;
    private static final int W_MASK = W_SIZE - 1;
    private static final int HASH1_SIZE = 1 << 19;
    private static final int HASH2_SIZE = 1 << 20;
    private static final int HASH1_MASK = HASH1_SIZE - 1;
    private static final int HASH2_MASK = HASH2_SIZE - 1;
    private static final int HASH1_SHIFT = 7;
    private static final int HASH2_SHIFT = 5;

    /** How far down a chain each level will look. */
    private static final int[] MAX_CHAIN = {4, 256, 1 << 12};

    private static final int HEADER_BYTES = 4;

    /**
     * Writes and reads the bit stream, least significant bit of each byte
     * first.
     *
     * <p>Both directions hold a buffer of bits that have not made a whole byte
     * yet, which is why the writer has to flush seven zero bits at the end:
     * without them the last partial byte never reaches the output.
     */
    private static final class Bits {

        private int buffer;
        private int count;
        private byte[] data;
        private int index;

        void put(int howMany, int value) {
            buffer |= value << count;
            count += howMany;
            while (count >= 8) {
                roomFor(1);
                data[index++] = (byte) buffer;
                buffer >>>= 8;
                count -= 8;
            }
        }

        int take(int howMany) {
            while (count < howMany) {
                buffer |= (index < data.length ? data[index] & 0xFF : 0) << count;
                index++;
                count += 8;
            }
            int taken = buffer & ((1 << howMany) - 1);
            buffer >>>= howMany;
            count -= howMany;
            return taken;
        }

        void roomFor(int wanted) {
            if (index + wanted <= data.length) {
                return;
            }
            data = Arrays.copyOf(data, Math.max(index + wanted, data.length * 2));
        }
    }

    private static int updatedHash1(int hash, int octet) {
        return ((hash << HASH1_SHIFT) + octet) & HASH1_MASK;
    }

    private static int updatedHash2(int hash, int octet) {
        return ((hash << HASH2_SHIFT) + octet) & HASH2_MASK;
    }

    /**
     * How much further away a match has to be worth being.
     *
     * <p>A match twice as far off has to be a byte longer to be preferred, and
     * so on by eights. It is what stops the compressor trading a short near
     * match for a slightly longer one on the far side of the window, whose
     * distance costs more bits than the extra length saves.
     */
    private static int penaltyFor(int distance, int against) {
        int penalty = 0;
        while (distance > against) {
            distance >>= 3;
            penalty++;
        }
        return penalty;
    }

    private static int octetAt(byte[] source, int at) {
        return at < source.length && at >= 0 ? source[at] & 0xFF : 0;
    }

    static byte[] compressed(byte[] source, int level) {
        int wanted = Math.max(0, Math.min(2, level));
        Bits bits = new Bits();
        bits.data = new byte[Math.max(16, source.length + HEADER_BYTES)];
        writeLittleEndian(bits.data, source.length);
        bits.index = HEADER_BYTES;

        int[] head = new int[HASH1_SIZE + HASH2_SIZE];
        Arrays.fill(head, -1);
        int[] previous = new int[W_SIZE];

        int hash1 = 0;
        int hash2 = 0;
        for (int at = 0; at < HASH1_LEN; at++) {
            hash1 = updatedHash1(hash1, octetAt(source, at));
        }
        for (int at = 0; at < HASH2_LEN; at++) {
            hash2 = updatedHash2(hash2, octetAt(source, at));
        }

        int at = 0;
        while (at < source.length) {
            int length = MIN_MATCH - 1;
            int offset = W_SIZE;
            int longestPossible = Math.min(MAX_MATCH, source.length - at);
            int oldest = at <= W_SIZE ? 0 : at - W_SIZE;

            if (head[hash1] >= oldest) {
                int start = head[hash1];
                if (octetAt(source, start) == octetAt(source, at)) {
                    int matched = 1;
                    while (matched < longestPossible
                            && octetAt(source, start + matched)
                                    == octetAt(source, at + matched)) {
                        matched++;
                    }
                    if (matched > length) {
                        length = matched;
                        offset = at - start;
                    }
                }
            }

            if (length < MAX_MATCH) {
                int chain = MAX_CHAIN[wanted];
                int start = head[hash2 + HASH1_SIZE];
                while (chain-- != 0 && start >= oldest) {
                    if (octetAt(source, start + length) == octetAt(source, at + length)
                            && octetAt(source, start) == octetAt(source, at)) {
                        int matched = 1;
                        while (matched < longestPossible
                                && octetAt(source, start + matched)
                                        == octetAt(source, at + matched)) {
                            matched++;
                        }
                        if (matched > length + penaltyFor((at - start) >> 4, offset)) {
                            length = matched;
                            offset = at - start;
                        }
                        if (matched == longestPossible) {
                            break;
                        }
                    }
                    start = previous[start & W_MASK];
                }
            }

            if (length == MIN_MATCH && offset > TOO_FAR) {
                length = 0;
            }

            if (wanted >= 2 && length >= MIN_MATCH && length < longestPossible) {
                int next = at + 1;
                int lazily = Math.min(length + 4, longestPossible);
                int chain = MAX_CHAIN[wanted];
                int start = head[updatedHash2(hash2,
                        octetAt(source, next + HASH2_LEN - 1)) + HASH1_SIZE];
                while (chain-- != 0 && start >= oldest) {
                    if (octetAt(source, start + length) == octetAt(source, next + length)
                            && octetAt(source, start) == octetAt(source, next)) {
                        int matched = 1;
                        while (matched < lazily
                                && octetAt(source, start + matched)
                                        == octetAt(source, next + matched)) {
                            matched++;
                        }
                        if (matched > length + penaltyFor(next - start, offset)) {
                            length = 0;
                            break;
                        }
                        if (matched == lazily) {
                            break;
                        }
                    }
                    start = previous[start & W_MASK];
                }
            }

            bits.roomFor(16);

            if (length >= MIN_MATCH) {
                bits.put(1, 1);
                writeTheLength(bits, length - MIN_MATCH);
                writeTheOffset(bits, offset - 1);
            } else {
                length = 1;
                bits.put(9, octetAt(source, at) << 1);
            }

            while (length-- != 0) {
                head[hash1] = at;
                previous[at & W_MASK] = head[hash2 + HASH1_SIZE];
                head[hash2 + HASH1_SIZE] = at;
                at++;
                hash1 = updatedHash1(hash1, octetAt(source, at + HASH1_LEN - 1));
                hash2 = updatedHash2(hash2, octetAt(source, at + HASH2_LEN - 1));
            }
        }
        bits.put(7, 0);
        return Arrays.copyOf(bits.data, bits.index);
    }

    /** Six brackets, each a run of zero bits then the offset into the bracket. */
    private static void writeTheLength(Bits bits, int length) {
        if (length < A) {
            bits.put(1, 1);
            bits.put(A_BITS, length);
        } else if (length < B) {
            bits.put(2, 1 << 1);
            bits.put(B_BITS, length - A);
        } else if (length < C) {
            bits.put(3, 1 << 2);
            bits.put(C_BITS, length - B);
        } else if (length < D) {
            bits.put(4, 1 << 3);
            bits.put(D_BITS, length - C);
        } else if (length < E) {
            bits.put(5, 1 << 4);
            bits.put(E_BITS, length - D);
        } else {
            bits.put(5, 0);
            bits.put(F_BITS, length - E);
        }
    }

    /** A slot number, then the distance within that slot's range. */
    private static void writeTheOffset(Bits bits, int offset) {
        int slot = W_BITS - NUM_SLOTS;
        while (offset >= (2 << slot)) {
            slot++;
        }
        bits.put(SLOT_BITS, slot - (W_BITS - NUM_SLOTS));
        if (slot > W_BITS - NUM_SLOTS) {
            bits.put(slot, offset - (1 << slot));
        } else {
            bits.put(W_BITS - (NUM_SLOTS - 1), offset);
        }
    }

    /**
     * Reads it back, given as many bytes as the header says or fewer.
     *
     * <p>{@code if (limit && size > limit) size = limit} -- DECOMPRESS/SIZE
     * asks for the first so many bytes and stops there, which is how a script
     * reads the front of something without the whole of it.
     */
    static byte[] decompressed(byte[] source, int limit) {
        if (source.length < HEADER_BYTES) {
            throw new IllegalArgumentException("crush data ends before its length");
        }
        int size = readLittleEndian(source);
        if (limit > 0 && size > limit) {
            size = limit;
        }
        Bits bits = new Bits();
        bits.data = source;
        bits.index = HEADER_BYTES;
        byte[] into = new byte[size];

        int at = 0;
        while (at < size) {
            if (bits.take(1) == 0) {
                into[at++] = (byte) bits.take(8);
                continue;
            }
            int length = readTheLength(bits);
            int slot = bits.take(SLOT_BITS) + (W_BITS - NUM_SLOTS);
            int start = ~(slot > W_BITS - NUM_SLOTS
                    ? bits.take(slot) + (1 << slot)
                    : bits.take(W_BITS - (NUM_SLOTS - 1))) + at;
            if (start < 0) {
                throw new IllegalArgumentException("crush data names a match before the start");
            }
            for (int copied = 0; copied < MIN_MATCH + length; copied++) {
                if (at >= size) {
                    return into;
                }
                into[at++] = into[start++];
            }
        }
        return into;
    }

    private static int readTheLength(Bits bits) {
        if (bits.take(1) != 0) {
            return bits.take(A_BITS);
        }
        if (bits.take(1) != 0) {
            return bits.take(B_BITS) + A;
        }
        if (bits.take(1) != 0) {
            return bits.take(C_BITS) + B;
        }
        if (bits.take(1) != 0) {
            return bits.take(D_BITS) + C;
        }
        if (bits.take(1) != 0) {
            return bits.take(E_BITS) + D;
        }
        return bits.take(F_BITS) + E;
    }

    private static void writeLittleEndian(byte[] into, int number) {
        into[0] = (byte) number;
        into[1] = (byte) (number >>> 8);
        into[2] = (byte) (number >>> 16);
        into[3] = (byte) (number >>> 24);
    }

    private static int readLittleEndian(byte[] from) {
        return (from[0] & 0xFF)
                | ((from[1] & 0xFF) << 8)
                | ((from[2] & 0xFF) << 16)
                | ((from[3] & 0xFF) << 24);
    }
}
