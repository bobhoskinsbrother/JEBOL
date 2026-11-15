package org.jebol.domain.eval.compression.zlibng;

import java.util.Arrays;
import java.util.zip.Adler32;
import java.util.zip.CRC32;

/** The shape below is zlib-ng's deflate_state, converted mechanically: see docs/zlib-ng-port.md, and do not tidy it. */
final class ZlibNgDeflateState {

    static final int LENGTH_CODES = 29;
    static final int LITERALS = 256;
    static final int L_CODES = LITERALS + 1 + LENGTH_CODES;
    static final int D_CODES = 30;
    static final int BL_CODES = 19;
    static final int HEAP_SIZE = 2 * L_CODES + 1;
    static final int MAX_BITS = 15;
    static final int BIT_BUF_SIZE = 64;
    static final int END_BLOCK = 256;

    static final int INIT_STATE = 1;
    static final int BUSY_STATE = 2;
    static final int FINISH_STATE = 3;
    static final int GZIP_STATE = 4;

    static final int HASH_SIZE = 65536;
    static final int HASH_MASK = HASH_SIZE - 1;
    static final int ROLLING_HASH_MASK = 32768 - 1;
    static final int MULTIPLICATIVE_HASH = 0x9E3779B1;
    static final int MULTIPLICATIVE_HASH_SLIDE = 16;
    static final int ROLLING_HASH_SLIDE = 5;
    static final int CHAIN_ABOVE_WHICH_THE_HASH_ROLLS = 1024;

    static final int STD_MIN_MATCH = 3;
    static final int STD_MAX_MATCH = 258;
    static final int WANT_MIN_MATCH = 4;
    static final int MIN_LOOKAHEAD = STD_MAX_MATCH + STD_MIN_MATCH + 1;
    static final int WIN_INIT = STD_MAX_MATCH;
    static final int MAX_STORED = 65535;

    static final int NEED_MORE = 0;
    static final int BLOCK_DONE = 1;
    static final int FINISH_STARTED = 2;
    static final int FINISH_DONE = 3;

    final ZlibNgDeflater strm;

    byte[] pendingBuf;
    int pendingOut;
    int pendingBufSize;
    int pending;
    int wrap;
    int status;
    int lastFlush;
    int blockOpen;

    int wSize;
    int wBits;
    int wMask;
    int lookahead;
    int highWater;
    int windowSize;
    byte[] window;
    int[] prev;
    int[] head;
    int insH;
    int blockStart;

    int prevMatch;
    int matchAvailable;
    int strstart;
    int matchStart;
    int prevLength;
    int maxChainLength;
    int maxLazyMatch;
    int level;
    int strategy;
    int goodMatch;
    int niceMatch;
    boolean isRollingHash;

    final int[] dynLtreeFreqOrCode = new int[HEAP_SIZE];
    final int[] dynLtreeDadOrLen = new int[HEAP_SIZE];
    final int[] dynDtreeFreqOrCode = new int[2 * D_CODES + 1];
    final int[] dynDtreeDadOrLen = new int[2 * D_CODES + 1];
    final int[] blTreeFreqOrCode = new int[2 * BL_CODES + 1];
    final int[] blTreeDadOrLen = new int[2 * BL_CODES + 1];
    int lDescMaxCode;
    int dDescMaxCode;
    int blDescMaxCode;
    final int[] blCount = new int[MAX_BITS + 1];
    final int[] heap = new int[2 * L_CODES + 1];
    int heapLen;
    int heapMax;
    final int[] depth = new int[2 * L_CODES + 1];

    int litBufsize;
    int[] dBuf;
    int[] lBuf;
    int symNext;
    int symEnd;
    long optLen;
    long staticLen;
    int matches;
    int insert;

    long biBuf;
    int biValid;

    final Adler32 adler = new Adler32();
    final CRC32 crc = new CRC32();

    ZlibNgDeflateState(ZlibNgDeflater strm, int windowBits, int litBufsize) {
        this.strm = strm;
        this.windowSize = 2 * (1 << windowBits);
        this.window = new byte[windowSize];
        this.prev = new int[1 << windowBits];
        this.head = new int[HASH_SIZE];
        this.pendingBuf = new byte[litBufsize * 4 + Long.BYTES];
        this.dBuf = new int[litBufsize];
        this.lBuf = new int[litBufsize];
    }

    int maxDist() {
        return wSize - MIN_LOOKAHEAD;
    }

    void putByte(int c) {
        pendingBuf[pending++] = (byte) c;
    }

    void putShort(int w) {
        pendingBuf[pending] = (byte) w;
        pendingBuf[pending + 1] = (byte) (w >>> 8);
        pending += 2;
    }

    void putShortMsb(int w) {
        pendingBuf[pending] = (byte) (w >>> 8);
        pendingBuf[pending + 1] = (byte) w;
        pending += 2;
    }

    void putUint32(int dw) {
        for (int each = 0; each < Integer.BYTES; each++) {
            pendingBuf[pending + each] = (byte) (dw >>> (Byte.SIZE * each));
        }
        pending += Integer.BYTES;
    }

    void putUint32Msb(int dw) {
        for (int each = 0; each < Integer.BYTES; each++) {
            pendingBuf[pending + each] = (byte) (dw >>> (Byte.SIZE * (Integer.BYTES - 1 - each)));
        }
        pending += Integer.BYTES;
    }

    void putUint64(long lld) {
        for (int each = 0; each < Long.BYTES; each++) {
            pendingBuf[pending + each] = (byte) (lld >>> (Byte.SIZE * each));
        }
        pending += Long.BYTES;
    }

    void sendBits(long value, int length) {
        int totalBits = biValid + length;
        if (totalBits < BIT_BUF_SIZE && biValid < BIT_BUF_SIZE) {
            biBuf |= value << biValid;
            biValid = totalBits;
        } else if (biValid >= BIT_BUF_SIZE) {
            putUint64(biBuf);
            biBuf = value;
            biValid = length;
        } else {
            biBuf |= value << biValid;
            putUint64(biBuf);
            biBuf = value >>> (BIT_BUF_SIZE - biValid);
            biValid = totalBits - BIT_BUF_SIZE;
        }
    }

    void biWindup() {
        if (biValid > 56) {
            putUint64(biBuf);
        } else {
            if (biValid > 24) {
                putUint32((int) biBuf);
                biBuf >>>= 32;
                biValid -= 32;
            }
            if (biValid > 8) {
                putShort((int) biBuf);
                biBuf >>>= 16;
                biValid -= 16;
            }
            if (biValid > 0) {
                putByte((int) biBuf);
            }
        }
        biBuf = 0;
        biValid = 0;
    }

    void flushBits() {
        if (biValid >= 48) {
            putUint32((int) biBuf);
            putShort((int) (biBuf >>> 32));
            biBuf >>>= 48;
            biValid -= 48;
        } else if (biValid >= 32) {
            putUint32((int) biBuf);
            biBuf >>>= 32;
            biValid -= 32;
        }
        if (biValid >= 16) {
            putShort((int) biBuf);
            biBuf >>>= 16;
            biValid -= 16;
        }
        if (biValid >= 8) {
            putByte((int) biBuf);
            biBuf >>>= 8;
            biValid -= 8;
        }
    }

    void flushPending() {
        flushBits();
        int len = Math.min(pending, strm.availOut);
        if (len == 0) {
            return;
        }
        System.arraycopy(pendingBuf, pendingOut, strm.nextOutBuffer, strm.nextOut, len);
        strm.nextOut += len;
        pendingOut += len;
        strm.totalOut += len;
        strm.availOut -= len;
        pending -= len;
        if (pending == 0) {
            pendingOut = 0;
        }
    }

    int readBuf(byte[] buf, int at, int size) {
        int len = Math.min(strm.availIn, size);
        if (len == 0) {
            return 0;
        }
        System.arraycopy(strm.nextInBuffer, strm.nextIn, buf, at, len);
        if (wrap == 2) {
            crc.update(strm.nextInBuffer, strm.nextIn, len);
        } else if (wrap == 1) {
            adler.update(strm.nextInBuffer, strm.nextIn, len);
        }
        strm.availIn -= len;
        strm.nextIn += len;
        strm.totalIn += len;
        return len;
    }

    boolean tallyLit(int c) {
        dBuf[symNext] = 0;
        lBuf[symNext++] = c & 0xFF;
        dynLtreeFreqOrCode[c & 0xFF]++;
        return symNext == symEnd;
    }

    boolean tallyDist(int dist, int len) {
        dBuf[symNext] = dist & 0xFFFF;
        lBuf[symNext++] = len & 0xFF;
        matches++;
        int distance = dist - 1;
        dynLtreeFreqOrCode[ZlibNgTrees.LENGTH_CODE[len & 0xFF] + LITERALS + 1]++;
        dynDtreeFreqOrCode[ZlibNgTrees.distanceCode(distance)]++;
        return symNext == symEnd;
    }

    private int readFourBytes(int at) {
        return (window[at] & 0xFF)
                | ((window[at + 1] & 0xFF) << 8)
                | ((window[at + 2] & 0xFF) << 16)
                | ((window[at + 3] & 0xFF) << 24);
    }

    private int multiplicativeHashAt(int str) {
        return ((readFourBytes(str) * MULTIPLICATIVE_HASH) >>> MULTIPLICATIVE_HASH_SLIDE) & HASH_MASK;
    }

    int quickInsertStringMultiplicative(int str) {
        int hm = multiplicativeHashAt(str);
        int headAt = head[hm];
        if (headAt != str) {
            prev[str & wMask] = headAt;
            head[hm] = str & 0xFFFF;
        }
        return headAt;
    }

    void insertStringMultiplicative(int str, int count) {
        for (int idx = str, at = str; at < str + count; idx = (idx + 1) & 0xFFFF, at++) {
            int hm = multiplicativeHashAt(at);
            int headAt = head[hm];
            if (headAt != idx) {
                prev[idx & wMask] = headAt;
                head[hm] = idx;
            }
        }
    }

    static int updateHashRolling(int h, int val) {
        return ((h << ROLLING_HASH_SLIDE) ^ (val & 0xFF)) & ROLLING_HASH_MASK;
    }

    private int quickInsertStringRolling(int str) {
        insH = updateHashRolling(insH, window[str + STD_MIN_MATCH - 1]);
        int hm = insH;
        int headAt = head[hm];
        if (headAt != str) {
            prev[str & wMask] = headAt;
            head[hm] = str & 0xFFFF;
        }
        return headAt;
    }

    private void insertStringRolling(int str, int count) {
        for (int idx = str, at = str; at < str + count; idx = (idx + 1) & 0xFFFF, at++) {
            insH = updateHashRolling(insH, window[at + STD_MIN_MATCH - 1]);
            int hm = insH;
            int headAt = head[hm];
            if (headAt != idx) {
                prev[idx & wMask] = headAt;
                head[hm] = idx;
            }
        }
    }

    int quickInsertStringForLevel(int str) {
        return isRollingHash ? quickInsertStringRolling(str) : quickInsertStringMultiplicative(str);
    }

    void insertStringForLevel(int str, int count) {
        if (isRollingHash) {
            insertStringRolling(str, count);
        } else {
            insertStringMultiplicative(str, count);
        }
    }

    int updateHashForLevel(int h, int val) {
        if (isRollingHash) {
            return updateHashRolling(h, val);
        }
        return (((val & 0xFF) * MULTIPLICATIVE_HASH) >>> MULTIPLICATIVE_HASH_SLIDE) & HASH_MASK;
    }

    void slideHash() {
        int wsize = wSize & 0xFFFF;
        slideHashChain(head, HASH_SIZE, wsize);
        slideHashChain(prev, wsize, wsize);
    }

    private static void slideHashChain(int[] table, int entries, int wsize) {
        for (int i = 0; i < entries; i++) {
            int m = table[i];
            table[i] = m >= wsize ? m - wsize : 0;
        }
    }

    void clearHash() {
        Arrays.fill(head, 0);
    }

    void fillWindow() {
        int wsize = wSize;
        do {
            int more = windowSize - lookahead - strstart;
            if (strstart >= wsize + maxDist()) {
                System.arraycopy(window, wsize, window, 0, wsize);
                if (matchStart >= wsize) {
                    matchStart -= wsize;
                } else {
                    matchStart = 0;
                    prevLength = 0;
                }
                strstart -= wsize;
                blockStart -= wsize;
                if (insert > strstart) {
                    insert = strstart;
                }
                slideHash();
                more += wsize;
            }
            if (strm.availIn == 0) {
                break;
            }
            int n = readBuf(window, strstart + lookahead, more);
            lookahead += n;
            if (lookahead + insert >= STD_MIN_MATCH) {
                int str = strstart - insert;
                if (maxChainLength > CHAIN_ABOVE_WHICH_THE_HASH_ROLLS) {
                    insH = updateHashForLevel(window[str] & 0xFF, window[str + 1]);
                } else if (str >= 1) {
                    quickInsertStringForLevel(str + 2 - STD_MIN_MATCH);
                }
                int count = insert;
                if (lookahead == 1) {
                    count -= 1;
                }
                if (count > 0) {
                    insertStringForLevel(str, count);
                    insert -= count;
                }
            }
        } while (lookahead < MIN_LOOKAHEAD && strm.availIn != 0);
        if (highWater < windowSize) {
            int curr = strstart + lookahead;
            if (highWater < curr) {
                int init = Math.min(windowSize - curr, WIN_INIT);
                Arrays.fill(window, curr, curr + init, (byte) 0);
                highWater = curr + init;
            } else if (highWater < curr + WIN_INIT) {
                int init = Math.min(curr + WIN_INIT - highWater, windowSize - highWater);
                Arrays.fill(window, highWater, highWater + init, (byte) 0);
                highWater += init;
            }
        }
    }

    void flushBlockOnly(boolean last) {
        ZlibNgTrees.flushBlock(this, blockStart >= 0, blockStart, strstart - blockStart, last);
        blockStart = strstart;
        flushPending();
    }
}
