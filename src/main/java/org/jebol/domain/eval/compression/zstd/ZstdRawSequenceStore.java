package org.jebol.domain.eval.compression.zstd;

final class ZstdRawSequenceStore {

    int[] offsets;
    int[] literalLengths;
    int[] matchLengths;
    int position;
    int positionInSequence;
    int size;
    int capacity;

    ZstdRawSequenceStore(int capacity) {
        this.capacity = capacity;
        offsets = new int[capacity];
        literalLengths = new int[capacity];
        matchLengths = new int[capacity];
    }

    ZstdRawSequenceStore copy() {
        ZstdRawSequenceStore copy = new ZstdRawSequenceStore(0);
        copy.offsets = offsets;
        copy.literalLengths = literalLengths;
        copy.matchLengths = matchLengths;
        copy.position = position;
        copy.positionInSequence = positionInSequence;
        copy.size = size;
        copy.capacity = capacity;
        return copy;
    }

    void skipBytes(long count) {
        long current = (positionInSequence + count) & 0xFFFFFFFFL;
        while (current != 0 && position < size) {
            long length = (literalLengths[position] & 0xFFFFFFFFL) + (matchLengths[position] & 0xFFFFFFFFL);
            if (current >= length) {
                current -= length;
                position++;
            } else {
                positionInSequence = (int) current;
                break;
            }
        }
        if (current == 0 || position == size) {
            positionInSequence = 0;
        }
    }
}
