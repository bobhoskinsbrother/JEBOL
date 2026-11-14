package org.jebol.domain.cipher;

import java.util.Arrays;
import java.util.Optional;

public final class CounterWithCbcMac {

    private static final int BLOCK = 16;
    private static final int SHORTEST_NONCE = 7;
    private static final int LONGEST_NONCE = 13;
    private static final int LONGEST_HEADER = 0xFF00;
    private static final int LONGEST_TAG = 16;
    private static final int OCTET = 0xFF;

    private static final int STARTED = 1;
    private static final int LENGTHS_SET = 1 << 1;
    private static final int HEADER_STARTED = 1 << 2;
    private static final int HEADER_FINISHED = 1 << 3;
    private static final int FAILED = 1 << 4;

    private enum Mode { DECRYPT, ENCRYPT, STAR_DECRYPT, STAR_ENCRYPT }

    private OneBlock cipher;
    private Mode mode = Mode.DECRYPT;
    private int countOctets;
    private long messageOctets;
    private long headerOctets;
    private int tagOctets;
    private long processed;
    private final byte[] chained = new byte[BLOCK];
    private final byte[] counter = new byte[BLOCK];
    private int state;

    public static byte[] clampNonce(byte[] vector) {
        return Arrays.copyOf(vector, Math.clamp(vector.length, SHORTEST_NONCE, LONGEST_NONCE));
    }

    public void useKey(OneBlock keyed) {
        cipher = keyed;
    }

    public boolean start(boolean encrypting, byte[] nonce) {
        if (nonce.length < SHORTEST_NONCE || nonce.length > LONGEST_NONCE) {
            return false;
        }
        mode = encrypting ? Mode.ENCRYPT : Mode.DECRYPT;
        countOctets = BLOCK - 1 - nonce.length;
        Arrays.fill(counter, (byte) 0);
        counter[0] = (byte) (countOctets - 1);
        System.arraycopy(nonce, 0, counter, 1, nonce.length);
        counter[BLOCK - 1] = 1;
        System.arraycopy(nonce, 0, chained, 1, nonce.length);
        state |= STARTED;
        return chainFirstBlockWhenReady();
    }

    public void allowNoTag() {
        mode = mode == Mode.ENCRYPT ? Mode.STAR_ENCRYPT : Mode.STAR_DECRYPT;
    }

    public boolean setLengths(long header, long message, int tag) {
        if (tag < 0 || tag == 2 || tag > LONGEST_TAG || tag % 2 != 0) {
            return false;
        }
        if (header >= LONGEST_HEADER) {
            return false;
        }
        messageOctets = message;
        headerOctets = header;
        tagOctets = tag;
        processed = 0;
        state |= LENGTHS_SET;
        return chainFirstBlockWhenReady();
    }

    private boolean chainFirstBlockWhenReady() {
        if ((state & STARTED) == 0 || (state & LENGTHS_SET) == 0) {
            return true;
        }
        if (tagOctets == 0) {
            if (isStar()) {
                messageOctets = 0;
                return true;
            }
            return false;
        }
        chained[0] |= (byte) ((headerOctets > 0 ? 1 : 0) << 6);
        chained[0] |= (byte) (((tagOctets - 2) / 2) << 3);
        chained[0] |= (byte) (countOctets - 1);
        long left = messageOctets;
        for (int at = 0; at < countOctets; at++, left >>>= 8) {
            chained[BLOCK - 1 - at] = (byte) (left & OCTET);
        }
        if (left > 0) {
            state |= FAILED;
            return false;
        }
        encipherChained();
        return true;
    }

    public boolean authenticate(byte[] header, int from, int length) {
        if ((state & FAILED) != 0) {
            return false;
        }
        if (length == 0) {
            return true;
        }
        if ((state & HEADER_FINISHED) != 0) {
            return false;
        }
        if ((state & HEADER_STARTED) == 0) {
            if (length > headerOctets) {
                return false;
            }
            chained[0] ^= (byte) ((headerOctets >>> 8) & OCTET);
            chained[1] ^= (byte) (headerOctets & OCTET);
            state |= HEADER_STARTED;
        } else if (processed + length > headerOctets) {
            return false;
        }
        int at = from;
        int left = length;
        while (left > 0) {
            int offset = (int) ((processed + 2) % BLOCK);
            int use = Math.min(BLOCK - offset, left);
            for (int within = 0; within < use; within++) {
                chained[offset + within] ^= header[at + within];
            }
            processed += use;
            left -= use;
            at += use;
            if (use + offset == BLOCK || processed == headerOctets) {
                encipherChained();
            }
        }
        if (processed == headerOctets) {
            state |= HEADER_FINISHED;
            processed = 0;
        }
        return true;
    }

    public boolean update(byte[] input, int from, int length, byte[] output, int into) {
        if ((state & FAILED) != 0) {
            return false;
        }
        if (tagOctets != 0 && processed + length > messageOctets) {
            return false;
        }
        int at = 0;
        while (at < length) {
            int offset = (int) (processed % BLOCK);
            int use = Math.min(BLOCK - offset, length - at);
            processed += use;
            boolean blockEnds = use + offset == BLOCK || processed == messageOctets;
            byte[] keystream = cipher.encipher(counter.clone());
            for (int within = 0; within < use; within++) {
                byte plain = isEnciphering()
                        ? input[from + at + within]
                        : (byte) (input[from + at + within] ^ keystream[offset + within]);
                chained[offset + within] ^= plain;
                output[into + at + within] = isEnciphering()
                        ? (byte) (plain ^ keystream[offset + within])
                        : plain;
            }
            if (blockEnds) {
                encipherChained();
                countOn();
            }
            at += use;
        }
        return true;
    }

    public Optional<byte[]> finish(int tagLength) {
        if ((state & FAILED) != 0) {
            return Optional.empty();
        }
        if (headerOctets > 0 && (state & HEADER_FINISHED) == 0) {
            return Optional.empty();
        }
        if (messageOctets > 0 && processed != messageOctets) {
            return Optional.empty();
        }
        for (int at = 0; at < countOctets; at++) {
            counter[BLOCK - 1 - at] = 0;
        }
        byte[] mask = cipher.encipher(counter.clone());
        for (int at = 0; at < BLOCK; at++) {
            chained[at] ^= mask[at];
        }
        byte[] tag = Arrays.copyOf(chained, tagLength);
        clearState();
        return Optional.of(tag);
    }

    public static boolean agreeInConstantTime(byte[] written, byte[] computed) {
        if (written.length != computed.length) {
            return false;
        }
        int differences = 0;
        for (int at = 0; at < written.length; at++) {
            differences |= written[at] ^ computed[at];
        }
        return differences == 0;
    }

    private void clearState() {
        state = 0;
        Arrays.fill(chained, (byte) 0);
        Arrays.fill(counter, (byte) 0);
    }

    private void countOn() {
        for (int at = 0; at < countOctets; at++) {
            counter[BLOCK - 1 - at]++;
            if (counter[BLOCK - 1 - at] != 0) {
                return;
            }
        }
    }

    private void encipherChained() {
        byte[] next = cipher.encipher(chained.clone());
        System.arraycopy(next, 0, chained, 0, BLOCK);
    }

    private boolean isStar() {
        return mode == Mode.STAR_ENCRYPT || mode == Mode.STAR_DECRYPT;
    }

    private boolean isEnciphering() {
        return mode == Mode.ENCRYPT || mode == Mode.STAR_ENCRYPT;
    }
}
