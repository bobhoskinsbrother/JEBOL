package org.jebol.domain.eval.hashing;

public final class XxHash extends XxHashFamily {

    private static final int P32_4 = 0x27D4EB2F;
    private static final int P32_5 = 0x165667B1;

    public byte[] of32MostSignificantByteFirst(byte[] message) {
        return octets.writeBigEndian(hash32(message) & 0xFFFFFFFFL, 4);
    }

    public byte[] of64MostSignificantByteFirst(byte[] message) {
        return octets.writeBigEndian(hash64(message), 8);
    }

    private int hash32(byte[] message) {
        int at = 0;
        int running;
        if (message.length >= 16) {
            int first = P32_1 + P32_2;
            int second = P32_2;
            int third = 0;
            int fourth = -P32_1;
            while (at + 16 <= message.length) {
                first = stir32(first, octets.readLittleEndianWordAt(message, at));
                second = stir32(second, octets.readLittleEndianWordAt(message, at + 4));
                third = stir32(third, octets.readLittleEndianWordAt(message, at + 8));
                fourth = stir32(fourth, octets.readLittleEndianWordAt(message, at + 12));
                at += 16;
            }
            running = Integer.rotateLeft(first, 1) + Integer.rotateLeft(second, 7)
                    + Integer.rotateLeft(third, 12) + Integer.rotateLeft(fourth, 18);
        } else {
            running = P32_5;
        }
        running += message.length;
        while (at + 4 <= message.length) {
            running = Integer.rotateLeft(running + octets.readLittleEndianWordAt(message, at) * P32_3, 17) * P32_4;
            at += 4;
        }
        while (at < message.length) {
            running = Integer.rotateLeft(
                    running + (message[at] & 0xFF) * P32_5, 11) * P32_1;
            at++;
        }
        return scramble32(running);
    }

    private int stir32(int accumulator, int word) {
        return Integer.rotateLeft(accumulator + word * P32_2, 13) * P32_1;
    }

    private int scramble32(int running) {
        int mixed = running ^ running >>> 15;
        mixed *= P32_2;
        mixed ^= mixed >>> 13;
        mixed *= P32_3;
        return mixed ^ mixed >>> 16;
    }

    private long hash64(byte[] message) {
        int at = 0;
        long running;
        if (message.length >= 32) {
            long first = P64_1 + P64_2;
            long second = P64_2;
            long third = 0;
            long fourth = -P64_1;
            while (at + 32 <= message.length) {
                first = stir64(first, octets.readLittleEndianLongAt(message, at));
                second = stir64(second, octets.readLittleEndianLongAt(message, at + 8));
                third = stir64(third, octets.readLittleEndianLongAt(message, at + 16));
                fourth = stir64(fourth, octets.readLittleEndianLongAt(message, at + 24));
                at += 32;
            }
            running = Long.rotateLeft(first, 1) + Long.rotateLeft(second, 7)
                    + Long.rotateLeft(third, 12) + Long.rotateLeft(fourth, 18);
            running = foldInAfterOneMoreStir(running, first);
            running = foldInAfterOneMoreStir(running, second);
            running = foldInAfterOneMoreStir(running, third);
            running = foldInAfterOneMoreStir(running, fourth);
        } else {
            running = P64_5;
        }
        running += message.length;
        while (at + 8 <= message.length) {
            running = Long.rotateLeft(
                    running ^ stir64(0, octets.readLittleEndianLongAt(message, at)), 27) * P64_1 + P64_4;
            at += 8;
        }
        if (at + 4 <= message.length) {
            running = Long.rotateLeft(
                    running ^ (octets.readLittleEndianWordAt(message, at) & 0xFFFFFFFFL) * P64_1, 23)
                    * P64_2 + P64_3;
            at += 4;
        }
        while (at < message.length) {
            running = Long.rotateLeft(running ^ (message[at] & 0xFFL) * P64_5, 11) * P64_1;
            at++;
        }
        return avalanche64(running);
    }

    private long stir64(long accumulator, long word) {
        return Long.rotateLeft(accumulator + word * P64_2, 31) * P64_1;
    }

    private long foldInAfterOneMoreStir(long running, long accumulator) {
        return (running ^ stir64(0, accumulator)) * P64_1 + P64_4;
    }
}
