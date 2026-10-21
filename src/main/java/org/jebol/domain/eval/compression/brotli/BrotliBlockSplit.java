package org.jebol.domain.eval.compression.brotli;

final class BrotliBlockSplit {

    static final int MOST_TYPES_ALLOWED = 256;

    private int[] types = new int[0];
    private int[] lengths = new int[0];
    private int howManyBlocks;
    private int howManyTypes;

    void makeRoomFor(int howMany) {
        if (howMany <= types.length) {
            return;
        }
        types = java.util.Arrays.copyOf(types, howMany);
        lengths = java.util.Arrays.copyOf(lengths, howMany);
    }

    int readTypeAt(int which) {
        return types[which];
    }

    int readLengthAt(int which) {
        return lengths[which];
    }

    void setType(int which, int type) {
        types[which] = type;
    }

    void setLength(int which, int length) {
        lengths[which] = length;
    }

    void growLength(int which, int by) {
        lengths[which] += by;
    }

    int countBlocks() {
        return howManyBlocks;
    }

    void setBlockCount(int howMany) {
        howManyBlocks = howMany;
    }

    int countTypes() {
        return howManyTypes;
    }

    void addType() {
        howManyTypes++;
    }

    void setTypeCount(int howMany) {
        howManyTypes = howMany;
    }
}
