package org.jebol.domain.eval.compression.zlibng;

/** The shape below is zlib-ng's inftrees.c, converted mechanically: see docs/zlib-ng-port.md, and do not tidy it. */
final class ZlibNgInflateTable {

    static final int CODES = 0;
    static final int LENS = 1;
    static final int DISTS = 2;

    static final int ENOUGH_LENS = 1332;
    static final int ENOUGH_DISTS = 592;
    static final int ENOUGH = ENOUGH_LENS + ENOUGH_DISTS;

    static final int INVALID_CODE = 64;
    static final int END_OF_BLOCK = 32;

    private static final int MAX_BITS = 15;
    private static final int CODE_LENGTH_SYMBOLS_MATCH = 20;
    private static final int LITERAL_SYMBOLS_MATCH = 257;

    private static final int[] LBASE = {
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258, 0, 0};
    private static final int[] LEXT = {
        16, 16, 16, 16, 16, 16, 16, 16, 17, 17, 17, 17, 18, 18, 18, 18,
        19, 19, 19, 19, 20, 20, 20, 20, 21, 21, 21, 21, 16, 203, 77};
    private static final int[] DBASE = {
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145,
        8193, 12289, 16385, 24577, 0, 0};
    private static final int[] DEXT = {
        16, 16, 16, 16, 17, 17, 18, 18, 19, 19, 20, 20, 21, 21, 22, 22,
        23, 23, 24, 24, 25, 25, 26, 26, 27, 27,
        28, 28, 29, 29, 64, 64};

    final int[] op;
    final int[] bits;
    final int[] val;

    ZlibNgInflateTable(int size) {
        op = new int[size];
        bits = new int[size];
        val = new int[size];
    }

    private void put(int at, int hereOp, int hereBits, int hereVal) {
        op[at] = hereOp;
        bits[at] = hereBits;
        val[at] = hereVal;
    }

    static ZlibNgInflateTable fixedLengths() {
        int[] lens = new int[320];
        int sym = 0;
        while (sym < 144) {
            lens[sym++] = 8;
        }
        while (sym < 256) {
            lens[sym++] = 9;
        }
        while (sym < 280) {
            lens[sym++] = 7;
        }
        while (sym < 288) {
            lens[sym++] = 8;
        }
        ZlibNgInflateTable table = new ZlibNgInflateTable(1 << 9);
        table.build(LENS, lens, 0, 288, new int[] {0}, new int[] {9}, new int[288]);
        return table;
    }

    static ZlibNgInflateTable fixedDistances() {
        int[] lens = new int[32];
        java.util.Arrays.fill(lens, 5);
        ZlibNgInflateTable table = new ZlibNgInflateTable(1 << 5);
        table.build(DISTS, lens, 0, 32, new int[] {0}, new int[] {5}, new int[288]);
        return table;
    }

    int build(int type, int[] lens, int lensFrom, int codes, int[] tableAt, int[] bitsWanted, int[] work) {
        int[] count = new int[MAX_BITS + 1];
        int[] offs = new int[MAX_BITS + 1];
        for (int sym = 0; sym < codes; sym++) {
            count[lens[lensFrom + sym]]++;
        }
        int root = bitsWanted[0];
        int max;
        for (max = MAX_BITS; max >= 1; max--) {
            if (count[max] != 0) {
                break;
            }
        }
        root = Math.min(root, max);
        int table = tableAt[0];
        if (max == 0) {
            put(table, INVALID_CODE, 1, 0);
            put(table + 1, INVALID_CODE, 1, 0);
            tableAt[0] = table + 2;
            bitsWanted[0] = 1;
            return 0;
        }
        int min;
        for (min = 1; min < max; min++) {
            if (count[min] != 0) {
                break;
            }
        }
        root = Math.max(root, min);
        int left = 1;
        for (int len = 1; len <= MAX_BITS; len++) {
            left <<= 1;
            left -= count[len];
            if (left < 0) {
                return -1;
            }
        }
        if (left > 0 && (type == CODES || max != 1)) {
            return -1;
        }
        offs[1] = 0;
        for (int len = 1; len < MAX_BITS; len++) {
            offs[len + 1] = offs[len] + count[len];
        }
        for (int sym = 0; sym < codes; sym++) {
            if (lens[lensFrom + sym] != 0) {
                work[offs[lens[lensFrom + sym]]++] = sym;
            }
        }
        int[] base;
        int[] extra;
        int match;
        switch (type) {
            case CODES -> {
                base = work;
                extra = work;
                match = CODE_LENGTH_SYMBOLS_MATCH;
            }
            case LENS -> {
                base = LBASE;
                extra = LEXT;
                match = LITERAL_SYMBOLS_MATCH;
            }
            default -> {
                base = DBASE;
                extra = DEXT;
                match = 0;
            }
        }
        int huff = 0;
        int sym = 0;
        int len = min;
        int next = table;
        int curr = root;
        int drop = 0;
        int low = -1;
        int used = 1 << root;
        int mask = used - 1;
        if ((type == LENS && used > ENOUGH_LENS) || (type == DISTS && used > ENOUGH_DISTS)) {
            return 1;
        }
        for (;;) {
            int hereBits = len - drop;
            int hereOp;
            int hereVal;
            if (work[sym] >= match) {
                hereOp = extra[work[sym] - match] & 0xFF;
                hereVal = base[work[sym] - match];
            } else if (work[sym] + 1 < match) {
                hereOp = 0;
                hereVal = work[sym];
            } else {
                hereOp = END_OF_BLOCK + INVALID_CODE;
                hereVal = 0;
            }
            int incr = 1 << (len - drop);
            int fill = 1 << curr;
            min = fill;
            do {
                fill -= incr;
                put(next + (huff >>> drop) + fill, hereOp, hereBits, hereVal);
            } while (fill != 0);
            incr = 1 << (len - 1);
            while ((huff & incr) != 0) {
                incr >>>= 1;
            }
            if (incr != 0) {
                huff &= incr - 1;
                huff += incr;
            } else {
                huff = 0;
            }
            sym++;
            if (--count[len] == 0) {
                if (len == max) {
                    break;
                }
                len = lens[lensFrom + work[sym]];
            }
            if (len > root && (huff & mask) != low) {
                if (drop == 0) {
                    drop = root;
                }
                next += min;
                curr = len - drop;
                left = 1 << curr;
                while (curr + drop < max) {
                    left -= count[curr + drop];
                    if (left <= 0) {
                        break;
                    }
                    curr++;
                    left <<= 1;
                }
                used += 1 << curr;
                if ((type == LENS && used > ENOUGH_LENS) || (type == DISTS && used > ENOUGH_DISTS)) {
                    return 1;
                }
                low = huff & mask;
                put(table + low, curr, root, next - table);
            }
        }
        if (huff != 0) {
            put(next + huff, INVALID_CODE, len - drop, 0);
        }
        tableAt[0] = table + used;
        bitsWanted[0] = root;
        return 0;
    }
}
