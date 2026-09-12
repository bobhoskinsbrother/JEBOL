package org.jebol.domain.cipher;

/**
 * The two plain ways of running a block cipher over more than one block, for a
 * cipher the JVM has never heard of.
 *
 * <p>Both walk whole blocks and neither pads: what to do with a part block is
 * the caller's question, not the mode's.
 */
public final class BlockModes {

    private BlockModes() {
    }

    private static final int BLOCK = Camellia.BLOCK;

    /**
     * Electronic codebook: every block on its own, which is why it is the one
     * nobody should use for a real message -- two identical blocks of plain
     * text give two identical blocks of cipher text, so the shape of the
     * message shows through.
     */
    public static byte[] codebook(OneBlock cipher, byte[] octets) {
        byte[] answer = new byte[octets.length];
        for (int at = 0; at + BLOCK <= octets.length; at += BLOCK) {
            byte[] transformed = cipher.enciphered(blockAt(octets, at));
            System.arraycopy(transformed, 0, answer, at, BLOCK);
        }
        return answer;
    }

    /**
     * Cipher block chaining, enciphering: each block is combined with the
     * cipher text before it, so the same block twice gives two answers. The
     * vector is left holding the last block out, so a second call carries on
     * from the first.
     */
    public static byte[] chainingForwards(
            OneBlock cipher, byte[] vector, byte[] octets) {

        byte[] answer = new byte[octets.length];
        for (int at = 0; at + BLOCK <= octets.length; at += BLOCK) {
            byte[] combined = new byte[BLOCK];
            for (int within = 0; within < BLOCK; within++) {
                combined[within] = (byte) (octets[at + within] ^ vector[within]);
            }
            byte[] transformed = cipher.enciphered(combined);
            System.arraycopy(transformed, 0, answer, at, BLOCK);
            System.arraycopy(transformed, 0, vector, 0, BLOCK);
        }
        return answer;
    }

    /**
     * Cipher block chaining, deciphering: undo the block, then combine with
     * the cipher text that came before it. The order is the other way round
     * from enciphering.
     */
    public static byte[] chainingBackwards(
            OneBlock cipher, byte[] vector, byte[] octets) {

        byte[] answer = new byte[octets.length];
        for (int at = 0; at + BLOCK <= octets.length; at += BLOCK) {
            byte[] keptBeforeTheVectorIsOverwritten = blockAt(octets, at);
            byte[] undone = cipher.enciphered(keptBeforeTheVectorIsOverwritten);
            for (int within = 0; within < BLOCK; within++) {
                answer[at + within] = (byte) (undone[within] ^ vector[within]);
            }
            System.arraycopy(keptBeforeTheVectorIsOverwritten, 0, vector, 0, BLOCK);
        }
        return answer;
    }

    private static byte[] blockAt(byte[] octets, int at) {
        byte[] block = new byte[BLOCK];
        System.arraycopy(octets, at, block, 0, BLOCK);
        return block;
    }
}
