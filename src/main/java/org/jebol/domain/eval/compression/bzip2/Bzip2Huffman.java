package org.jebol.domain.eval.compression.bzip2;

final class Bzip2Huffman {

    static final int MAX_ALPHA_SIZE = 258;
    static final int MAX_CODE_LENGTH = 23;
    private static final int WEIGHT_MASK = 0xFFFFFF00;
    private static final int DEPTH_MASK = 0xFF;

    private Bzip2Huffman() {
    }

    private static int addWeights(int first, int second) {
        return ((first & WEIGHT_MASK) + (second & WEIGHT_MASK))
                | (1 + Math.max(first & DEPTH_MASK, second & DEPTH_MASK));
    }

    private static void siftUp(int[] heap, int[] weight, int start) {
        int at = start;
        int moving = heap[at];
        while (weight[moving] < weight[heap[at >> 1]]) {
            heap[at] = heap[at >> 1];
            at >>= 1;
        }
        heap[at] = moving;
    }

    private static void siftDown(int[] heap, int[] weight, int size) {
        int at = 1;
        int moving = heap[at];
        for (;;) {
            int child = at << 1;
            if (child > size) {
                break;
            }
            if (child < size && weight[heap[child + 1]] < weight[heap[child]]) {
                child++;
            }
            if (weight[moving] < weight[heap[child]]) {
                break;
            }
            heap[at] = heap[child];
            at = child;
        }
        heap[at] = moving;
    }

    static void makeCodeLengths(byte[] lengths, int[] frequencies, int alphaSize, int longest) {
        int[] heap = new int[MAX_ALPHA_SIZE + 2];
        int[] weight = new int[MAX_ALPHA_SIZE * 2];
        int[] parent = new int[MAX_ALPHA_SIZE * 2];
        for (int i = 0; i < alphaSize; i++) {
            weight[i + 1] = (frequencies[i] == 0 ? 1 : frequencies[i]) << 8;
        }
        for (;;) {
            int nodes = alphaSize;
            int heapSize = 0;
            heap[0] = 0;
            weight[0] = 0;
            parent[0] = -2;
            for (int i = 1; i <= alphaSize; i++) {
                parent[i] = -1;
                heapSize++;
                heap[heapSize] = i;
                siftUp(heap, weight, heapSize);
            }
            while (heapSize > 1) {
                int first = heap[1];
                heap[1] = heap[heapSize];
                heapSize--;
                siftDown(heap, weight, heapSize);
                int second = heap[1];
                heap[1] = heap[heapSize];
                heapSize--;
                siftDown(heap, weight, heapSize);
                nodes++;
                parent[first] = nodes;
                parent[second] = nodes;
                weight[nodes] = addWeights(weight[first], weight[second]);
                parent[nodes] = -1;
                heapSize++;
                heap[heapSize] = nodes;
                siftUp(heap, weight, heapSize);
            }
            boolean isTooLong = false;
            for (int i = 1; i <= alphaSize; i++) {
                int depth = 0;
                int node = i;
                while (parent[node] >= 0) {
                    node = parent[node];
                    depth++;
                }
                lengths[i - 1] = (byte) depth;
                if (depth > longest) {
                    isTooLong = true;
                }
            }
            if (!isTooLong) {
                return;
            }
            for (int i = 1; i <= alphaSize; i++) {
                weight[i] = (1 + (weight[i] >> 8) / 2) << 8;
            }
        }
    }

    static void assignCodes(int[] codes, byte[] lengths, int shortest, int longest, int alphaSize) {
        int code = 0;
        for (int length = shortest; length <= longest; length++) {
            for (int i = 0; i < alphaSize; i++) {
                if ((lengths[i] & 0xFF) == length) {
                    codes[i] = code;
                    code++;
                }
            }
            code <<= 1;
        }
    }

    static void createDecodeTables(int[] limit, int[] base, int[] perm, byte[] lengths, int shortest, int longest,
                                   int alphaSize) {
        int at = 0;
        for (int length = shortest; length <= longest; length++) {
            for (int symbol = 0; symbol < alphaSize; symbol++) {
                if ((lengths[symbol] & 0xFF) == length) {
                    perm[at] = symbol;
                    at++;
                }
            }
        }
        for (int i = 0; i < MAX_CODE_LENGTH; i++) {
            base[i] = 0;
        }
        for (int symbol = 0; symbol < alphaSize; symbol++) {
            base[(lengths[symbol] & 0xFF) + 1]++;
        }
        for (int i = 1; i < MAX_CODE_LENGTH; i++) {
            base[i] += base[i - 1];
        }
        for (int i = 0; i < MAX_CODE_LENGTH; i++) {
            limit[i] = 0;
        }
        int code = 0;
        for (int length = shortest; length <= longest; length++) {
            code += base[length + 1] - base[length];
            limit[length] = code - 1;
            code <<= 1;
        }
        for (int length = shortest + 1; length <= longest; length++) {
            base[length] = ((limit[length - 1] + 1) << 1) - base[length];
        }
    }
}
