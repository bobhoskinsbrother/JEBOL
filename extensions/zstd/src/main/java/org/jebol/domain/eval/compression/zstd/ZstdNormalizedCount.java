package org.jebol.domain.eval.compression.zstd;

import java.util.Arrays;

final class ZstdNormalizedCount {

    private static final int MIN_TABLE_LOG = 5;
    private static final int ABSOLUTE_MAX_TABLE_LOG = 15;
    private static final int SMALLEST_HEADER_READ = 8;

    final short[] counts;
    int maxSymbol;
    int tableLog;

    ZstdNormalizedCount(int capacity) {
        counts = new short[capacity];
    }

    int read(int maxSymbolValue, byte[] source, int from, int length) {
        if (length < SMALLEST_HEADER_READ) {
            byte[] padded = new byte[SMALLEST_HEADER_READ];
            System.arraycopy(source, from, padded, 0, length);
            int countSize = read(maxSymbolValue, padded, 0, SMALLEST_HEADER_READ);
            if (countSize > length) {
                throw ZstdError.CORRUPTION_DETECTED.failure();
            }
            return countSize;
        }
        int end = from + length;
        int at = from;
        int charnum = 0;
        int maxSymbolsPlusOne = maxSymbolValue + 1;
        boolean isAfterZero = false;
        Arrays.fill(counts, 0, maxSymbolsPlusOne, (short) 0);
        int bitStream = ZstdOctets.readInt(source, at);
        int nbBits = (bitStream & 0xF) + MIN_TABLE_LOG;
        if (nbBits > ABSOLUTE_MAX_TABLE_LOG) {
            throw ZstdError.TABLE_LOG_TOO_LARGE.failure();
        }
        bitStream >>>= 4;
        int bitCount = 4;
        tableLog = nbBits;
        int remaining = (1 << nbBits) + 1;
        int threshold = 1 << nbBits;
        nbBits++;
        for (;;) {
            if (isAfterZero) {
                int repeats = Integer.numberOfTrailingZeros(~bitStream | 0x80000000) >> 1;
                while (repeats >= 12) {
                    charnum += 3 * 12;
                    if (at <= end - 7) {
                        at += 3;
                    } else {
                        bitCount -= 8 * (end - 7 - at);
                        bitCount &= 31;
                        at = end - 4;
                    }
                    bitStream = ZstdOctets.readInt(source, at) >>> bitCount;
                    repeats = Integer.numberOfTrailingZeros(~bitStream | 0x80000000) >> 1;
                }
                charnum += 3 * repeats;
                bitStream >>>= 2 * repeats;
                bitCount += 2 * repeats;
                charnum += bitStream & 3;
                bitCount += 2;
                if (charnum >= maxSymbolsPlusOne) {
                    break;
                }
                if (at <= end - 7 || at + (bitCount >> 3) <= end - 4) {
                    at += bitCount >> 3;
                    bitCount &= 7;
                } else {
                    bitCount -= 8 * (end - 4 - at);
                    bitCount &= 31;
                    at = end - 4;
                }
                bitStream = ZstdOctets.readInt(source, at) >>> bitCount;
            }
            int max = (2 * threshold - 1) - remaining;
            int count;
            if (Integer.compareUnsigned(bitStream & (threshold - 1), max) < 0) {
                count = bitStream & (threshold - 1);
                bitCount += nbBits - 1;
            } else {
                count = bitStream & (2 * threshold - 1);
                if (count >= threshold) {
                    count -= max;
                }
                bitCount += nbBits;
            }
            count--;
            if (count >= 0) {
                remaining -= count;
            } else {
                remaining += count;
            }
            counts[charnum++] = (short) count;
            isAfterZero = count == 0;
            if (remaining < threshold) {
                if (remaining <= 1) {
                    break;
                }
                nbBits = ZstdOctets.highBit(remaining) + 1;
                threshold = 1 << (nbBits - 1);
            }
            if (charnum >= maxSymbolsPlusOne) {
                break;
            }
            if (at <= end - 7 || at + (bitCount >> 3) <= end - 4) {
                at += bitCount >> 3;
                bitCount &= 7;
            } else {
                bitCount -= 8 * (end - 4 - at);
                bitCount &= 31;
                at = end - 4;
            }
            bitStream = ZstdOctets.readInt(source, at) >>> bitCount;
        }
        if (remaining != 1) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        if (charnum > maxSymbolsPlusOne) {
            throw ZstdError.MAX_SYMBOL_VALUE_TOO_SMALL.failure();
        }
        if (bitCount > 32) {
            throw ZstdError.CORRUPTION_DETECTED.failure();
        }
        maxSymbol = charnum - 1;
        at += (bitCount + 7) >> 3;
        return at - from;
    }
}
