package org.jebol.domain.eval.deflate;

final class MatchCache {

    final char[] lengths;

    final char[] offsets;

    MatchCache(int capacity) {
        lengths = new char[capacity];
        offsets = new char[capacity];
    }

    void record(int at, int length, int offset) {
        lengths[at] = (char) length;
        offsets[at] = (char) offset;
    }

    void moveToTheStart(int from, int count) {
        System.arraycopy(lengths, from, lengths, 0, count);
        System.arraycopy(offsets, from, offsets, 0, count);
    }
}
