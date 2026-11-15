package org.jebol.domain.eval.compression.zlibng;

import java.util.Optional;
import java.util.zip.Adler32;
import java.util.zip.CRC32;

import static org.jebol.domain.eval.compression.zlibng.ZlibNgInflateTable.CODES;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgInflateTable.DISTS;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgInflateTable.END_OF_BLOCK;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgInflateTable.INVALID_CODE;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgInflateTable.LENS;

/**
 * A port of zlib-ng 2.3.2's inflate, answering its result codes exactly: what it decodes, where it stops,
 * and which damage it calls {@code Z_DATA_ERROR}. It is driven as zlib's {@code z_stream} is.
 */
public final class ZlibNgInflater {

    public static final int Z_NO_FLUSH = 0;
    public static final int Z_SYNC_FLUSH = 2;
    public static final int Z_FINISH = 4;

    public static final int Z_OK = 0;
    public static final int Z_STREAM_END = 1;
    public static final int Z_NEED_DICT = 2;
    public static final int Z_STREAM_ERROR = -2;
    public static final int Z_DATA_ERROR = -3;
    public static final int Z_BUF_ERROR = -5;

    private static final int MAX_WBITS = 15;
    private static final int MIN_WBITS = 8;
    private static final int Z_DEFLATED = 8;
    private static final int GZIP_MAGIC = 0x8b1f;
    private static final int GZIP_HEADER_CRC = 0x0200;
    private static final int GZIP_EXTRA = 0x0400;
    private static final int GZIP_NAME = 0x0800;
    private static final int GZIP_COMMENT = 0x1000;
    private static final int GZIP_RESERVED_FLAGS = 0xe000;
    private static final int ZLIB_PRESET_DICTIONARY = 0x200;
    private static final int WRAP_ZLIB = 1;
    private static final int WRAP_GZIP = 2;
    private static final int WRAP_CHECKED = 4;
    private static final int TOO_MANY_LENGTHS = 286;
    private static final int TOO_MANY_DISTANCES = 30;
    private static final int[] ORDER = {16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15};

    private static final ZlibNgInflateTable LENFIX = ZlibNgInflateTable.fixedLengths();
    private static final ZlibNgInflateTable DISTFIX = ZlibNgInflateTable.fixedDistances();

    private enum Mode {
        HEAD, FLAGS, TIME, OS, EXLEN, EXTRA, NAME, COMMENT, HCRC, DICTID, DICT,
        TYPE, TYPEDO, STORED, COPY, TABLE, LENLENS, CODELENS, LEN, LENEXT, DIST, DISTEXT, MATCH, LIT,
        CHECK, LENGTH, DONE, BAD
    }

    private byte[] nextInBuffer;
    private int nextIn;
    private int availIn;
    private long totalIn;
    private byte[] nextOutBuffer;
    private int nextOut;
    private int availOut;
    private long totalOut;

    private Mode mode;
    private boolean last;
    private int wrap;
    private int flags;
    private long check;
    private long total;
    private int wbits;
    private int wsize;
    private int whave;
    private int wnext;
    private final byte[] window = new byte[1 << MAX_WBITS];
    private long hold;
    private int bits;
    private ZlibNgInflateTable lencode;
    private int lencodeAt;
    private int lenbits;
    private ZlibNgInflateTable distcode;
    private int distcodeAt;
    private int distbits;
    private int length;
    private int offset;
    private int extra;
    private int ncode;
    private int nlen;
    private int ndist;
    private int have;
    private final int[] lens = new int[320];
    private final int[] work = new int[288];
    private final ZlibNgInflateTable codes = new ZlibNgInflateTable(ZlibNgInflateTable.ENOUGH);
    private final CRC32 crc = new CRC32();
    private final Adler32 adler = new Adler32();
    private final CRC32 headerCrc = new CRC32();

    private int put;
    private int left;
    private int next;
    private int haveIn;

    private ZlibNgInflater() {
    }

    /**
     * Opens a stream as {@code inflateInit2} does, or answers nothing where it answers {@code Z_STREAM_ERROR}.
     * A negative window is raw deflate, 8 to 15 is zlib, 16 more is gzip, and 32 more is either.
     */
    public static Optional<ZlibNgInflater> open(int windowBits) {
        ZlibNgInflater inflater = new ZlibNgInflater();
        int window = windowBits;
        int wrapAsked;
        if (window < 0) {
            wrapAsked = 0;
            if (window < -MAX_WBITS) {
                return Optional.empty();
            }
            window = -window;
        } else {
            wrapAsked = (window >> 4) + 5;
            if (window < 48) {
                window &= MAX_WBITS;
            }
        }
        if (window != 0 && (window < MIN_WBITS || window > MAX_WBITS)) {
            return Optional.empty();
        }
        inflater.wrap = wrapAsked;
        inflater.wbits = window;
        inflater.reset();
        return Optional.of(inflater);
    }

    /** Gives the stream its next input, as setting {@code next_in} and {@code avail_in} does. */
    public void input(byte[] buffer, int from, int howMany) {
        nextInBuffer = buffer;
        nextIn = from;
        availIn = howMany;
    }

    /** Gives the stream room to write, as setting {@code next_out} and {@code avail_out} does. */
    public void output(byte[] buffer, int from, int howMany) {
        nextOutBuffer = buffer;
        nextOut = from;
        availOut = howMany;
    }

    public int availIn() {
        return availIn;
    }

    public int availOut() {
        return availOut;
    }

    public long totalIn() {
        return totalIn;
    }

    public long totalOut() {
        return totalOut;
    }

    /** Sets {@code total_out} to nought without touching the stream, as a caller that rewinds its buffer does. */
    public void forgetTotalOut() {
        totalOut = 0;
    }

    /** Starts the stream again with the same window and wrapping, as {@code inflateReset} does. */
    public void reset() {
        wsize = 0;
        whave = 0;
        wnext = 0;
        totalIn = 0;
        totalOut = 0;
        total = 0;
        mode = Mode.HEAD;
        check = 1;
        last = false;
        flags = -1;
        hold = 0;
        bits = 0;
        lencode = codes;
        lencodeAt = 0;
        distcode = codes;
        distcodeAt = 0;
        crc.reset();
        adler.reset();
    }

    private boolean pullByte() {
        if (haveIn == 0) {
            return false;
        }
        haveIn--;
        hold += (long) (nextInBuffer[next++] & 0xFF) << bits;
        bits += 8;
        return true;
    }

    private boolean needBits(int n) {
        while (bits < n) {
            if (!pullByte()) {
                return false;
            }
        }
        return true;
    }

    private int bitsOf(int n) {
        return (int) (hold & ((1L << n) - 1));
    }

    private void dropBits(int n) {
        hold >>>= n;
        bits -= n;
    }

    private void initBits() {
        hold = 0;
        bits = 0;
    }

    private void byteBits() {
        hold >>>= bits & 7;
        bits -= bits & 7;
    }

    private void crcOfHeader(long word, int bytes) {
        for (int each = 0; each < bytes; each++) {
            headerCrc.update((int) (word >>> (Byte.SIZE * each)) & 0xFF);
        }
    }

    private void checksum(int from, int howMany) {
        if (howMany == 0) {
            return;
        }
        if (flags != 0) {
            crc.update(nextOutBuffer, from, howMany);
        } else {
            adler.update(nextOutBuffer, from, howMany);
        }
    }

    private void updateWindow(int end, int len) {
        if (wsize == 0) {
            wsize = 1 << wbits;
        }
        if (len >= wsize) {
            System.arraycopy(nextOutBuffer, end - wsize, window, 0, wsize);
            wnext = 0;
            whave = wsize;
        } else {
            int dist = Math.min(wsize - wnext, len);
            System.arraycopy(nextOutBuffer, end - len, window, wnext, dist);
            len -= dist;
            if (len != 0) {
                System.arraycopy(nextOutBuffer, end - len, window, 0, len);
                wnext = len;
                whave = wsize;
            } else {
                wnext += dist;
                if (wnext == wsize) {
                    wnext = 0;
                }
                if (whave < wsize) {
                    whave += dist;
                }
            }
        }
    }

    private void fixedTables() {
        lencode = LENFIX;
        lencodeAt = 0;
        lenbits = 9;
        distcode = DISTFIX;
        distcodeAt = 0;
        distbits = 5;
    }

    /** Decompresses as much as it can, as {@code inflate} does, and answers its result code. */
    public int inflate(int flush) {
        if (nextOutBuffer == null || (nextInBuffer == null && availIn != 0)) {
            return Z_STREAM_ERROR;
        }
        put = nextOut;
        left = availOut;
        next = nextIn;
        haveIn = availIn;
        int in = haveIn;
        int out = left;
        int committed = put;
        int ret = Z_OK;
        leave:
        for (;;) {
            switch (mode) {
                case HEAD -> {
                    if (wrap == 0) {
                        mode = Mode.TYPEDO;
                        continue;
                    }
                    if (!needBits(16)) {
                        break leave;
                    }
                    if ((wrap & WRAP_GZIP) != 0 && hold == GZIP_MAGIC) {
                        if (wbits == 0) {
                            wbits = MAX_WBITS;
                        }
                        headerCrc.reset();
                        crcOfHeader(hold, 2);
                        initBits();
                        mode = Mode.FLAGS;
                        continue;
                    }
                    if ((wrap & WRAP_ZLIB) == 0 || (((long) bitsOf(8) << 8) + (hold >>> 8)) % 31 != 0) {
                        mode = Mode.BAD;
                        continue;
                    }
                    if (bitsOf(4) != Z_DEFLATED) {
                        mode = Mode.BAD;
                        continue;
                    }
                    dropBits(4);
                    int len = bitsOf(4) + 8;
                    if (wbits == 0) {
                        wbits = len;
                    }
                    if (len > MAX_WBITS || len > wbits) {
                        mode = Mode.BAD;
                        continue;
                    }
                    flags = 0;
                    adler.reset();
                    mode = (hold & ZLIB_PRESET_DICTIONARY) != 0 ? Mode.DICTID : Mode.TYPE;
                    initBits();
                }
                case FLAGS -> {
                    if (!needBits(16)) {
                        break leave;
                    }
                    flags = (int) hold;
                    if ((flags & 0xff) != Z_DEFLATED) {
                        mode = Mode.BAD;
                        continue;
                    }
                    if ((flags & GZIP_RESERVED_FLAGS) != 0) {
                        mode = Mode.BAD;
                        continue;
                    }
                    if (isHeaderChecked()) {
                        crcOfHeader(hold, 2);
                    }
                    initBits();
                    mode = Mode.TIME;
                }
                case TIME -> {
                    if (!needBits(32)) {
                        break leave;
                    }
                    if (isHeaderChecked()) {
                        crcOfHeader(hold, 4);
                    }
                    initBits();
                    mode = Mode.OS;
                }
                case OS -> {
                    if (!needBits(16)) {
                        break leave;
                    }
                    if (isHeaderChecked()) {
                        crcOfHeader(hold, 2);
                    }
                    initBits();
                    mode = Mode.EXLEN;
                }
                case EXLEN -> {
                    if ((flags & GZIP_EXTRA) != 0) {
                        if (!needBits(16)) {
                            break leave;
                        }
                        length = (int) (hold & 0xFFFF);
                        if (isHeaderChecked()) {
                            crcOfHeader(hold, 2);
                        }
                        initBits();
                    }
                    mode = Mode.EXTRA;
                }
                case EXTRA -> {
                    if ((flags & GZIP_EXTRA) != 0) {
                        int copy = Math.min(length, haveIn);
                        if (copy != 0) {
                            if (isHeaderChecked()) {
                                headerCrc.update(nextInBuffer, next, copy);
                            }
                            haveIn -= copy;
                            next += copy;
                            length -= copy;
                        }
                        if (length != 0) {
                            break leave;
                        }
                    }
                    length = 0;
                    mode = Mode.NAME;
                }
                case NAME -> {
                    if ((flags & GZIP_NAME) != 0) {
                        if (!skipZeroEnded()) {
                            break leave;
                        }
                    }
                    length = 0;
                    mode = Mode.COMMENT;
                }
                case COMMENT -> {
                    if ((flags & GZIP_COMMENT) != 0) {
                        if (!skipZeroEnded()) {
                            break leave;
                        }
                    }
                    mode = Mode.HCRC;
                }
                case HCRC -> {
                    if ((flags & GZIP_HEADER_CRC) != 0) {
                        if (!needBits(16)) {
                            break leave;
                        }
                        if ((wrap & WRAP_CHECKED) != 0 && hold != (headerCrc.getValue() & 0xffff)) {
                            mode = Mode.BAD;
                            continue;
                        }
                        initBits();
                    }
                    crc.reset();
                    mode = Mode.TYPE;
                }
                case DICTID -> {
                    if (!needBits(32)) {
                        break leave;
                    }
                    initBits();
                    mode = Mode.DICT;
                }
                case DICT -> {
                    restore();
                    return Z_NEED_DICT;
                }
                case TYPE, TYPEDO -> {
                    if (last) {
                        byteBits();
                        mode = Mode.CHECK;
                        continue;
                    }
                    if (!needBits(3)) {
                        break leave;
                    }
                    last = bitsOf(1) != 0;
                    dropBits(1);
                    switch (bitsOf(2)) {
                        case 0 -> mode = Mode.STORED;
                        case 1 -> {
                            fixedTables();
                            mode = Mode.LEN;
                        }
                        case 2 -> mode = Mode.TABLE;
                        default -> mode = Mode.BAD;
                    }
                    dropBits(2);
                }
                case STORED -> {
                    byteBits();
                    if (!needBits(32)) {
                        break leave;
                    }
                    if ((hold & 0xffff) != ((hold >>> 16) ^ 0xffff)) {
                        mode = Mode.BAD;
                        continue;
                    }
                    length = (int) (hold & 0xFFFF);
                    initBits();
                    mode = Mode.COPY;
                }
                case COPY -> {
                    int copy = length;
                    if (copy != 0) {
                        copy = Math.min(copy, haveIn);
                        copy = Math.min(copy, left);
                        if (copy == 0) {
                            break leave;
                        }
                        System.arraycopy(nextInBuffer, next, nextOutBuffer, put, copy);
                        haveIn -= copy;
                        next += copy;
                        left -= copy;
                        put += copy;
                        length -= copy;
                        continue;
                    }
                    mode = Mode.TYPE;
                }
                case TABLE -> {
                    if (!needBits(14)) {
                        break leave;
                    }
                    nlen = bitsOf(5) + 257;
                    dropBits(5);
                    ndist = bitsOf(5) + 1;
                    dropBits(5);
                    ncode = bitsOf(4) + 4;
                    dropBits(4);
                    if (nlen > TOO_MANY_LENGTHS || ndist > TOO_MANY_DISTANCES) {
                        mode = Mode.BAD;
                        continue;
                    }
                    have = 0;
                    mode = Mode.LENLENS;
                }
                case LENLENS -> {
                    while (have < ncode) {
                        if (!needBits(3)) {
                            break leave;
                        }
                        lens[ORDER[have++]] = bitsOf(3);
                        dropBits(3);
                    }
                    while (have < 19) {
                        lens[ORDER[have++]] = 0;
                    }
                    int[] tableAt = {0};
                    int[] bitsWanted = {7};
                    lencode = codes;
                    lencodeAt = 0;
                    int built = codes.build(CODES, lens, 0, 19, tableAt, bitsWanted, work);
                    lenbits = bitsWanted[0];
                    if (built != 0) {
                        mode = Mode.BAD;
                        continue;
                    }
                    have = 0;
                    mode = Mode.CODELENS;
                }
                case CODELENS -> {
                    boolean isBad = false;
                    while (have < nlen + ndist) {
                        int at;
                        for (;;) {
                            at = lencodeAt + bitsOf(lenbits);
                            if (lencode.bits[at] <= bits) {
                                break;
                            }
                            if (!pullByte()) {
                                break leave;
                            }
                        }
                        int hereBits = lencode.bits[at];
                        int hereVal = lencode.val[at];
                        if (hereVal < 16) {
                            dropBits(hereBits);
                            lens[have++] = hereVal;
                        } else {
                            int len;
                            int copy;
                            if (hereVal == 16) {
                                if (!needBits(hereBits + 2)) {
                                    break leave;
                                }
                                dropBits(hereBits);
                                if (have == 0) {
                                    isBad = true;
                                    break;
                                }
                                len = lens[have - 1];
                                copy = 3 + bitsOf(2);
                                dropBits(2);
                            } else if (hereVal == 17) {
                                if (!needBits(hereBits + 3)) {
                                    break leave;
                                }
                                dropBits(hereBits);
                                len = 0;
                                copy = 3 + bitsOf(3);
                                dropBits(3);
                            } else {
                                if (!needBits(hereBits + 7)) {
                                    break leave;
                                }
                                dropBits(hereBits);
                                len = 0;
                                copy = 11 + bitsOf(7);
                                dropBits(7);
                            }
                            if (have + copy > nlen + ndist) {
                                isBad = true;
                                break;
                            }
                            while (copy != 0) {
                                --copy;
                                lens[have++] = len;
                            }
                        }
                    }
                    if (isBad || lens[256] == 0) {
                        mode = Mode.BAD;
                        continue;
                    }
                    int[] tableAt = {0};
                    int[] bitsWanted = {10};
                    lencode = codes;
                    lencodeAt = 0;
                    int built = codes.build(LENS, lens, 0, nlen, tableAt, bitsWanted, work);
                    lenbits = bitsWanted[0];
                    if (built != 0) {
                        mode = Mode.BAD;
                        continue;
                    }
                    distcode = codes;
                    distcodeAt = tableAt[0];
                    bitsWanted[0] = 9;
                    built = codes.build(DISTS, lens, nlen, ndist, tableAt, bitsWanted, work);
                    distbits = bitsWanted[0];
                    if (built != 0) {
                        mode = Mode.BAD;
                        continue;
                    }
                    mode = Mode.LEN;
                }
                case LEN -> {
                    int at;
                    for (;;) {
                        at = lencodeAt + bitsOf(lenbits);
                        if (lencode.bits[at] <= bits) {
                            break;
                        }
                        if (!pullByte()) {
                            break leave;
                        }
                    }
                    int hereOp = lencode.op[at];
                    if (hereOp != 0 && (hereOp & 0xf0) == 0) {
                        int lastBits = lencode.bits[at];
                        int lastOp = hereOp;
                        int lastVal = lencode.val[at];
                        for (;;) {
                            at = lencodeAt + lastVal + (bitsOf(lastBits + lastOp) >>> lastBits);
                            if (lastBits + lencode.bits[at] <= bits) {
                                break;
                            }
                            if (!pullByte()) {
                                break leave;
                            }
                        }
                        dropBits(lastBits);
                        hereOp = lencode.op[at];
                    }
                    dropBits(lencode.bits[at]);
                    length = lencode.val[at];
                    if (hereOp == 0) {
                        mode = Mode.LIT;
                        continue;
                    }
                    if ((hereOp & END_OF_BLOCK) != 0) {
                        mode = Mode.TYPE;
                        continue;
                    }
                    if ((hereOp & INVALID_CODE) != 0) {
                        mode = Mode.BAD;
                        continue;
                    }
                    extra = hereOp & MAX_WBITS;
                    mode = Mode.LENEXT;
                }
                case LENEXT -> {
                    if (extra != 0) {
                        if (!needBits(extra)) {
                            break leave;
                        }
                        length += bitsOf(extra);
                        dropBits(extra);
                    }
                    mode = Mode.DIST;
                }
                case DIST -> {
                    int at;
                    for (;;) {
                        at = distcodeAt + bitsOf(distbits);
                        if (distcode.bits[at] <= bits) {
                            break;
                        }
                        if (!pullByte()) {
                            break leave;
                        }
                    }
                    int hereOp = distcode.op[at];
                    if ((hereOp & 0xf0) == 0) {
                        int lastBits = distcode.bits[at];
                        int lastOp = hereOp;
                        int lastVal = distcode.val[at];
                        for (;;) {
                            at = distcodeAt + lastVal + (bitsOf(lastBits + lastOp) >>> lastBits);
                            if (lastBits + distcode.bits[at] <= bits) {
                                break;
                            }
                            if (!pullByte()) {
                                break leave;
                            }
                        }
                        dropBits(lastBits);
                        hereOp = distcode.op[at];
                    }
                    dropBits(distcode.bits[at]);
                    if ((hereOp & INVALID_CODE) != 0) {
                        mode = Mode.BAD;
                        continue;
                    }
                    offset = distcode.val[at];
                    extra = hereOp & MAX_WBITS;
                    mode = Mode.DISTEXT;
                }
                case DISTEXT -> {
                    if (extra != 0) {
                        if (!needBits(extra)) {
                            break leave;
                        }
                        offset += bitsOf(extra);
                        dropBits(extra);
                    }
                    mode = Mode.MATCH;
                }
                case MATCH -> {
                    if (left == 0) {
                        break leave;
                    }
                    int copy = out - left;
                    if (offset > copy) {
                        copy = offset - copy;
                        if (copy > whave) {
                            mode = Mode.BAD;
                            continue;
                        }
                        int from;
                        if (copy > wnext) {
                            copy -= wnext;
                            from = wsize - copy;
                        } else {
                            from = wnext - copy;
                        }
                        copy = Math.min(copy, length);
                        copy = Math.min(copy, left);
                        System.arraycopy(window, from, nextOutBuffer, put, copy);
                        put += copy;
                    } else {
                        copy = Math.min(length, left);
                        for (int each = 0; each < copy; each++) {
                            nextOutBuffer[put] = nextOutBuffer[put - offset];
                            put++;
                        }
                    }
                    left -= copy;
                    length -= copy;
                    if (length == 0) {
                        mode = Mode.LEN;
                    }
                }
                case LIT -> {
                    if (left == 0) {
                        break leave;
                    }
                    nextOutBuffer[put++] = (byte) length;
                    left--;
                    mode = Mode.LEN;
                }
                case CHECK -> {
                    if (wrap != 0) {
                        if (!needBits(32)) {
                            break leave;
                        }
                        int produced = out - left;
                        totalOut += produced;
                        total += produced;
                        if ((wrap & WRAP_CHECKED) != 0) {
                            checksum(committed, put - committed);
                            committed = put;
                            check = flags != 0 ? crc.getValue() : adler.getValue();
                        }
                        out = left;
                        long stored = flags != 0 ? hold & 0xFFFFFFFFL : Integer.reverseBytes((int) hold) & 0xFFFFFFFFL;
                        if ((wrap & WRAP_CHECKED) != 0 && stored != check) {
                            mode = Mode.BAD;
                            continue;
                        }
                        initBits();
                    }
                    mode = Mode.LENGTH;
                }
                case LENGTH -> {
                    if (wrap != 0 && flags != 0) {
                        if (!needBits(32)) {
                            break leave;
                        }
                        if ((wrap & WRAP_CHECKED) != 0 && hold != (total & 0xffffffffL)) {
                            mode = Mode.BAD;
                            continue;
                        }
                        initBits();
                    }
                    mode = Mode.DONE;
                }
                case DONE -> {
                    ret = Z_STREAM_END;
                    break leave;
                }
                case BAD -> {
                    ret = Z_DATA_ERROR;
                    break leave;
                }
            }
        }
        restore();
        int checkBytes = out - availOut;
        if (wsize != 0 || (out != availOut && mode.ordinal() < Mode.BAD.ordinal()
                && (mode.ordinal() < Mode.CHECK.ordinal() || flush != Z_FINISH))) {
            if ((wrap & WRAP_CHECKED) != 0) {
                checksum(committed, put - committed);
            }
            updateWindow(nextOut, checkBytes);
        }
        in -= availIn;
        out -= availOut;
        totalIn += in;
        totalOut += out;
        total += out;
        if (((in == 0 && out == 0) || flush == Z_FINISH) && ret == Z_OK) {
            if (wsize == 0 && flush == Z_FINISH && (wrap & WRAP_CHECKED) != 0) {
                checksum(committed, put - committed);
            }
            ret = Z_BUF_ERROR;
        }
        return ret;
    }

    private boolean isHeaderChecked() {
        return (flags & GZIP_HEADER_CRC) != 0 && (wrap & WRAP_CHECKED) != 0;
    }

    private boolean skipZeroEnded() {
        if (haveIn == 0) {
            return false;
        }
        int copy = 0;
        int len;
        do {
            len = nextInBuffer[next + copy++] & 0xFF;
        } while (len != 0 && copy < haveIn);
        if (isHeaderChecked()) {
            headerCrc.update(nextInBuffer, next, copy);
        }
        haveIn -= copy;
        next += copy;
        return len == 0;
    }

    private void restore() {
        nextOut = put;
        availOut = left;
        nextIn = next;
        availIn = haveIn;
    }
}
