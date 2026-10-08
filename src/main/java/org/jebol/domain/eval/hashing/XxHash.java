package org.jebol.domain.eval.hashing;

public final class XxHash extends XxHashFamily {

    private static final int P32_4 = 0x27D4EB2F;
    private static final int P32_5 = 0x165667B1;

    public byte[] of32MostSignificantByteFirst(byte[] message) {
        return octets.bigEndian(hash32(message) & 0xFFFFFFFFL, 4);
    }

    public byte[] of64MostSignificantByteFirst(byte[] message) {
        return octets.bigEndian(hash64(message), 8);
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
                first = stirred32(first, octets.littleEndianWordAt(message, at));
                second = stirred32(second, octets.littleEndianWordAt(message, at + 4));
                third = stirred32(third, octets.littleEndianWordAt(message, at + 8));
                fourth = stirred32(fourth, octets.littleEndianWordAt(message, at + 12));
                at += 16;
            }
            running = Integer.rotateLeft(first, 1) + Integer.rotateLeft(second, 7)
                    + Integer.rotateLeft(third, 12) + Integer.rotateLeft(fourth, 18);
        } else {
            running = P32_5;
        }
        running += message.length;
        while (at + 4 <= message.length) {
            running = Integer.rotateLeft(running + octets.littleEndianWordAt(message, at) * P32_3, 17) * P32_4;
            at += 4;
        }
        while (at < message.length) {
            running = Integer.rotateLeft(
                    running + (message[at] & 0xFF) * P32_5, 11) * P32_1;
            at++;
        }
        return scrambled32(running);
    }

    private int stirred32(int accumulator, int word) {
        return Integer.rotateLeft(accumulator + word * P32_2, 13) * P32_1;
    }

    private int scrambled32(int running) {
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
                first = stirred64(first, octets.littleEndianLongAt(message, at));
                second = stirred64(second, octets.littleEndianLongAt(message, at + 8));
                third = stirred64(third, octets.littleEndianLongAt(message, at + 16));
                fourth = stirred64(fourth, octets.littleEndianLongAt(message, at + 24));
                at += 32;
            }
            running = Long.rotateLeft(first, 1) + Long.rotateLeft(second, 7)
                    + Long.rotateLeft(third, 12) + Long.rotateLeft(fourth, 18);
            running = foldedInAfterOneMoreStir(running, first);
            running = foldedInAfterOneMoreStir(running, second);
            running = foldedInAfterOneMoreStir(running, third);
            running = foldedInAfterOneMoreStir(running, fourth);
        } else {
            running = P64_5;
        }
        running += message.length;
        while (at + 8 <= message.length) {
            running = Long.rotateLeft(
                    running ^ stirred64(0, octets.littleEndianLongAt(message, at)), 27) * P64_1 + P64_4;
            at += 8;
        }
        if (at + 4 <= message.length) {
            running = Long.rotateLeft(
                    running ^ (octets.littleEndianWordAt(message, at) & 0xFFFFFFFFL) * P64_1, 23)
                    * P64_2 + P64_3;
            at += 4;
        }
        while (at < message.length) {
            running = Long.rotateLeft(running ^ (message[at] & 0xFFL) * P64_5, 11) * P64_1;
            at++;
        }
        return avalanched64(running);
    }

    private long stirred64(long accumulator, long word) {
        return Long.rotateLeft(accumulator + word * P64_2, 31) * P64_1;
    }

    private long foldedInAfterOneMoreStir(long running, long accumulator) {
        return (running ^ stirred64(0, accumulator)) * P64_1 + P64_4;
    }
}
