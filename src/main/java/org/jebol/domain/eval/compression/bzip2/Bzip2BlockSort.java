package org.jebol.domain.eval.compression.bzip2;

import java.util.Arrays;

/** See docs/bzip2-port.md, and do not tidy this. */
final class Bzip2BlockSort {

    static final int OVERSHOOT = 34;
    private static final int FREQUENCY_TABLE_SIZE = 65537;
    private static final int SMALLEST_FOR_MAIN_SORT = 10000;
    private static final int WORK_PER_OCTET = 9;
    private static final int RADIX_DEPTH = 2;
    private static final int DEPTH_LIMIT = 14;
    private static final int SMALL_RANGE = 20;
    private static final int STACK_SIZE = 100;
    private static final int SET_MASK = 1 << 21;
    private static final int CLEAR_MASK = ~SET_MASK;
    private static final int LAST_OCTET = 255;
    private static final int FALLBACK_SMALL_RANGE = 10;
    private static final int[] INCREMENTS = {
            1, 4, 13, 40, 121, 364, 1093, 3280, 9841, 29524, 88573, 265720, 797161, 2391484};

    private final int[] pointers;
    private final byte[] block;
    private final int[] frequencies = new int[FREQUENCY_TABLE_SIZE];
    private char[] quadrant = new char[0];
    private int[] classes = new int[0];
    private int blockLength;
    private int budget;

    Bzip2BlockSort(int[] pointers, byte[] block) {
        this.pointers = pointers;
        this.block = block;
    }

    int sort(int length) {
        blockLength = length;
        if (length < SMALLEST_FOR_MAIN_SORT) {
            sortByFallback();
        } else {
            if (quadrant.length < length + OVERSHOOT) {
                quadrant = new char[length + OVERSHOOT];
            }
            budget = length * WORK_PER_OCTET;
            sortByMainAlgorithm();
            if (budget < 0) {
                sortByFallback();
            }
        }
        for (int i = 0; i < length; i++) {
            if (pointers[i] == 0) {
                return i;
            }
        }
        throw new IllegalStateException("bzip2's block sort lost the block's start");
    }

    private void sortSmallFallbackRange(int low, int high) {
        if (low == high) {
            return;
        }
        if (high - low > 3) {
            for (int i = high - 4; i >= low; i--) {
                int moving = pointers[i];
                int movingClass = classes[moving];
                int j = i + 4;
                while (j <= high && Integer.compareUnsigned(movingClass, classes[pointers[j]]) > 0) {
                    pointers[j - 4] = pointers[j];
                    j += 4;
                }
                pointers[j - 4] = moving;
            }
        }
        for (int i = high - 1; i >= low; i--) {
            int moving = pointers[i];
            int movingClass = classes[moving];
            int j = i + 1;
            while (j <= high && Integer.compareUnsigned(movingClass, classes[pointers[j]]) > 0) {
                pointers[j - 1] = pointers[j];
                j++;
            }
            pointers[j - 1] = moving;
        }
    }

    private void swapPointers(int first, int second) {
        int held = pointers[first];
        pointers[first] = pointers[second];
        pointers[second] = held;
    }

    private void swapPointerRuns(int first, int second, int count) {
        for (int i = 0; i < count; i++) {
            swapPointers(first + i, second + i);
        }
    }

    private void quicksortFallbackRange(int startLow, int startHigh) {
        int[] stackLow = new int[STACK_SIZE];
        int[] stackHigh = new int[STACK_SIZE];
        int random = 0;
        int depth = 0;
        stackLow[depth] = startLow;
        stackHigh[depth] = startHigh;
        depth++;
        while (depth > 0) {
            depth--;
            int low = stackLow[depth];
            int high = stackHigh[depth];
            if (high - low < FALLBACK_SMALL_RANGE) {
                sortSmallFallbackRange(low, high);
                continue;
            }
            random = (random * 7621 + 1) % 32768;
            int pick = random % 3;
            int median = pick == 0 ? classes[pointers[low]]
                    : pick == 1 ? classes[pointers[(low + high) >> 1]]
                    : classes[pointers[high]];
            int unLow = low;
            int lessEnd = low;
            int unHigh = high;
            int greaterStart = high;
            for (;;) {
                while (unLow <= unHigh) {
                    int difference = classes[pointers[unLow]] - median;
                    if (difference == 0) {
                        swapPointers(unLow, lessEnd);
                        lessEnd++;
                        unLow++;
                        continue;
                    }
                    if (difference > 0) {
                        break;
                    }
                    unLow++;
                }
                while (unLow <= unHigh) {
                    int difference = classes[pointers[unHigh]] - median;
                    if (difference == 0) {
                        swapPointers(unHigh, greaterStart);
                        greaterStart--;
                        unHigh--;
                        continue;
                    }
                    if (difference < 0) {
                        break;
                    }
                    unHigh--;
                }
                if (unLow > unHigh) {
                    break;
                }
                swapPointers(unLow, unHigh);
                unLow++;
                unHigh--;
            }
            if (greaterStart < lessEnd) {
                continue;
            }
            int lows = Math.min(lessEnd - low, unLow - lessEnd);
            swapPointerRuns(low, unLow - lows, lows);
            int highs = Math.min(high - greaterStart, greaterStart - unHigh);
            swapPointerRuns(unLow, high - highs + 1, highs);
            int lowEnd = low + unLow - lessEnd - 1;
            int highStart = high - (greaterStart - unHigh) + 1;
            if (lowEnd - low > high - highStart) {
                stackLow[depth] = low;
                stackHigh[depth] = lowEnd;
                depth++;
                stackLow[depth] = highStart;
                stackHigh[depth] = high;
                depth++;
            } else {
                stackLow[depth] = highStart;
                stackHigh[depth] = high;
                depth++;
                stackLow[depth] = low;
                stackHigh[depth] = lowEnd;
                depth++;
            }
        }
    }

    private void setHeaderBit(int at) {
        frequencies[at >> 5] |= 1 << (at & 31);
    }

    private void clearHeaderBit(int at) {
        frequencies[at >> 5] &= ~(1 << (at & 31));
    }

    private boolean isHeaderBit(int at) {
        return (frequencies[at >> 5] & (1 << (at & 31))) != 0;
    }

    private void sortByFallback() {
        int length = blockLength;
        if (classes.length < length) {
            classes = new int[length];
        }
        int[] buckets = new int[257];
        for (int i = 0; i < length; i++) {
            buckets[block[i] & 0xFF]++;
        }
        for (int i = 1; i < 257; i++) {
            buckets[i] += buckets[i - 1];
        }
        for (int i = 0; i < length; i++) {
            int octet = block[i] & 0xFF;
            int at = buckets[octet] - 1;
            buckets[octet] = at;
            pointers[at] = i;
        }
        int bitWords = 2 + length / 32;
        for (int i = 0; i < bitWords; i++) {
            frequencies[i] = 0;
        }
        for (int i = 0; i < 256; i++) {
            setHeaderBit(buckets[i]);
        }
        for (int i = 0; i < 32; i++) {
            setHeaderBit(length + 2 * i);
            clearHeaderBit(length + 2 * i + 1);
        }
        int stride = 1;
        for (;;) {
            int bucketStart = 0;
            for (int i = 0; i < length; i++) {
                if (isHeaderBit(i)) {
                    bucketStart = i;
                }
                int at = pointers[i] - stride;
                if (at < 0) {
                    at += length;
                }
                classes[at] = bucketStart;
            }
            int unresolved = refineFallbackBuckets();
            stride *= 2;
            if (stride > length || unresolved == 0) {
                return;
            }
        }
    }

    private int refineFallbackBuckets() {
        int length = blockLength;
        int unresolved = 0;
        int right = -1;
        for (;;) {
            int at = right + 1;
            while (isHeaderBit(at) && (at & 31) != 0) {
                at++;
            }
            if (isHeaderBit(at)) {
                while (frequencies[at >> 5] == -1) {
                    at += 32;
                }
                while (isHeaderBit(at)) {
                    at++;
                }
            }
            int left = at - 1;
            if (left >= length) {
                return unresolved;
            }
            while (!isHeaderBit(at) && (at & 31) != 0) {
                at++;
            }
            if (!isHeaderBit(at)) {
                while (frequencies[at >> 5] == 0) {
                    at += 32;
                }
                while (!isHeaderBit(at)) {
                    at++;
                }
            }
            right = at - 1;
            if (right >= length) {
                return unresolved;
            }
            if (right > left) {
                unresolved += right - left + 1;
                quicksortFallbackRange(left, right);
                int previous = -1;
                for (int i = left; i <= right; i++) {
                    int current = classes[pointers[i]];
                    if (previous != current) {
                        setHeaderBit(i);
                        previous = current;
                    }
                }
            }
        }
    }

    private boolean isRotationGreater(int startFirst, int startSecond) {
        int first = startFirst;
        int second = startSecond;
        for (int step = 0; step < 12; step++) {
            int octetFirst = block[first] & 0xFF;
            int octetSecond = block[second] & 0xFF;
            if (octetFirst != octetSecond) {
                return octetFirst > octetSecond;
            }
            first++;
            second++;
        }
        int remaining = blockLength + 8;
        do {
            for (int step = 0; step < 8; step++) {
                int octetFirst = block[first] & 0xFF;
                int octetSecond = block[second] & 0xFF;
                if (octetFirst != octetSecond) {
                    return octetFirst > octetSecond;
                }
                char quadrantFirst = quadrant[first];
                char quadrantSecond = quadrant[second];
                if (quadrantFirst != quadrantSecond) {
                    return quadrantFirst > quadrantSecond;
                }
                first++;
                second++;
            }
            if (first >= blockLength) {
                first -= blockLength;
            }
            if (second >= blockLength) {
                second -= blockLength;
            }
            remaining -= 8;
            budget--;
        } while (remaining >= 0);
        return false;
    }

    private void insertAt(int at, int stride, int low, int depth) {
        int moving = pointers[at];
        int j = at;
        while (isRotationGreater(pointers[j - stride] + depth, moving + depth)) {
            pointers[j] = pointers[j - stride];
            j -= stride;
            if (j <= low + stride - 1) {
                break;
            }
        }
        pointers[j] = moving;
    }

    private void sortSmallMainRange(int low, int high, int depth) {
        int count = high - low + 1;
        if (count < 2) {
            return;
        }
        int increment = 0;
        while (INCREMENTS[increment] < count) {
            increment++;
        }
        increment--;
        for (; increment >= 0; increment--) {
            int stride = INCREMENTS[increment];
            int at = low + stride;
            for (;;) {
                if (at > high) {
                    break;
                }
                insertAt(at, stride, low, depth);
                at++;
                if (at > high) {
                    break;
                }
                insertAt(at, stride, low, depth);
                at++;
                if (at > high) {
                    break;
                }
                insertAt(at, stride, low, depth);
                at++;
                if (budget < 0) {
                    return;
                }
            }
        }
    }

    private static int findMedian(int first, int second, int third) {
        int a = first;
        int b = second;
        if (a > b) {
            int held = a;
            a = b;
            b = held;
        }
        if (b > third) {
            b = third;
            if (a > b) {
                b = a;
            }
        }
        return b;
    }

    private void quicksortMainRange(int startLow, int startHigh, int startDepth) {
        int[] stackLow = new int[STACK_SIZE];
        int[] stackHigh = new int[STACK_SIZE];
        int[] stackDepth = new int[STACK_SIZE];
        int[] nextLow = new int[3];
        int[] nextHigh = new int[3];
        int[] nextDepth = new int[3];
        int top = 0;
        stackLow[top] = startLow;
        stackHigh[top] = startHigh;
        stackDepth[top] = startDepth;
        top++;
        while (top > 0) {
            top--;
            int low = stackLow[top];
            int high = stackHigh[top];
            int depth = stackDepth[top];
            if (high - low < SMALL_RANGE || depth > DEPTH_LIMIT) {
                sortSmallMainRange(low, high, depth);
                if (budget < 0) {
                    return;
                }
                continue;
            }
            int median = findMedian(block[pointers[low] + depth] & 0xFF, block[pointers[high] + depth] & 0xFF,
                    block[pointers[(low + high) >> 1] + depth] & 0xFF);
            int unLow = low;
            int lessEnd = low;
            int unHigh = high;
            int greaterStart = high;
            for (;;) {
                while (unLow <= unHigh) {
                    int difference = (block[pointers[unLow] + depth] & 0xFF) - median;
                    if (difference == 0) {
                        swapPointers(unLow, lessEnd);
                        lessEnd++;
                        unLow++;
                        continue;
                    }
                    if (difference > 0) {
                        break;
                    }
                    unLow++;
                }
                while (unLow <= unHigh) {
                    int difference = (block[pointers[unHigh] + depth] & 0xFF) - median;
                    if (difference == 0) {
                        swapPointers(unHigh, greaterStart);
                        greaterStart--;
                        unHigh--;
                        continue;
                    }
                    if (difference < 0) {
                        break;
                    }
                    unHigh--;
                }
                if (unLow > unHigh) {
                    break;
                }
                swapPointers(unLow, unHigh);
                unLow++;
                unHigh--;
            }
            if (greaterStart < lessEnd) {
                stackLow[top] = low;
                stackHigh[top] = high;
                stackDepth[top] = depth + 1;
                top++;
                continue;
            }
            int lows = Math.min(lessEnd - low, unLow - lessEnd);
            swapPointerRuns(low, unLow - lows, lows);
            int highs = Math.min(high - greaterStart, greaterStart - unHigh);
            swapPointerRuns(unLow, high - highs + 1, highs);
            int lowEnd = low + unLow - lessEnd - 1;
            int highStart = high - (greaterStart - unHigh) + 1;
            nextLow[0] = low;
            nextHigh[0] = lowEnd;
            nextDepth[0] = depth;
            nextLow[1] = highStart;
            nextHigh[1] = high;
            nextDepth[1] = depth;
            nextLow[2] = lowEnd + 1;
            nextHigh[2] = highStart - 1;
            nextDepth[2] = depth + 1;
            swapIfSmaller(nextLow, nextHigh, nextDepth, 0, 1);
            swapIfSmaller(nextLow, nextHigh, nextDepth, 1, 2);
            swapIfSmaller(nextLow, nextHigh, nextDepth, 0, 1);
            for (int i = 0; i < 3; i++) {
                stackLow[top] = nextLow[i];
                stackHigh[top] = nextHigh[i];
                stackDepth[top] = nextDepth[i];
                top++;
            }
        }
    }

    private static void swapIfSmaller(int[] low, int[] high, int[] depth, int first, int second) {
        if (high[first] - low[first] < high[second] - low[second]) {
            int held = low[first];
            low[first] = low[second];
            low[second] = held;
            held = high[first];
            high[first] = high[second];
            high[second] = held;
            held = depth[first];
            depth[first] = depth[second];
            depth[second] = held;
        }
    }

    private int bigBucketSize(int bucket) {
        return frequencies[(bucket + 1) << 8] - frequencies[bucket << 8];
    }

    private void prepareMainSort() {
        int length = blockLength;
        Arrays.fill(frequencies, 0);
        int pair = (block[0] & 0xFF) << 8;
        for (int i = length - 1; i >= 0; i--) {
            quadrant[i] = 0;
            pair = (pair >> 8) | ((block[i] & 0xFF) << 8);
            frequencies[pair]++;
        }
        for (int i = 0; i < OVERSHOOT; i++) {
            block[length + i] = block[i];
            quadrant[length + i] = 0;
        }
        for (int i = 1; i <= 65536; i++) {
            frequencies[i] += frequencies[i - 1];
        }
        pair = (block[0] & 0xFF) << 8;
        for (int i = length - 1; i >= 0; i--) {
            pair = (pair >> 8) | ((block[i] & 0xFF) << 8);
            int at = frequencies[pair] - 1;
            frequencies[pair] = at;
            pointers[at] = i;
        }
    }

    private int[] orderBigBucketsByFrequency() {
        int[] order = new int[256];
        for (int i = 0; i <= LAST_OCTET; i++) {
            order[i] = i;
        }
        int stride = 1;
        do {
            stride = 3 * stride + 1;
        } while (stride <= 256);
        do {
            stride = stride / 3;
            for (int i = stride; i <= LAST_OCTET; i++) {
                int moving = order[i];
                int j = i;
                while (bigBucketSize(order[j - stride]) > bigBucketSize(moving)) {
                    order[j] = order[j - stride];
                    j -= stride;
                    if (j <= stride - 1) {
                        break;
                    }
                }
                order[j] = moving;
            }
        } while (stride != 1);
        return order;
    }

    private void sortByMainAlgorithm() {
        prepareMainSort();
        int length = blockLength;
        int[] order = orderBigBucketsByFrequency();
        boolean[] isBigDone = new boolean[256];
        int[] copyStart = new int[256];
        int[] copyEnd = new int[256];
        for (int i = 0; i <= LAST_OCTET; i++) {
            int bucket = order[i];
            for (int j = 0; j <= LAST_OCTET; j++) {
                if (j != bucket) {
                    int small = (bucket << 8) + j;
                    if ((frequencies[small] & SET_MASK) == 0) {
                        int low = frequencies[small] & CLEAR_MASK;
                        int high = (frequencies[small + 1] & CLEAR_MASK) - 1;
                        if (high > low) {
                            quicksortMainRange(low, high, RADIX_DEPTH);
                            if (budget < 0) {
                                return;
                            }
                        }
                    }
                    frequencies[small] |= SET_MASK;
                }
            }
            for (int j = 0; j <= LAST_OCTET; j++) {
                copyStart[j] = frequencies[(j << 8) + bucket] & CLEAR_MASK;
                copyEnd[j] = (frequencies[(j << 8) + bucket + 1] & CLEAR_MASK) - 1;
            }
            for (int j = frequencies[bucket << 8] & CLEAR_MASK; j < copyStart[bucket]; j++) {
                int at = pointers[j] - 1;
                if (at < 0) {
                    at += length;
                }
                int octet = block[at] & 0xFF;
                if (!isBigDone[octet]) {
                    pointers[copyStart[octet]++] = at;
                }
            }
            for (int j = (frequencies[(bucket + 1) << 8] & CLEAR_MASK) - 1; j > copyEnd[bucket]; j--) {
                int at = pointers[j] - 1;
                if (at < 0) {
                    at += length;
                }
                int octet = block[at] & 0xFF;
                if (!isBigDone[octet]) {
                    pointers[copyEnd[octet]--] = at;
                }
            }
            for (int j = 0; j <= LAST_OCTET; j++) {
                frequencies[(j << 8) + bucket] |= SET_MASK;
            }
            isBigDone[bucket] = true;
            if (i < LAST_OCTET) {
                updateQuadrants(bucket);
            }
        }
    }

    private void updateQuadrants(int bucket) {
        int start = frequencies[bucket << 8] & CLEAR_MASK;
        int size = (frequencies[(bucket + 1) << 8] & CLEAR_MASK) - start;
        int shifts = 0;
        while ((size >> shifts) > 65534) {
            shifts++;
        }
        for (int j = size - 1; j >= 0; j--) {
            int position = pointers[start + j];
            char value = (char) (j >> shifts);
            quadrant[position] = value;
            if (position < OVERSHOOT) {
                quadrant[position + blockLength] = value;
            }
        }
    }
}
