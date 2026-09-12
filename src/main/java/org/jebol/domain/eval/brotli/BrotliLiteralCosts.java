package org.jebol.domain.eval.brotli;

import java.util.Arrays;

final class BrotliLiteralCosts {

    private static final double THREE_QUARTERS_IS_ENOUGH_TO_CALL_IT_UTF8 = 0.75;
    private static final int WINDOW_HALF_FOR_TEXT = 495;
    private static final int WINDOW_HALF_FOR_ANYTHING_ELSE = 2000;
    private static final int HOW_MANY_BYTES_ARE_TREATED_AS_A_PROLOGUE = 2000;
    private static final double HOW_MUCH_THE_PROLOGUE_COSTS_EXTRA = 0.35;
    private static final int NOT_A_CHARACTER = 0x110000;

    private BrotliLiteralCosts() {
    }

    static void estimate(byte[] data, int from, int length, int mask,
            float[] cost) {

        if (mostlyUtf8(data, from, mask, length)) {
            estimateAsText(data, from, length, mask, cost);
            return;
        }
        estimateAsAnythingElse(data, from, length, mask, cost);
    }

    static boolean mostlyUtf8(byte[] data, int from, int mask, int length) {
        long howMuchIsUtf8 = 0;
        int at = 0;
        while (at < length) {
            int howManyBytes = oneCharacterWithAFlagAboveTheLowByteWhereItIsNotOne(
                    data, (from + at) & mask, length - at);
            at += howManyBytes & 0xFF;
            if ((howManyBytes >>> 8) == 0) {
                howMuchIsUtf8 += howManyBytes & 0xFF;
            }
        }
        return (double) howMuchIsUtf8
                > THREE_QUARTERS_IS_ENOUGH_TO_CALL_IT_UTF8 * (double) length;
    }

    private static int oneCharacterWithAFlagAboveTheLowByteWhereItIsNotOne(byte[] data, int at, int left) {
        int first = data[at] & 0xFF;
        if ((first & 0x80) == 0 && first > 0) {
            return 1;
        }
        if (left > 1 && (first & 0xE0) == 0xC0
                && (data[at + 1] & 0xC0) == 0x80
                && (((first & 0x1F) << 6) | (data[at + 1] & 0x3F)) > 0x7F) {
            return 2;
        }
        if (left > 2 && (first & 0xF0) == 0xE0
                && (data[at + 1] & 0xC0) == 0x80
                && (data[at + 2] & 0xC0) == 0x80
                && (((first & 0x0F) << 12) | ((data[at + 1] & 0x3F) << 6)
                        | (data[at + 2] & 0x3F)) > 0x7FF) {
            return 3;
        }
        if (left > 3 && (first & 0xF8) == 0xF0
                && (data[at + 1] & 0xC0) == 0x80
                && (data[at + 2] & 0xC0) == 0x80
                && (data[at + 3] & 0xC0) == 0x80) {
            int symbol = ((first & 0x07) << 18) | ((data[at + 1] & 0x3F) << 12)
                    | ((data[at + 2] & 0x3F) << 6) | (data[at + 3] & 0x3F);
            if (symbol > 0xFFFF && symbol <= 0x10FFFF) {
                return 4;
            }
        }
        return 1 | (NOT_A_CHARACTER << 8);
    }

    private static int whereInACharacter(int previous, int here, int mostAllowed) {
        if (here < 128) {
            return 0;
        }
        if (here >= 192) {
            return Math.min(1, mostAllowed);
        }
        return previous < 0xE0 ? 0 : Math.min(2, mostAllowed);
    }

    private static int howManyHistogramsAreWorthItStartingAtOneAndOnlyFalling(byte[] data, int from,
            int length, int mask) {

        int[] counts = new int[3];
        int previous = 0;
        for (int each = 0; each < length; each++) {
            int here = data[(from + each) & mask] & 0xFF;
            counts[whereInACharacter(previous, here, 2)]++;
            previous = here;
        }
        return counts[1] + counts[2] < 25 ? 0 : 1;
    }

    private static void estimateAsText(byte[] data, int from, int length,
            int mask, float[] cost) {

        int howManyHistograms =
                howManyHistogramsAreWorthItStartingAtOneAndOnlyFalling(
                        data, from, length, mask);
        int windowHalf = WINDOW_HALF_FOR_TEXT;
        int inWindow = Math.min(windowHalf, length);
        int[] histogram = new int[3 * 256];
        int[] inWindowOfEachKind = new int[3];

        int previous = 0;
        int whichHistogram = 0;
        for (int each = 0; each < inWindow; each++) {
            int here = data[(from + each) & mask] & 0xFF;
            histogram[256 * whichHistogram + here]++;
            inWindowOfEachKind[whichHistogram]++;
            whichHistogram = whereInACharacter(previous, here, howManyHistograms);
            previous = here;
        }

        for (int each = 0; each < length; each++) {
            if (each >= windowHalf) {
                int leaving = each < windowHalf + 1
                        ? 0 : data[(from + each - windowHalf - 1) & mask] & 0xFF;
                int beforeThat = each < windowHalf + 2
                        ? 0 : data[(from + each - windowHalf - 2) & mask] & 0xFF;
                int kind = whereInACharacter(beforeThat, leaving, howManyHistograms);
                histogram[256 * kind
                        + (data[(from + each - windowHalf) & mask] & 0xFF)]--;
                inWindowOfEachKind[kind]--;
            }
            if (each + windowHalf < length) {
                int arriving = data[(from + each + windowHalf - 1) & mask] & 0xFF;
                int beforeThat = data[(from + each + windowHalf - 2) & mask] & 0xFF;
                int kind = whereInACharacter(beforeThat, arriving, howManyHistograms);
                histogram[256 * kind
                        + (data[(from + each + windowHalf) & mask] & 0xFF)]++;
                inWindowOfEachKind[kind]++;
            }
            int justBefore = each < 1 ? 0 : data[(from + each - 1) & mask] & 0xFF;
            int beforeThat = each < 2 ? 0 : data[(from + each - 2) & mask] & 0xFF;
            int kind = whereInACharacter(beforeThat, justBefore, howManyHistograms);
            int here = data[(from + each) & mask] & 0xFF;
            long seen = histogram[256 * kind + here];
            if (seen == 0) {
                seen = 1;
            }
            double bits = BrotliCodes.fastLog2(inWindowOfEachKind[kind])
                    - BrotliCodes.fastLog2(seen);
            bits += 0.02905;
            if (bits < 1.0) {
                bits = bits * 0.5 + 0.5;
            }
            if (each < HOW_MANY_BYTES_ARE_TREATED_AS_A_PROLOGUE) {
                bits += HOW_MUCH_THE_PROLOGUE_COSTS_EXTRA
                        + HOW_MUCH_THE_PROLOGUE_COSTS_EXTRA
                                / HOW_MANY_BYTES_ARE_TREATED_AS_A_PROLOGUE
                                * (double) each;
            }
            cost[each] = (float) bits;
        }
    }

    private static void estimateAsAnythingElse(byte[] data, int from, int length,
            int mask, float[] cost) {

        int windowHalf = WINDOW_HALF_FOR_ANYTHING_ELSE;
        int inWindow = Math.min(windowHalf, length);
        int[] histogram = new int[256];
        Arrays.fill(histogram, 0);
        for (int each = 0; each < inWindow; each++) {
            histogram[data[(from + each) & mask] & 0xFF]++;
        }
        for (int each = 0; each < length; each++) {
            if (each >= windowHalf) {
                histogram[data[(from + each - windowHalf) & mask] & 0xFF]--;
                inWindow--;
            }
            if (each + windowHalf < length) {
                histogram[data[(from + each + windowHalf) & mask] & 0xFF]++;
                inWindow++;
            }
            long seen = histogram[data[(from + each) & mask] & 0xFF];
            if (seen == 0) {
                seen = 1;
            }
            double bits = BrotliCodes.fastLog2(inWindow) - BrotliCodes.fastLog2(seen);
            bits += 0.029;
            if (bits < 1.0) {
                bits = bits * 0.5 + 0.5;
            }
            cost[each] = (float) bits;
        }
    }
}
