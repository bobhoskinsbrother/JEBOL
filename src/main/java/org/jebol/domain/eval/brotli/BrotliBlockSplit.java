package org.jebol.domain.eval.brotli;

final class BrotliBlockSplit {

    static final int MOST_TYPES_ALLOWED = 256;

    private int[] types = new int[0];
    private int[] lengths = new int[0];
    private int howManyBlocks;
    private int howManyTypes;

    void roomFor(int howMany) {
        if (howMany <= types.length) {
            return;
        }
        types = java.util.Arrays.copyOf(types, howMany);
        lengths = java.util.Arrays.copyOf(lengths, howMany);
    }

    int typeAt(int which) {
        return types[which];
    }

    int lengthAt(int which) {
        return lengths[which];
    }

    void typeIs(int which, int type) {
        types[which] = type;
    }

    void lengthIs(int which, int length) {
        lengths[which] = length;
    }

    void lengthGrows(int which, int by) {
        lengths[which] += by;
    }

    int howManyBlocks() {
        return howManyBlocks;
    }

    void howManyBlocksIs(int howMany) {
        howManyBlocks = howMany;
    }

    int howManyTypes() {
        return howManyTypes;
    }

    void oneMoreType() {
        howManyTypes++;
    }

    void howManyTypesIs(int howMany) {
        howManyTypes = howMany;
    }
}
