package org.jebol.domain.eval.hashing;

abstract class XxHashFamily {

    static final int P32_1 = 0x9E3779B1;
    static final int P32_2 = 0x85EBCA77;
    static final int P32_3 = 0xC2B2AE3D;

    static final long P64_1 = 0x9E3779B185EBCA87L;
    static final long P64_2 = 0xC2B2AE3D27D4EB4FL;
    static final long P64_3 = 0x165667B19E3779F9L;
    static final long P64_4 = 0x85EBCA77C2B2AE63L;
    static final long P64_5 = 0x27D4EB2F165667C5L;

    final EndianOctets octets = new EndianOctets();

    long avalanched64(long hash) {
        long mixed = hash ^ hash >>> 33;
        mixed *= P64_2;
        mixed ^= mixed >>> 29;
        mixed *= P64_3;
        return mixed ^ mixed >>> 32;
    }
}
