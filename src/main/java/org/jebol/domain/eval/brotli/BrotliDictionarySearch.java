package org.jebol.domain.eval.brotli;

/**
 * Looking for the text about to be written among the dictionary's words.
 *
 * <p>{@code SearchInStaticDictionary} and {@code TestStaticDictionaryItem} in
 * {@code hash.h}. A dictionary word is referred to by a distance beyond the end
 * of everything written so far, so a match here costs nothing to store and
 * saves whatever the word is long.
 *
 * <p>Only a prefix of the word need match. Cutting the last few characters off
 * is one of the transforms, so a word that agrees for all but its last three
 * letters is still usable, with the transform saying which letters to drop. Ten
 * is as many as may be cut, and which transform does the cutting for each amount
 * is packed six bits at a time into one number.
 *
 * <p>The search gives up on itself. It counts how often it has looked and how
 * often that found something, and once fewer than one lookup in a hundred and
 * twenty eight is paying off it stops looking at all for the rest of the input.
 * That is why this holds state rather than being a static call: text that is not
 * English stops paying the dictionary's cost early.
 */
final class BrotliDictionarySearch {

    private static final long CUTTING_TRANSFORM_FOR_EACH_AMOUNT = 0x071B520ADA2D3200L;
    private static final int MOST_THAT_MAY_BE_CUT = 10;
    private static final int MIXING_MULTIPLIER = 0x1E35A7BD;
    private static final int SLOT_BITS = 14;

    private long lookups;
    private long matches;

    private static int slotFor(byte[] data, int at) {
        int fourBytes = (data[at] & 0xFF)
                | ((data[at + 1] & 0xFF) << 8)
                | ((data[at + 2] & 0xFF) << 16)
                | ((data[at + 3] & 0xFF) << 24);
        return (fourBytes * MIXING_MULTIPLIER) >>> (32 - SLOT_BITS);
    }

    private boolean stillPayingItsWay() {
        return matches >= (lookups >> 7);
    }

    void searchFrom(byte[] data, int at, int maxLength, long maxBackward,
                    long maxDistance, BrotliMatch best, boolean onlyTheFirstSlot) {

        if (!stillPayingItsWay()) {
            return;
        }
        int slot = slotFor(data, at) << 1;
        int howManySlots = onlyTheFirstSlot ? 1 : 2;
        for (int tried = 0; tried < howManySlots; tried++, slot++) {
            lookups++;
            if (BrotliDictionaryHash.wordLengthAt(slot) != 0
                    && wordFits(data, at, maxLength, maxBackward, maxDistance,
                            best, BrotliDictionaryHash.wordLengthAt(slot),
                            BrotliDictionaryHash.wordIndexAt(slot))) {
                matches++;
            }
        }
    }

    private static boolean wordFits(byte[] data, int at, int maxLength,
            long maxBackward, long maxDistance, BrotliMatch best,
            int wordLength, int wordIndex) {

        if (wordLength > maxLength) {
            return false;
        }
        byte[] words = BrotliDictionary.words();
        int wordAt = BrotliDictionary.offsetFor(wordLength) + wordLength * wordIndex;
        int agreeing = agreeingBytes(data, at, words, wordAt, wordLength);
        if (agreeing == 0 || agreeing + MOST_THAT_MAY_BE_CUT <= wordLength) {
            return false;
        }
        int cut = wordLength - agreeing;
        long whichTransform = ((long) cut << 2)
                + ((CUTTING_TRANSFORM_FOR_EACH_AMOUNT >>> (cut * 6)) & 0x3F);
        long backward = maxBackward + 1 + wordIndex
                + (whichTransform << BrotliDictionary.sizeBitsFor(wordLength));
        if (backward > maxDistance) {
            return false;
        }
        long score = BrotliMatch.scoreFor(agreeing, backward);
        if (score < best.score) {
            return false;
        }
        best.length = agreeing;
        best.lengthCodeDelta = wordLength - agreeing;
        best.distance = backward;
        best.score = score;
        return true;
    }

    private static int agreeingBytes(byte[] data, int at, byte[] words,
            int wordAt, int limit) {

        int agreeing = 0;
        while (agreeing < limit && data[at + agreeing] == words[wordAt + agreeing]) {
            agreeing++;
        }
        return agreeing;
    }
}
