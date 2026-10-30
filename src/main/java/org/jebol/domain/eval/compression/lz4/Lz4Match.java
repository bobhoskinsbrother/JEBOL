package org.jebol.domain.eval.compression.lz4;

record Lz4Match(int length, int offset, int back) {

    static final Lz4Match NONE = new Lz4Match(0, 0, 0);

    Lz4Match withLength(int shorter) {
        return new Lz4Match(shorter, offset, back);
    }
}
