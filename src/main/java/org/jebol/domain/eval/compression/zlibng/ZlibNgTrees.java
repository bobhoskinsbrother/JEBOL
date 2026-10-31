package org.jebol.domain.eval.compression.zlibng;

import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.BL_CODES;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.D_CODES;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.END_BLOCK;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.HEAP_SIZE;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.LENGTH_CODES;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.LITERALS;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.L_CODES;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.MAX_BITS;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.STD_MAX_MATCH;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.STD_MIN_MATCH;

/** The shape below is zlib-ng's trees.c, converted mechanically: see docs/zlib-ng-port.md, and do not tidy it. */
final class ZlibNgTrees {

    static final int STORED_BLOCK = 0;
    static final int STATIC_TREES = 1;
    static final int DYN_TREES = 2;

    private static final int MAX_BL_BITS = 7;
    private static final int REP_3_6 = 16;
    private static final int REPZ_3_10 = 17;
    private static final int REPZ_11_138 = 18;
    private static final int SMALLEST = 1;
    private static final int LENGTH_SENTINEL = 0xFFFF;
    private static final int STATIC_LTREE_SIZE = L_CODES + 2;
    private static final int DIST_CODE_LEN = 512;

    private static final int[] EXTRA_LBITS =
            {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0};
    private static final int[] EXTRA_DBITS =
            {0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13};
    private static final int[] EXTRA_BLBITS = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 3, 7};
    private static final int[] BL_ORDER = {16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15};

    static final int[] LENGTH_CODE = new int[STD_MAX_MATCH - STD_MIN_MATCH + 1];
    private static final int[] DIST_CODE = new int[DIST_CODE_LEN];
    private static final int[] BASE_LENGTH = new int[LENGTH_CODES];
    private static final int[] BASE_DIST = new int[D_CODES];
    static final int[] STATIC_LTREE_CODE = new int[STATIC_LTREE_SIZE];
    static final int[] STATIC_LTREE_LEN = new int[STATIC_LTREE_SIZE];
    private static final int[] STATIC_DTREE_CODE = new int[D_CODES];
    private static final int[] STATIC_DTREE_LEN = new int[D_CODES];

    static {
        fillLengthAndDistanceCodes();
        fillStaticTrees();
    }

    private ZlibNgTrees() {
    }

    private static void fillLengthAndDistanceCodes() {
        int length = 0;
        int code;
        for (code = 0; code < LENGTH_CODES - 1; code++) {
            BASE_LENGTH[code] = length;
            for (int n = 0; n < (1 << EXTRA_LBITS[code]); n++) {
                LENGTH_CODE[length++] = code;
            }
        }
        LENGTH_CODE[length - 1] = code;
        int dist = 0;
        for (code = 0; code < 16; code++) {
            BASE_DIST[code] = dist;
            for (int n = 0; n < (1 << EXTRA_DBITS[code]); n++) {
                DIST_CODE[dist++] = code;
            }
        }
        dist >>= 7;
        for (; code < D_CODES; code++) {
            BASE_DIST[code] = dist << 7;
            for (int n = 0; n < (1 << (EXTRA_DBITS[code] - 7)); n++) {
                DIST_CODE[256 + dist++] = code;
            }
        }
    }

    private static void fillStaticTrees() {
        int[] blCount = new int[MAX_BITS + 1];
        int n = 0;
        while (n <= 143) {
            STATIC_LTREE_LEN[n++] = 8;
            blCount[8]++;
        }
        while (n <= 255) {
            STATIC_LTREE_LEN[n++] = 9;
            blCount[9]++;
        }
        while (n <= 279) {
            STATIC_LTREE_LEN[n++] = 7;
            blCount[7]++;
        }
        while (n <= 287) {
            STATIC_LTREE_LEN[n++] = 8;
            blCount[8]++;
        }
        genCodes(STATIC_LTREE_CODE, STATIC_LTREE_LEN, L_CODES + 1, blCount);
        for (n = 0; n < D_CODES; n++) {
            STATIC_DTREE_LEN[n] = 5;
            STATIC_DTREE_CODE[n] = biReverse(n, 5);
        }
    }

    static int distanceCode(int dist) {
        return dist < 256 ? DIST_CODE[dist] : DIST_CODE[256 + (dist >>> 7)];
    }

    static int biReverse(int code, int len) {
        return Integer.reverse(code) >>> (Integer.SIZE - len);
    }

    static void trInit(ZlibNgDeflateState s) {
        s.biBuf = 0;
        s.biValid = 0;
        initBlock(s);
    }

    private static void initBlock(ZlibNgDeflateState s) {
        for (int n = 0; n < L_CODES; n++) {
            s.dynLtreeFreqOrCode[n] = 0;
        }
        for (int n = 0; n < D_CODES; n++) {
            s.dynDtreeFreqOrCode[n] = 0;
        }
        for (int n = 0; n < BL_CODES; n++) {
            s.blTreeFreqOrCode[n] = 0;
        }
        s.dynLtreeFreqOrCode[END_BLOCK] = 1;
        s.optLen = 0;
        s.staticLen = 0;
        s.symNext = 0;
        s.matches = 0;
    }

    private static boolean smaller(int[] freq, int n, int m, int[] depth) {
        return freq[n] < freq[m] || (freq[n] == freq[m] && depth[n] <= depth[m]);
    }

    private static void pqdownheap(int[] depth, int[] heap, int heapLen, int[] freq, int k) {
        int j = k << 1;
        int v = heap[k];
        while (j <= heapLen) {
            if (j < heapLen && smaller(freq, heap[j + 1], heap[j], depth)) {
                j++;
            }
            if (smaller(freq, v, heap[j], depth)) {
                break;
            }
            heap[k] = heap[j];
            k = j;
            j <<= 1;
        }
        heap[k] = v;
    }

    private record TreeDescription(int[] staticLengths, int[] extraBits, int extraBase, int elems, int maxLength) {
    }

    private static final TreeDescription LITERAL_DESCRIPTION =
            new TreeDescription(STATIC_LTREE_LEN, EXTRA_LBITS, LITERALS + 1, L_CODES, MAX_BITS);
    private static final TreeDescription DISTANCE_DESCRIPTION =
            new TreeDescription(STATIC_DTREE_LEN, EXTRA_DBITS, 0, D_CODES, MAX_BITS);
    private static final TreeDescription BIT_LENGTH_DESCRIPTION =
            new TreeDescription(null, EXTRA_BLBITS, 0, BL_CODES, MAX_BL_BITS);

    private static int buildTree(ZlibNgDeflateState s, int[] freqOrCode, int[] dadOrLen, TreeDescription desc) {
        int[] depth = s.depth;
        int[] heap = s.heap;
        int[] stree = desc.staticLengths();
        int elems = desc.elems();
        int maxCode = -1;
        int node;
        s.heapLen = 0;
        s.heapMax = HEAP_SIZE;
        for (int n = 0; n < elems; n++) {
            if (freqOrCode[n] != 0) {
                heap[++s.heapLen] = maxCode = n;
                depth[n] = 0;
            } else {
                dadOrLen[n] = 0;
            }
        }
        while (s.heapLen < 2) {
            node = heap[++s.heapLen] = (maxCode < 2 ? ++maxCode : 0);
            freqOrCode[node] = 1;
            depth[node] = 0;
            s.optLen--;
            if (stree != null) {
                s.staticLen -= stree[node];
            }
        }
        for (int n = s.heapLen / 2; n >= 1; n--) {
            pqdownheap(depth, heap, s.heapLen, freqOrCode, n);
        }
        node = elems;
        do {
            int n = heap[SMALLEST];
            heap[SMALLEST] = heap[s.heapLen--];
            pqdownheap(depth, heap, s.heapLen, freqOrCode, SMALLEST);
            int m = heap[SMALLEST];
            heap[--s.heapMax] = n;
            heap[--s.heapMax] = m;
            freqOrCode[node] = (freqOrCode[n] + freqOrCode[m]) & 0xFFFF;
            depth[node] = ((depth[n] >= depth[m] ? depth[n] : depth[m]) + 1) & 0xFF;
            dadOrLen[n] = dadOrLen[m] = node;
            heap[SMALLEST] = node++;
            pqdownheap(depth, heap, s.heapLen, freqOrCode, SMALLEST);
        } while (s.heapLen >= 2);
        heap[--s.heapMax] = heap[SMALLEST];
        genBitlen(s, freqOrCode, dadOrLen, maxCode, desc);
        genCodes(freqOrCode, dadOrLen, maxCode, s.blCount);
        return maxCode;
    }

    private static void genBitlen(ZlibNgDeflateState s, int[] freqOrCode, int[] dadOrLen, int maxCode,
                                  TreeDescription desc) {
        int[] stree = desc.staticLengths();
        int[] extra = desc.extraBits();
        int base = desc.extraBase();
        int maxLength = desc.maxLength();
        int h;
        int overflow = 0;
        for (int bits = 0; bits <= MAX_BITS; bits++) {
            s.blCount[bits] = 0;
        }
        dadOrLen[s.heap[s.heapMax]] = 0;
        for (h = s.heapMax + 1; h < HEAP_SIZE; h++) {
            int n = s.heap[h];
            int bits = dadOrLen[dadOrLen[n]] + 1;
            if (bits > maxLength) {
                bits = maxLength;
                overflow++;
            }
            dadOrLen[n] = bits;
            if (n > maxCode) {
                continue;
            }
            s.blCount[bits]++;
            int xbits = 0;
            if (n >= base) {
                xbits = extra[n - base];
            }
            int f = freqOrCode[n];
            s.optLen += (long) f * (bits + xbits);
            if (stree != null) {
                s.staticLen += (long) f * (stree[n] + xbits);
            }
        }
        if (overflow == 0) {
            return;
        }
        do {
            int bits = maxLength - 1;
            while (s.blCount[bits] == 0) {
                bits--;
            }
            s.blCount[bits]--;
            s.blCount[bits + 1] += 2;
            s.blCount[maxLength]--;
            overflow -= 2;
        } while (overflow > 0);
        for (int bits = maxLength; bits != 0; bits--) {
            int n = s.blCount[bits];
            while (n != 0) {
                int m = s.heap[--h];
                if (m > maxCode) {
                    continue;
                }
                if (dadOrLen[m] != bits) {
                    s.optLen += Integer.toUnsignedLong(bits * freqOrCode[m]);
                    s.optLen -= Integer.toUnsignedLong(dadOrLen[m] * freqOrCode[m]);
                    dadOrLen[m] = bits;
                }
                n--;
            }
        }
    }

    private static void genCodes(int[] code, int[] len, int maxCode, int[] blCount) {
        int[] nextCode = new int[MAX_BITS + 1];
        int running = 0;
        for (int bits = 1; bits <= MAX_BITS; bits++) {
            running = (running + blCount[bits - 1]) << 1;
            nextCode[bits] = running & 0xFFFF;
        }
        for (int n = 0; n <= maxCode; n++) {
            int length = len[n];
            if (length == 0) {
                continue;
            }
            code[n] = biReverse(nextCode[length]++, length);
        }
    }

    private static void scanTree(ZlibNgDeflateState s, int[] dadOrLen, int maxCode) {
        int prevlen = -1;
        int nextlen = dadOrLen[0];
        int count = 0;
        int maxCount = 7;
        int minCount = 4;
        if (nextlen == 0) {
            maxCount = 138;
            minCount = 3;
        }
        dadOrLen[maxCode + 1] = LENGTH_SENTINEL;
        for (int n = 0; n <= maxCode; n++) {
            int curlen = nextlen;
            nextlen = dadOrLen[n + 1];
            if (++count < maxCount && curlen == nextlen) {
                continue;
            } else if (count < minCount) {
                s.blTreeFreqOrCode[curlen] += count;
            } else if (curlen != 0) {
                if (curlen != prevlen) {
                    s.blTreeFreqOrCode[curlen]++;
                }
                s.blTreeFreqOrCode[REP_3_6]++;
            } else if (count <= 10) {
                s.blTreeFreqOrCode[REPZ_3_10]++;
            } else {
                s.blTreeFreqOrCode[REPZ_11_138]++;
            }
            count = 0;
            prevlen = curlen;
            if (nextlen == 0) {
                maxCount = 138;
                minCount = 3;
            } else if (curlen == nextlen) {
                maxCount = 6;
                minCount = 3;
            } else {
                maxCount = 7;
                minCount = 4;
            }
        }
    }

    private static void sendCode(ZlibNgDeflateState s, int c, int[] code, int[] len) {
        s.sendBits(code[c], len[c]);
    }

    private static void sendTree(ZlibNgDeflateState s, int[] dadOrLen, int maxCode) {
        int prevlen = -1;
        int nextlen = dadOrLen[0];
        int count = 0;
        int maxCount = 7;
        int minCount = 4;
        if (nextlen == 0) {
            maxCount = 138;
            minCount = 3;
        }
        for (int n = 0; n <= maxCode; n++) {
            int curlen = nextlen;
            nextlen = dadOrLen[n + 1];
            if (++count < maxCount && curlen == nextlen) {
                continue;
            } else if (count < minCount) {
                do {
                    sendCode(s, curlen, s.blTreeFreqOrCode, s.blTreeDadOrLen);
                } while (--count != 0);
            } else if (curlen != 0) {
                if (curlen != prevlen) {
                    sendCode(s, curlen, s.blTreeFreqOrCode, s.blTreeDadOrLen);
                    count--;
                }
                sendCode(s, REP_3_6, s.blTreeFreqOrCode, s.blTreeDadOrLen);
                s.sendBits(count - 3, 2);
            } else if (count <= 10) {
                sendCode(s, REPZ_3_10, s.blTreeFreqOrCode, s.blTreeDadOrLen);
                s.sendBits(count - 3, 3);
            } else {
                sendCode(s, REPZ_11_138, s.blTreeFreqOrCode, s.blTreeDadOrLen);
                s.sendBits(count - 11, 7);
            }
            count = 0;
            prevlen = curlen;
            if (nextlen == 0) {
                maxCount = 138;
                minCount = 3;
            } else if (curlen == nextlen) {
                maxCount = 6;
                minCount = 3;
            } else {
                maxCount = 7;
                minCount = 4;
            }
        }
    }

    private static int buildBlTree(ZlibNgDeflateState s) {
        scanTree(s, s.dynLtreeDadOrLen, s.lDescMaxCode);
        scanTree(s, s.dynDtreeDadOrLen, s.dDescMaxCode);
        s.blDescMaxCode = buildTree(s, s.blTreeFreqOrCode, s.blTreeDadOrLen, BIT_LENGTH_DESCRIPTION);
        int maxBlindex;
        for (maxBlindex = BL_CODES - 1; maxBlindex >= 3; maxBlindex--) {
            if (s.blTreeDadOrLen[BL_ORDER[maxBlindex]] != 0) {
                break;
            }
        }
        s.optLen += 3 * ((long) maxBlindex + 1) + 5 + 5 + 4;
        return maxBlindex;
    }

    private static void sendAllTrees(ZlibNgDeflateState s, int lcodes, int dcodes, int blcodes) {
        s.sendBits(lcodes - 257, 5);
        s.sendBits(dcodes - 1, 5);
        s.sendBits(blcodes - 4, 4);
        for (int rank = 0; rank < blcodes; rank++) {
            s.sendBits(s.blTreeDadOrLen[BL_ORDER[rank]], 3);
        }
        sendTree(s, s.dynLtreeDadOrLen, lcodes - 1);
        sendTree(s, s.dynDtreeDadOrLen, dcodes - 1);
    }

    static void emitTree(ZlibNgDeflateState s, int type, boolean last) {
        s.sendBits((type << 1) + (last ? 1 : 0), 3);
    }

    static void emitLit(ZlibNgDeflateState s, int[] ltreeCode, int[] ltreeLen, int c) {
        s.sendBits(ltreeCode[c], ltreeLen[c]);
    }

    static void emitDist(ZlibNgDeflateState s, int[] ltreeCode, int[] ltreeLen, int[] dtreeCode, int[] dtreeLen,
                         int lc, int dist) {
        int code = LENGTH_CODE[lc];
        int c = code + LITERALS + 1;
        long matchBits = ltreeCode[c];
        int matchBitsLen = ltreeLen[c];
        int extra = EXTRA_LBITS[code];
        if (extra != 0) {
            lc -= BASE_LENGTH[code];
            matchBits |= ((long) lc << matchBitsLen);
            matchBitsLen += extra;
        }
        dist--;
        code = distanceCode(dist);
        matchBits |= ((long) dtreeCode[code] << matchBitsLen);
        matchBitsLen += dtreeLen[code];
        extra = EXTRA_DBITS[code];
        if (extra != 0) {
            dist -= BASE_DIST[code];
            matchBits |= ((long) dist << matchBitsLen);
            matchBitsLen += extra;
        }
        s.sendBits(matchBits, matchBitsLen);
    }

    static void emitStaticLit(ZlibNgDeflateState s, int c) {
        emitLit(s, STATIC_LTREE_CODE, STATIC_LTREE_LEN, c);
    }

    static void emitStaticDist(ZlibNgDeflateState s, int lc, int dist) {
        emitDist(s, STATIC_LTREE_CODE, STATIC_LTREE_LEN, STATIC_DTREE_CODE, STATIC_DTREE_LEN, lc, dist);
    }

    static void emitEndBlock(ZlibNgDeflateState s, int[] ltreeCode, int[] ltreeLen) {
        s.sendBits(ltreeCode[END_BLOCK], ltreeLen[END_BLOCK]);
    }

    static void emitStaticEndBlockAndAlignIfLast(ZlibNgDeflateState s, boolean last) {
        emitEndBlock(s, STATIC_LTREE_CODE, STATIC_LTREE_LEN);
        if (last) {
            s.biWindup();
        }
    }

    static void storedBlock(ZlibNgDeflateState s, byte[] buf, int at, int storedLen, boolean last) {
        emitTree(s, STORED_BLOCK, last);
        s.biWindup();
        s.putShort(storedLen);
        s.putShort(~storedLen);
        if (storedLen != 0) {
            System.arraycopy(buf, at, s.pendingBuf, s.pending, storedLen);
            s.pending += storedLen;
        }
    }

    static void align(ZlibNgDeflateState s) {
        emitTree(s, STATIC_TREES, false);
        emitStaticEndBlockAndAlignIfLast(s, false);
        s.flushBits();
    }

    static void flushBlock(ZlibNgDeflateState s, boolean hasBuf, int bufAt, int storedLen, boolean last) {
        long optLenb;
        long staticLenb;
        int maxBlindex = 0;
        if (s.symNext == 0) {
            optLenb = 0;
            staticLenb = 0;
            s.staticLen = 7;
        } else if (s.level > 0) {
            s.lDescMaxCode = buildTree(s, s.dynLtreeFreqOrCode, s.dynLtreeDadOrLen, LITERAL_DESCRIPTION);
            s.dDescMaxCode = buildTree(s, s.dynDtreeFreqOrCode, s.dynDtreeDadOrLen, DISTANCE_DESCRIPTION);
            maxBlindex = buildBlTree(s);
            optLenb = (s.optLen + 3 + 7) >>> 3;
            staticLenb = (s.staticLen + 3 + 7) >>> 3;
            if (Long.compareUnsigned(staticLenb, optLenb) <= 0 || s.strategy == ZlibNgDeflater.Z_FIXED) {
                optLenb = staticLenb;
            }
        } else {
            optLenb = Integer.toUnsignedLong(storedLen) + 5;
            staticLenb = optLenb;
        }
        if (Long.compareUnsigned(Integer.toUnsignedLong(storedLen + 4), optLenb) <= 0 && hasBuf) {
            storedBlock(s, s.window, bufAt, storedLen, last);
        } else if (staticLenb == optLenb) {
            emitTree(s, STATIC_TREES, last);
            compressBlock(s, STATIC_LTREE_CODE, STATIC_LTREE_LEN, STATIC_DTREE_CODE, STATIC_DTREE_LEN);
        } else {
            emitTree(s, DYN_TREES, last);
            sendAllTrees(s, s.lDescMaxCode + 1, s.dDescMaxCode + 1, maxBlindex + 1);
            compressBlock(s, s.dynLtreeFreqOrCode, s.dynLtreeDadOrLen, s.dynDtreeFreqOrCode, s.dynDtreeDadOrLen);
        }
        initBlock(s);
        if (last) {
            s.biWindup();
        }
    }

    private static void compressBlock(ZlibNgDeflateState s, int[] ltreeCode, int[] ltreeLen,
                                      int[] dtreeCode, int[] dtreeLen) {
        int symNext = s.symNext;
        int sx = 0;
        if (symNext != 0) {
            do {
                int dist = s.dBuf[sx];
                int lc = s.lBuf[sx++];
                if (dist == 0) {
                    emitLit(s, ltreeCode, ltreeLen, lc);
                } else {
                    emitDist(s, ltreeCode, ltreeLen, dtreeCode, dtreeLen, lc, dist);
                }
            } while (sx < symNext);
        }
        emitEndBlock(s, ltreeCode, ltreeLen);
    }
}
