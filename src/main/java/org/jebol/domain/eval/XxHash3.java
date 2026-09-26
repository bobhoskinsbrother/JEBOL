package org.jebol.domain.eval;

final class XxHash3 {

    private XxHash3() {
    }

    private static final int P32_1 = 0x9E3779B1;
    private static final int P32_2 = 0x85EBCA77;
    private static final int P32_3 = 0xC2B2AE3D;

    private static final long P64_1 = 0x9E3779B185EBCA87L;
    private static final long P64_2 = 0xC2B2AE3D27D4EB4FL;
    private static final long P64_3 = 0x165667B19E3779F9L;
    private static final long P64_4 = 0x85EBCA77C2B2AE63L;
    private static final long P64_5 = 0x27D4EB2F165667C5L;

    private static final long MIXER_1 = 0x165667919E3779F9L;
    private static final long MIXER_2 = 0x9FB21C651E98DF25L;

    private static final int STRIPE = 64;
    private static final int LANES = STRIPE / 8;
    private static final int SECRET_CONSUMED_PER_STRIPE = 8;
    private static final int SECRET_FLOOR = 136;
    private static final int LAST_ACCUMULATION_STARTS = 7;
    private static final int MERGE_STARTS = 11;
    private static final int MIDSIZE_STARTS = 3;
    private static final int MIDSIZE_LAST_OFFSET = 17;
    private static final int LONGEST_MIDSIZE = 240;

    private static final byte[] SECRET = secret();

    private static byte[] secret() {
        int[] published = {
            0xb8, 0xfe, 0x6c, 0x39, 0x23, 0xa4, 0x4b, 0xbe, 0x7c, 0x01, 0x81, 0x2c,
            0xf7, 0x21, 0xad, 0x1c, 0xde, 0xd4, 0x6d, 0xe9, 0x83, 0x90, 0x97, 0xdb,
            0x72, 0x40, 0xa4, 0xa4, 0xb7, 0xb3, 0x67, 0x1f, 0xcb, 0x79, 0xe6, 0x4e,
            0xcc, 0xc0, 0xe5, 0x78, 0x82, 0x5a, 0xd0, 0x7d, 0xcc, 0xff, 0x72, 0x21,
            0xb8, 0x08, 0x46, 0x74, 0xf7, 0x43, 0x24, 0x8e, 0xe0, 0x35, 0x90, 0xe6,
            0x81, 0x3a, 0x26, 0x4c, 0x3c, 0x28, 0x52, 0xbb, 0x91, 0xc3, 0x00, 0xcb,
            0x88, 0xd0, 0x65, 0x8b, 0x1b, 0x53, 0x2e, 0xa3, 0x71, 0x64, 0x48, 0x97,
            0xa2, 0x0d, 0xf9, 0x4e, 0x38, 0x19, 0xef, 0x46, 0xa9, 0xde, 0xac, 0xd8,
            0xa8, 0xfa, 0x76, 0x3f, 0xe3, 0x9c, 0x34, 0x3f, 0xf9, 0xdc, 0xbb, 0xc7,
            0xc7, 0x0b, 0x4f, 0x1d, 0x8a, 0x51, 0xe0, 0x4b, 0xcd, 0xb4, 0x59, 0x31,
            0xc8, 0x9f, 0x7e, 0xc9, 0xd9, 0x78, 0x73, 0x64, 0xea, 0xc5, 0xac, 0x83,
            0x34, 0xd3, 0xeb, 0xc3, 0xc5, 0x81, 0xa0, 0xff, 0xfa, 0x13, 0x63, 0xeb,
            0x17, 0x0d, 0xdd, 0x51, 0xb7, 0xf0, 0xda, 0x49, 0xd3, 0x16, 0x55, 0x26,
            0x29, 0xd4, 0x68, 0x9e, 0x2b, 0x16, 0xbe, 0x58, 0x7d, 0x47, 0xa1, 0xfc,
            0x8f, 0xf8, 0xb8, 0xd1, 0x7a, 0xd0, 0x31, 0xce, 0x45, 0xcb, 0x3a, 0x8f,
            0x95, 0x16, 0x04, 0x28, 0xaf, 0xd7, 0xfb, 0xca, 0xbb, 0x4b, 0x40, 0x7e,
        };
        byte[] bytes = new byte[published.length];
        for (int at = 0; at < published.length; at++) {
            bytes[at] = (byte) published[at];
        }
        return bytes;
    }

    static byte[] of64MostSignificantByteFirst(byte[] message) {
        return asBigEndian(hash64(message));
    }

    static byte[] of128MostSignificantByteFirst(byte[] message) {
        long[] halves = hash128(message);
        byte[] written = new byte[16];
        System.arraycopy(asBigEndian(halves[1]), 0, written, 0, 8);
        System.arraycopy(asBigEndian(halves[0]), 0, written, 8, 8);
        return written;
    }

    private static long hash64(byte[] message) {
        int length = message.length;
        if (length <= 16) {
            return ofAtMostSixteen(message);
        }
        if (length <= 128) {
            return ofSeventeenToAHundredAndTwentyEight(message);
        }
        if (length <= LONGEST_MIDSIZE) {
            return ofAHundredAndTwentyNineToTwoHundredAndForty(message);
        }
        return finalisedAsSixtyFour(accumulatorsOver(message), message.length);
    }

    private static long ofAtMostSixteen(byte[] message) {
        int length = message.length;
        if (length > 8) {
            long flipLow = longAt(SECRET, 24) ^ longAt(SECRET, 32);
            long flipHigh = longAt(SECRET, 40) ^ longAt(SECRET, 48);
            long low = longAt(message, 0) ^ flipLow;
            long high = longAt(message, length - 8) ^ flipHigh;
            return avalanched(length + Long.reverseBytes(low) + high
                    + foldedProductOf(low, high));
        }
        if (length >= 4) {
            long first = wordAt(message, 0) & 0xFFFFFFFFL;
            long second = wordAt(message, length - 4) & 0xFFFFFFFFL;
            long flip = longAt(SECRET, 8) ^ longAt(SECRET, 16);
            return rrmxmxed((second + (first << 32)) ^ flip, length);
        }
        if (length > 0) {
            return avalanched64(shortCombination(message) ^ shortFlip());
        }
        return avalanched64(longAt(SECRET, 56) ^ longAt(SECRET, 64));
    }

    private static long shortCombination(byte[] message) {
        int length = message.length;
        int first = message[0] & 0xFF;
        int middle = message[length >> 1] & 0xFF;
        int last = message[length - 1] & 0xFF;
        return (first << 16 | (long) middle << 24 | last | (long) length << 8) & 0xFFFFFFFFL;
    }

    private static long shortFlip() {
        return (wordAt(SECRET, 0) ^ wordAt(SECRET, 4)) & 0xFFFFFFFFL;
    }

    private static long mixedSixteen(byte[] message, int at, int secretAt) {
        return foldedProductOf(
                longAt(message, at) ^ longAt(SECRET, secretAt),
                longAt(message, at + 8) ^ longAt(SECRET, secretAt + 8));
    }

    private static long ofSeventeenToAHundredAndTwentyEight(byte[] message) {
        int length = message.length;
        long running = length * P64_1;
        if (length > 32) {
            if (length > 64) {
                if (length > 96) {
                    running += mixedSixteen(message, 48, 96);
                    running += mixedSixteen(message, length - 64, 112);
                }
                running += mixedSixteen(message, 32, 64);
                running += mixedSixteen(message, length - 48, 80);
            }
            running += mixedSixteen(message, 16, 32);
            running += mixedSixteen(message, length - 32, 48);
        }
        running += mixedSixteen(message, 0, 0);
        running += mixedSixteen(message, length - 16, 16);
        return avalanched(running);
    }

    private static long ofAHundredAndTwentyNineToTwoHundredAndForty(byte[] message) {
        int length = message.length;
        int rounds = length / 16;
        long running = length * P64_1;
        for (int each = 0; each < 8; each++) {
            running += mixedSixteen(message, 16 * each, 16 * each);
        }
        long tail = mixedSixteen(message, length - 16,
                SECRET_FLOOR - MIDSIZE_LAST_OFFSET);
        running = avalanched(running);
        for (int each = 8; each < rounds; each++) {
            tail += mixedSixteen(message, 16 * each,
                    16 * (each - 8) + MIDSIZE_STARTS);
        }
        return avalanched(running + tail);
    }

    private static long[] accumulatorsOver(byte[] message) {
        long[] accumulators = {
            P32_3 & 0xFFFFFFFFL, P64_1, P64_2, P64_3,
            P64_4, P32_2 & 0xFFFFFFFFL, P64_5, P32_1 & 0xFFFFFFFFL,
        };
        int stripesPerBlock = (SECRET.length - STRIPE) / SECRET_CONSUMED_PER_STRIPE;
        int blockLength = STRIPE * stripesPerBlock;
        int blocks = (message.length - 1) / blockLength;
        for (int block = 0; block < blocks; block++) {
            accumulate(accumulators, message, block * blockLength, stripesPerBlock);
            scramble(accumulators, SECRET.length - STRIPE);
        }
        int stripesLeft = ((message.length - 1) - blockLength * blocks) / STRIPE;
        accumulate(accumulators, message, blocks * blockLength, stripesLeft);
        accumulateOneStripe(accumulators, message, message.length - STRIPE,
                SECRET.length - STRIPE - LAST_ACCUMULATION_STARTS);
        return accumulators;
    }

    private static void accumulate(
            long[] accumulators, byte[] message, int from, int stripes) {

        for (int stripe = 0; stripe < stripes; stripe++) {
            accumulateOneStripe(accumulators, message, from + stripe * STRIPE,
                    stripe * SECRET_CONSUMED_PER_STRIPE);
        }
    }

    private static void accumulateOneStripe(
            long[] accumulators, byte[] message, int from, int secretAt) {

        for (int lane = 0; lane < LANES; lane++) {
            long data = longAt(message, from + lane * 8);
            long keyed = data ^ longAt(SECRET, secretAt + lane * 8);
            accumulators[lane ^ 1] += data;
            accumulators[lane] += (keyed & 0xFFFFFFFFL) * (keyed >>> 32 & 0xFFFFFFFFL);
        }
    }

    private static void scramble(long[] accumulators, int secretAt) {
        for (int lane = 0; lane < LANES; lane++) {
            long scrambled = accumulators[lane] ^ accumulators[lane] >>> 47;
            scrambled ^= longAt(SECRET, secretAt + lane * 8);
            accumulators[lane] = scrambled * (P32_1 & 0xFFFFFFFFL);
        }
    }

    private static long merged(long[] accumulators, int secretAt, long start) {
        long running = start;
        for (int pair = 0; pair < 4; pair++) {
            running += foldedProductOf(
                    accumulators[2 * pair] ^ longAt(SECRET, secretAt + 16 * pair),
                    accumulators[2 * pair + 1] ^ longAt(SECRET, secretAt + 16 * pair + 8));
        }
        return avalanched(running);
    }

    private static long finalisedAsSixtyFour(long[] accumulators, long length) {
        return merged(accumulators, MERGE_STARTS, length * P64_1);
    }

    private static long[] hash128(byte[] message) {
        int length = message.length;
        if (length <= 16) {
            return ofAtMostSixteenAsTwoHalves(message);
        }
        if (length <= 128) {
            return ofSeventeenToAHundredAndTwentyEightAsTwoHalves(message);
        }
        if (length <= LONGEST_MIDSIZE) {
            return ofAHundredAndTwentyNineToTwoHundredAndFortyAsTwoHalves(message);
        }
        long[] accumulators = accumulatorsOver(message);
        return new long[] {
            finalisedAsSixtyFour(accumulators, length),
            merged(accumulators, SECRET.length - STRIPE - MERGE_STARTS,
                    ~(length * P64_2)),
        };
    }

    private static long[] ofAtMostSixteenAsTwoHalves(byte[] message) {
        int length = message.length;
        if (length > 8) {
            return ofNineToSixteenAsTwoHalves(message);
        }
        if (length >= 4) {
            return ofFourToEightAsTwoHalves(message);
        }
        if (length > 0) {
            long combinedLow = shortCombination(message);
            long combinedHigh = Integer.rotateLeft(
                    Integer.reverseBytes((int) combinedLow), 13) & 0xFFFFFFFFL;
            long flipLow = (wordAt(SECRET, 0) ^ wordAt(SECRET, 4)) & 0xFFFFFFFFL;
            long flipHigh = (wordAt(SECRET, 8) ^ wordAt(SECRET, 12)) & 0xFFFFFFFFL;
            return new long[] {
                avalanched64(combinedLow ^ flipLow),
                avalanched64(combinedHigh ^ flipHigh),
            };
        }
        return new long[] {
            avalanched64(longAt(SECRET, 64) ^ longAt(SECRET, 72)),
            avalanched64(longAt(SECRET, 80) ^ longAt(SECRET, 88)),
        };
    }

    private static long[] ofFourToEightAsTwoHalves(byte[] message) {
        int length = message.length;
        long low = wordAt(message, 0) & 0xFFFFFFFFL;
        long high = wordAt(message, length - 4) & 0xFFFFFFFFL;
        long flip = longAt(SECRET, 16) ^ longAt(SECRET, 24);
        long keyed = (low + (high << 32)) ^ flip;
        long productLow = keyed * (P64_1 + ((long) length << 2));
        long productHigh = Math.unsignedMultiplyHigh(keyed, P64_1 + ((long) length << 2));

        productHigh += productLow << 1;
        productLow ^= productHigh >>> 3;
        productLow ^= productLow >>> 35;
        productLow *= MIXER_2;
        productLow ^= productLow >>> 28;
        return new long[] {productLow, avalanched(productHigh)};
    }

    private static long[] ofNineToSixteenAsTwoHalves(byte[] message) {
        int length = message.length;
        long flipLow = longAt(SECRET, 32) ^ longAt(SECRET, 40);
        long flipHigh = longAt(SECRET, 48) ^ longAt(SECRET, 56);
        long low = longAt(message, 0);
        long high = longAt(message, length - 8);
        long mixed = low ^ high ^ flipLow;
        long productLow = mixed * P64_1;
        long productHigh = Math.unsignedMultiplyHigh(mixed, P64_1);

        productLow += (long) (length - 1) << 54;
        high ^= flipHigh;
        productHigh += high + (high & 0xFFFFFFFFL) * ((P32_2 & 0xFFFFFFFFL) - 1);
        productLow ^= Long.reverseBytes(productHigh);

        long lowOfProduct = productLow * P64_2;
        long highOfProduct = Math.unsignedMultiplyHigh(productLow, P64_2)
                + productHigh * P64_2;
        return new long[] {avalanched(lowOfProduct), avalanched(highOfProduct)};
    }

    private static long[] mixedThirtyTwo(
            long[] accumulators, byte[] message, int first, int second, int secretAt) {

        accumulators[0] += mixedSixteen(message, first, secretAt);
        accumulators[0] ^= longAt(message, second) + longAt(message, second + 8);
        accumulators[1] += mixedSixteen(message, second, secretAt + 16);
        accumulators[1] ^= longAt(message, first) + longAt(message, first + 8);
        return accumulators;
    }

    private static long[] bothHalvesFrom(long[] accumulators, int length) {
        long low = avalanched(accumulators[0] + accumulators[1]);
        long high = -avalanched(accumulators[0] * P64_1
                + accumulators[1] * P64_4
                + (long) length * P64_2);
        return new long[] {low, high};
    }

    private static long[] ofSeventeenToAHundredAndTwentyEightAsTwoHalves(byte[] message) {
        int length = message.length;
        long[] accumulators = {length * P64_1, 0};
        if (length > 32) {
            if (length > 64) {
                if (length > 96) {
                    mixedThirtyTwo(accumulators, message, 48, length - 64, 96);
                }
                mixedThirtyTwo(accumulators, message, 32, length - 48, 64);
            }
            mixedThirtyTwo(accumulators, message, 16, length - 32, 32);
        }
        mixedThirtyTwo(accumulators, message, 0, length - 16, 0);
        return bothHalvesFrom(accumulators, length);
    }

    private static long[] ofAHundredAndTwentyNineToTwoHundredAndFortyAsTwoHalves(
            byte[] message) {

        int length = message.length;
        long[] accumulators = {length * P64_1, 0};
        for (int at = 32; at < 160; at += 32) {
            mixedThirtyTwo(accumulators, message, at - 32, at - 16, at - 32);
        }
        accumulators[0] = avalanched(accumulators[0]);
        accumulators[1] = avalanched(accumulators[1]);
        for (int at = 160; at <= length; at += 32) {
            mixedThirtyTwo(accumulators, message, at - 32, at - 16,
                    MIDSIZE_STARTS + at - 160);
        }
        mixedThirtyTwo(accumulators, message, length - 16, length - 32,
                SECRET_FLOOR - MIDSIZE_LAST_OFFSET - 16);
        return bothHalvesFrom(accumulators, length);
    }

    private static long foldedProductOf(long left, long right) {
        return left * right ^ Math.unsignedMultiplyHigh(left, right);
    }

    private static long avalanched(long hash) {
        long mixed = hash ^ hash >>> 37;
        mixed *= MIXER_1;
        return mixed ^ mixed >>> 32;
    }

    private static long rrmxmxed(long hash, long length) {
        long mixed = hash ^ Long.rotateLeft(hash, 49) ^ Long.rotateLeft(hash, 24);
        mixed *= MIXER_2;
        mixed ^= (mixed >>> 35) + length;
        mixed *= MIXER_2;
        return mixed ^ mixed >>> 28;
    }

    private static long avalanched64(long hash) {
        long mixed = hash ^ hash >>> 33;
        mixed *= P64_2;
        mixed ^= mixed >>> 29;
        mixed *= P64_3;
        return mixed ^ mixed >>> 32;
    }

    private static int wordAt(byte[] bytes, int at) {
        return bytes[at] & 0xFF
                | (bytes[at + 1] & 0xFF) << 8
                | (bytes[at + 2] & 0xFF) << 16
                | (bytes[at + 3] & 0xFF) << 24;
    }

    private static long longAt(byte[] bytes, int at) {
        return wordAt(bytes, at) & 0xFFFFFFFFL
                | (long) wordAt(bytes, at + 4) << 32;
    }

    private static byte[] asBigEndian(long value) {
        byte[] written = new byte[8];
        for (int at = 0; at < 8; at++) {
            written[at] = (byte) (value >>> (7 - at) * 8);
        }
        return written;
    }
}
