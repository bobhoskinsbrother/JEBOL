package org.jebol.domain.eval.compression.brotli;

final class BrotliDictionarySearch {

    private static final long CUTTING_TRANSFORM_FOR_EACH_AMOUNT = 0x071B520ADA2D3200L;
    private static final int MOST_THAT_MAY_BE_CUT = 10;
    private static final int MIXING_MULTIPLIER = 0x1E35A7BD;
    private static final int SLOT_BITS = 14;

    private long lookups;
    private long matches;

    private static int hashSlotFor(byte[] data, int at) {
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
                    long maxDistance, BrotliMatch best, boolean onlyFirstSlot) {

        if (!stillPayingItsWay()) {
            return;
        }
        int slot = hashSlotFor(data, at) << 1;
        int howManySlots = onlyFirstSlot ? 1 : 2;
        for (int tried = 0; tried < howManySlots; tried++, slot++) {
            lookups++;
            if (BrotliDictionaryHash.readWordLengthAt(slot) != 0
                    && fitsWord(data, at, maxLength, maxBackward, maxDistance,
                            best, BrotliDictionaryHash.readWordLengthAt(slot),
                            BrotliDictionaryHash.readWordIndexAt(slot))) {
                matches++;
            }
        }
    }

    private static boolean fitsWord(byte[] data, int at, int maxLength,
                                    long maxBackward, long maxDistance, BrotliMatch best,
                                    int wordLength, int wordIndex) {

        if (wordLength > maxLength) {
            return false;
        }
        byte[] words = BrotliDictionary.readWords();
        int wordAt = BrotliDictionary.offsetFor(wordLength) + wordLength * wordIndex;
        int agreeing = countAgreeingBytes(data, at, words, wordAt, wordLength);
        if (agreeing == 0 || agreeing + MOST_THAT_MAY_BE_CUT <= wordLength) {
            return false;
        }
        int cut = wordLength - agreeing;
        long whichTransform = ((long) cut << 2)
                + ((CUTTING_TRANSFORM_FOR_EACH_AMOUNT >>> (cut * 6)) & 0x3F);
        long backward = maxBackward + 1 + wordIndex
                + (whichTransform << BrotliDictionary.readSizeBitsFor(wordLength));
        if (backward > maxDistance) {
            return false;
        }
        long score = BrotliMatch.score(agreeing, backward);
        if (score < best.score) {
            return false;
        }
        best.length = agreeing;
        best.lengthCodeDelta = wordLength - agreeing;
        best.distance = backward;
        best.score = score;
        return true;
    }

    private static int countAgreeingBytes(byte[] data, int at, byte[] words,
                                          int wordAt, int limit) {

        int agreeing = 0;
        while (agreeing < limit && data[at + agreeing] == words[wordAt + agreeing]) {
            agreeing++;
        }
        return agreeing;
    }
}
