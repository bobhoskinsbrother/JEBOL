package org.jebol.domain.eval;

final class Md4 extends Md4FamilyDigest {

    private static final int[] STARTING_WORDS = {
        0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476,
    };

    private static final int[][] WORD_ORDER = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
        {0, 4, 8, 12, 1, 5, 9, 13, 2, 6, 10, 14, 3, 7, 11, 15},
        {0, 8, 4, 12, 2, 10, 6, 14, 1, 9, 5, 13, 3, 11, 7, 15},
    };

    private static final int[][] ROTATIONS_REPEATING_FOUR_TO_A_ROUND = {
        {3, 7, 11, 19},
        {3, 5, 9, 13},
        {3, 9, 11, 15},
    };

    private static final int[] ROUND_CONSTANTS = {
        0x00000000, 0x5A827999, 0x6ED9EBA1,
    };

    @Override
    int[] startingWords() {
        return STARTING_WORDS.clone();
    }

    @Override
    void compress(int[] words, int[] block) {
        int first = words[0];
        int second = words[1];
        int third = words[2];
        int fourth = words[3];
        for (int round = 0; round < 3; round++) {
            for (int step = 0; step < 16; step++) {
                int turned = Integer.rotateLeft(first
                        + mixedByChoiceThenMajorityThenExclusiveOr(
                                round, second, third, fourth)
                        + block[WORD_ORDER[round][step]]
                        + ROUND_CONSTANTS[round],
                        ROTATIONS_REPEATING_FOUR_TO_A_ROUND[round][step % 4]);
                first = fourth;
                fourth = third;
                third = second;
                second = turned;
            }
        }
        words[0] += first;
        words[1] += second;
        words[2] += third;
        words[3] += fourth;
    }

    private int mixedByChoiceThenMajorityThenExclusiveOr(
            int round, int second, int third, int fourth) {
        return switch (round) {
            case 0 -> second & third | ~second & fourth;
            case 1 -> second & third | second & fourth | third & fourth;
            default -> second ^ third ^ fourth;
        };
    }
}
