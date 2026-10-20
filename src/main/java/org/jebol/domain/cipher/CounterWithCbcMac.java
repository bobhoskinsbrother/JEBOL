package org.jebol.domain.cipher;

import java.util.Arrays;

public final class CounterWithCbcMac {

    private static final int BLOCK = 16;
    private static final int SHORTEST_NONCE = 7;
    private static final int LONGEST_NONCE = 13;
    private static final int WHERE_TWO_BYTES_STOP_BEING_ENOUGH_FOR_A_HEADER = 0xFF00;
    private static final Sealed NOTHING = new Sealed(new byte[0], false);

    private final JoinedOctets joined = new JoinedOctets();

    public boolean canIssueATagOf(int octets) {
        return octets >= 4 && octets <= BLOCK && octets % 2 == 0;
    }

    public record Sealed(byte[] octets, boolean worked) {
    }


    public Sealed encipher(OneBlock cipher, byte[] vector, int tagOctets,
                           byte[] header, byte[] message) {

        if (tagOctets != 0 && !canIssueATagOf(tagOctets)) {
            return NOTHING;
        }
        byte[] nonce = clampNonce(vector);
        byte[] cipherText = maskWithKeystream(cipher, nonce, message);
        if (tagOctets == 0) {
            return new Sealed(cipherText, true);
        }
        byte[] tag = computeTag(cipher, nonce, tagOctets, header, message);
        return new Sealed(joined.of(cipherText, tag), true);
    }

    public Sealed decipher(OneBlock cipher, byte[] vector, int tagOctets,
                           byte[] header, byte[] sealedOctets) {

        if (tagOctets != 0 && !canIssueATagOf(tagOctets)) {
            return NOTHING;
        }
        if (sealedOctets.length < tagOctets) {
            return NOTHING;
        }
        byte[] cipherText =
                Arrays.copyOf(sealedOctets, sealedOctets.length - tagOctets);
        byte[] tagWritten = Arrays.copyOfRange(
                sealedOctets, sealedOctets.length - tagOctets, sealedOctets.length);
        byte[] nonce = clampNonce(vector);
        byte[] message = maskWithKeystream(cipher, nonce, cipherText);
        if (tagOctets == 0) {
            return new Sealed(message, true);
        }
        byte[] tagComputed = computeTag(cipher, nonce, tagOctets, header, message);
        return agreeInConstantTime(tagWritten, tagComputed)
                ? new Sealed(message, true)
                : NOTHING;
    }

    private boolean agreeInConstantTime(
            byte[] written, byte[] computed) {
        if (written.length != computed.length) {
            return false;
        }
        int differences = 0;
        for (int at = 0; at < written.length; at++) {
            differences |= written[at] ^ computed[at];
        }
        return differences == 0;
    }

    private byte[] clampNonce(byte[] vector) {
        return Arrays.copyOf(vector,
                Math.clamp(vector.length, SHORTEST_NONCE, LONGEST_NONCE));
    }

    private byte[] maskWithKeystream(
            OneBlock cipher, byte[] nonce, byte[] message) {
        byte[] masked = new byte[message.length];
        for (int at = 0; at < message.length; at += BLOCK) {
            byte[] keystream = cipher.encipher(
                    makeCounterBlock(nonce, at / BLOCK + 1));
            for (int within = 0; within < BLOCK && at + within < message.length;
                    within++) {
                masked[at + within] =
                        (byte) (message[at + within] ^ keystream[within]);
            }
        }
        return masked;
    }

    private byte[] makeCounterBlock(byte[] nonce, long count) {
        int countOctets = BLOCK - 1 - nonce.length;
        byte[] counter = new byte[BLOCK];
        counter[0] = (byte) (countOctets - 1);
        System.arraycopy(nonce, 0, counter, 1, nonce.length);
        long left = count;
        for (int at = BLOCK - 1; at > nonce.length; at--) {
            counter[at] = (byte) left;
            left >>>= 8;
        }
        return counter;
    }

    private byte[] computeTag(OneBlock cipher, byte[] nonce, int tagOctets,
                              byte[] header, byte[] message) {

        byte[] chained = cipher.encipher(
                makeFirstBlock(nonce, tagOctets, header.length, message.length));
        if (header.length > 0) {
            chained = chainThrough(cipher, chained,
                    joined.of(writeHeadersLength(header.length), header));
        }
        chained = chainThrough(cipher, chained, message);
        byte[] maskedWith = cipher.encipher(makeCounterBlock(nonce, 0));
        byte[] tag = new byte[tagOctets];
        for (int at = 0; at < tagOctets; at++) {
            tag[at] = (byte) (chained[at] ^ maskedWith[at]);
        }
        return tag;
    }

    private byte[] makeFirstBlock(byte[] nonce, int tagOctets,
                                  int headerOctets, int messageOctets) {

        int countOctets = BLOCK - 1 - nonce.length;
        byte[] first = new byte[BLOCK];
        first[0] = (byte) ((headerOctets > 0 ? 0x40 : 0)
                | (tagOctets - 2) / 2 << 3
                | countOctets - 1);
        System.arraycopy(nonce, 0, first, 1, nonce.length);
        long left = messageOctets;
        for (int at = BLOCK - 1; at > nonce.length; at--) {
            first[at] = (byte) left;
            left >>>= 8;
        }
        return first;
    }

    private byte[] writeHeadersLength(int octets) {
        if (octets < WHERE_TWO_BYTES_STOP_BEING_ENOUGH_FOR_A_HEADER) {
            return new byte[] {(byte) (octets >>> 8), (byte) octets};
        }
        byte[] written = new byte[6];
        written[0] = (byte) 0xFF;
        written[1] = (byte) 0xFE;
        for (int at = 5; at >= 2; at--) {
            written[at] = (byte) (octets >>> (5 - at) * 8);
        }
        return written;
    }

    private byte[] chainThrough(OneBlock cipher, byte[] chained,
                                byte[] octets) {

        byte[] running = chained;
        for (int at = 0; at < octets.length; at += BLOCK) {
            byte[] combined = new byte[BLOCK];
            for (int within = 0; within < BLOCK; within++) {
                byte next = at + within < octets.length
                        ? octets[at + within]
                        : 0;
                combined[within] = (byte) (running[within] ^ next);
            }
            running = cipher.encipher(combined);
        }
        return running;
    }
}
