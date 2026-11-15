package org.jebol.domain.host;

import java.util.Arrays;

public final class Sha3 {

    private static final int ROUNDS = 24;
    private static final int LANES = 25;
    private static final int STATE_BYTES = 200;
    private static final byte DOMAIN_PADDING = 0x06;
    private static final byte FINAL_BIT = (byte) 0x80;
    private static final int HMAC_INNER = 0x36;
    private static final int HMAC_OUTER = 0x5c;

    private static final long[] ROUND_CONSTANTS = {
            0x0000000000000001L, 0x0000000000008082L, 0x800000000000808aL, 0x8000000080008000L,
            0x000000000000808bL, 0x0000000080000001L, 0x8000000080008081L, 0x8000000000008009L,
            0x000000000000008aL, 0x0000000000000088L, 0x0000000080008009L, 0x000000008000000aL,
            0x000000008000808bL, 0x800000000000008bL, 0x8000000000008089L, 0x8000000000008003L,
            0x8000000000008002L, 0x8000000000000080L, 0x000000000000800aL, 0x800000008000000aL,
            0x8000000080008081L, 0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L,
    };

    private static final int[] ROTATIONS = {
            1, 3, 6, 10, 15, 21, 28, 36, 45, 55, 2, 14, 27, 41, 56, 8, 25, 43, 62, 18, 39, 61, 20, 44,
    };

    private static final int[] PI_LANES = {
            10, 7, 11, 17, 18, 3, 5, 16, 8, 21, 24, 4, 15, 23, 19, 13, 12, 2, 20, 14, 22, 9, 6, 1,
    };

    private final int outputBytes;
    private final int blockBytes;
    private final long[] state;
    private int index;

    public Sha3(int outputBytes) {
        this(outputBytes, STATE_BYTES - 2 * outputBytes, new long[LANES], 0);
    }

    private Sha3(int outputBytes, int blockBytes, long[] state, int index) {
        this.outputBytes = outputBytes;
        this.blockBytes = blockBytes;
        this.state = state;
        this.index = index;
    }

    public int blockBytes() {
        return blockBytes;
    }

    public void add(byte[] octets, int from, int length) {
        for (int at = from; at < from + length; at++) {
            absorb(index, octets[at]);
            index = (index + 1) % blockBytes;
            if (index == 0) {
                permute(state);
            }
        }
    }

    public Sha3 copy() {
        return new Sha3(outputBytes, blockBytes, state.clone(), index);
    }

    public byte[] digest() {
        absorb(index, DOMAIN_PADDING);
        absorb(blockBytes - 1, FINAL_BIT);
        permute(state);
        byte[] output = new byte[outputBytes];
        for (int at = 0; at < outputBytes; at++) {
            output[at] = (byte) (state[at >> 3] >>> ((at & 7) << 3));
        }
        return output;
    }

    public static byte[] hmac(int outputBytes, byte[] key, byte[] octets) {
        Sha3 sizing = new Sha3(outputBytes);
        byte[] block = key.length > sizing.blockBytes() ? digestOf(outputBytes, key) : key;
        byte[] padded = Arrays.copyOf(block, sizing.blockBytes());
        Sha3 inner = new Sha3(outputBytes);
        inner.add(masked(padded, HMAC_INNER), 0, padded.length);
        inner.add(octets, 0, octets.length);
        byte[] innerDigest = inner.digest();
        Sha3 outer = new Sha3(outputBytes);
        outer.add(masked(padded, HMAC_OUTER), 0, padded.length);
        outer.add(innerDigest, 0, innerDigest.length);
        return outer.digest();
    }

    private static byte[] digestOf(int outputBytes, byte[] octets) {
        Sha3 running = new Sha3(outputBytes);
        running.add(octets, 0, octets.length);
        return running.digest();
    }

    private static byte[] masked(byte[] padded, int mask) {
        byte[] masked = new byte[padded.length];
        for (int at = 0; at < padded.length; at++) {
            masked[at] = (byte) (padded[at] ^ mask);
        }
        return masked;
    }

    private void absorb(int at, byte octet) {
        state[at >> 3] ^= (octet & 0xFFL) << ((at & 7) << 3);
    }

    private static void permute(long[] s) {
        long[] column = new long[5];
        for (int round = 0; round < ROUNDS; round++) {
            for (int x = 0; x < 5; x++) {
                column[x] = s[x] ^ s[x + 5] ^ s[x + 10] ^ s[x + 15] ^ s[x + 20];
            }
            for (int x = 0; x < 5; x++) {
                long mixed = column[(x + 4) % 5] ^ Long.rotateLeft(column[(x + 1) % 5], 1);
                for (int y = 0; y < LANES; y += 5) {
                    s[y + x] ^= mixed;
                }
            }
            long carried = s[1];
            for (int step = 0; step < ROUNDS; step++) {
                int lane = PI_LANES[step];
                long displaced = s[lane];
                s[lane] = Long.rotateLeft(carried, ROTATIONS[step]);
                carried = displaced;
            }
            for (int y = 0; y < LANES; y += 5) {
                for (int x = 0; x < 5; x++) {
                    column[x] = s[y + x];
                }
                for (int x = 0; x < 5; x++) {
                    s[y + x] = column[x] ^ (~column[(x + 1) % 5] & column[(x + 2) % 5]);
                }
            }
            s[0] ^= ROUND_CONSTANTS[round];
        }
    }
}
