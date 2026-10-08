package org.jebol.domain.eval.brotli;

final class BrotliMatchingBytes {

    int counted(byte[] data, int firstAt, int secondAt, int limit) {
        int matched = 0;
        while (matched < limit && data[firstAt + matched] == data[secondAt + matched]) {
            matched++;
        }
        return matched;
    }
}
