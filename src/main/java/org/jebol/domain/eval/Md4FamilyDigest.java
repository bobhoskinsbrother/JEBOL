package org.jebol.domain.eval;

abstract class Md4FamilyDigest {

    private static final int BLOCK_BYTES = 64;
    private static final int WORDS_IN_A_BLOCK = 16;
    private static final int ROOM_FOR_THE_END_MARK_AND_THE_LENGTH = 9;

    private final EndianOctets octets = new EndianOctets();

    abstract int[] startingWords();

    abstract void compress(int[] words, int[] block);

    byte[] of(byte[] message) {
        int[] words = startingWords();
        byte[] padded = padded(message);
        int[] block = new int[WORDS_IN_A_BLOCK];
        for (int at = 0; at < padded.length; at += BLOCK_BYTES) {
            for (int word = 0; word < WORDS_IN_A_BLOCK; word++) {
                block[word] = octets.littleEndianWordAt(padded, at + word * 4);
            }
            compress(words, block);
        }
        return digestOf(words);
    }

    private byte[] padded(byte[] message) {
        int blocks = (message.length + ROOM_FOR_THE_END_MARK_AND_THE_LENGTH + BLOCK_BYTES - 1)
                / BLOCK_BYTES;
        byte[] padded = new byte[blocks * BLOCK_BYTES];
        System.arraycopy(message, 0, padded, 0, message.length);
        padded[message.length] = (byte) 0x80;
        long bits = (long) message.length * 8;
        for (int at = 0; at < 8; at++) {
            padded[padded.length - 8 + at] = (byte) (bits >>> at * 8);
        }
        return padded;
    }

    private byte[] digestOf(int[] words) {
        byte[] digest = new byte[words.length * 4];
        for (int word = 0; word < words.length; word++) {
            for (int at = 0; at < 4; at++) {
                digest[word * 4 + at] = (byte) (words[word] >>> at * 8);
            }
        }
        return digest;
    }
}
