package org.jebol.domain.eval.compression.zlibng;

import java.util.Optional;

import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.BLOCK_DONE;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.BUSY_STATE;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.CHAIN_ABOVE_WHICH_THE_HASH_ROLLS;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.FINISH_DONE;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.FINISH_STARTED;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.FINISH_STATE;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.GZIP_STATE;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.INIT_STATE;
import static org.jebol.domain.eval.compression.zlibng.ZlibNgDeflateState.NEED_MORE;

/**
 * A port of zlib-ng 2.3.2's deflate, answering its bytes exactly for every level, strategy, window and
 * flush. It is driven as zlib's {@code z_stream} is: give it input and room for output, call
 * {@link #deflate(int)} with a flush mode, and read back how much of each it used.
 */
public final class ZlibNgDeflater {

    public static final int Z_NO_FLUSH = 0;
    public static final int Z_PARTIAL_FLUSH = 1;
    public static final int Z_SYNC_FLUSH = 2;
    public static final int Z_FULL_FLUSH = 3;
    public static final int Z_FINISH = 4;
    public static final int Z_BLOCK = 5;

    public static final int Z_OK = 0;
    public static final int Z_STREAM_END = 1;
    public static final int Z_STREAM_ERROR = -2;
    public static final int Z_BUF_ERROR = -5;

    public static final int Z_DEFAULT_COMPRESSION = -1;
    public static final int Z_DEFAULT_STRATEGY = 0;
    public static final int Z_FILTERED = 1;
    public static final int Z_HUFFMAN_ONLY = 2;
    public static final int Z_RLE = 3;
    public static final int Z_FIXED = 4;

    public static final int DEFAULT_MEM_LEVEL = 8;

    private static final int MAX_WBITS = 15;
    private static final int MIN_WBITS = 8;
    private static final int MAX_MEM_LEVEL = 9;
    private static final int GZIP_WINDOW_OFFSET = 16;
    private static final int Z_DEFLATED = 8;
    private static final int PRESET_DICT = 0x20;
    private static final int ZLIB_WRAPLEN = 6;
    private static final int GZIP_WRAPLEN = 18;
    private static final int DEFLATE_BLOCK_OVERHEAD = (3 + 15 + 6) >> 3;
    private static final int GZIP_ID1 = 31;
    private static final int GZIP_ID2 = 139;
    private static final int FLUSH_MARKING_AN_UNFINISHED_OUTPUT = -1;
    private static final int FLUSH_MARKING_A_FRESH_STREAM = -2;

    private record Configuration(int goodLength, int maxLazy, int niceLength, int maxChain, Strategy func) {
    }

    @FunctionalInterface
    private interface Strategy {
        int compress(ZlibNgDeflateState s, int flush);
    }

    private static final Configuration[] CONFIGURATION_TABLE = {
        new Configuration(0, 0, 0, 0, ZlibNgStrategies::deflateStored),
        new Configuration(0, 0, 0, 0, ZlibNgStrategies::deflateQuick),
        new Configuration(4, 4, 8, 4, ZlibNgStrategies::deflateFast),
        new Configuration(4, 6, 16, 6, ZlibNgStrategies::deflateMedium),
        new Configuration(4, 12, 32, 24, ZlibNgStrategies::deflateMedium),
        new Configuration(8, 16, 32, 32, ZlibNgStrategies::deflateMedium),
        new Configuration(8, 16, 128, 128, ZlibNgStrategies::deflateMedium),
        new Configuration(8, 32, 128, 256, ZlibNgStrategies::deflateSlow),
        new Configuration(32, 128, 258, 1024, ZlibNgStrategies::deflateSlow),
        new Configuration(32, 258, 258, 4096, ZlibNgStrategies::deflateSlow),
    };

    byte[] nextInBuffer;
    int nextIn;
    int availIn;
    long totalIn;
    byte[] nextOutBuffer;
    int nextOut;
    int availOut;
    long totalOut;

    private final ZlibNgDeflateState state;
    private final int operatingSystem;

    private ZlibNgDeflater(int level, int windowBits, int wrap, int memLevel, int strategy, int operatingSystem) {
        int litBufsize = 1 << (memLevel + 6);
        this.state = new ZlibNgDeflateState(this, windowBits, litBufsize);
        this.operatingSystem = operatingSystem;
        ZlibNgDeflateState s = state;
        s.status = INIT_STATE;
        s.wrap = wrap;
        s.wBits = windowBits;
        s.wSize = 1 << s.wBits;
        s.wMask = s.wSize - 1;
        s.highWater = 0;
        s.litBufsize = litBufsize;
        s.pendingBufSize = s.litBufsize * 4;
        s.symEnd = s.litBufsize - 1;
        s.level = level;
        s.strategy = strategy;
        s.blockOpen = 0;
        reset();
    }

    /**
     * Opens a stream as {@code deflateInit2} does, or answers nothing where it answers {@code Z_STREAM_ERROR}.
     * A negative window is raw deflate, 8 to 15 is zlib, and 16 more than that is gzip. The operating system
     * is the byte a gzip header names, which zlib-ng fixes when it is compiled.
     */
    public static Optional<ZlibNgDeflater> open(int level, int windowBits, int memLevel, int strategy,
                                                int operatingSystem) {
        int wrap = 1;
        int levelAsked = level == Z_DEFAULT_COMPRESSION ? 6 : level;
        int window = windowBits;
        if (window < 0) {
            wrap = 0;
            if (window < -MAX_WBITS) {
                return Optional.empty();
            }
            window = -window;
        } else if (window > MAX_WBITS) {
            wrap = 2;
            window -= GZIP_WINDOW_OFFSET;
        }
        if (memLevel < 1 || memLevel > MAX_MEM_LEVEL || window < MIN_WBITS || window > MAX_WBITS
                || levelAsked < 0 || levelAsked > 9 || strategy < 0 || strategy > Z_FIXED
                || (window == MIN_WBITS && wrap != 1)) {
            return Optional.empty();
        }
        if (window == MIN_WBITS) {
            window = MIN_WBITS + 1;
        }
        return Optional.of(new ZlibNgDeflater(levelAsked, window, wrap, memLevel, strategy, operatingSystem));
    }

    /** Gives the stream its next input, as setting {@code next_in} and {@code avail_in} does. */
    public void input(byte[] buffer, int from, int length) {
        nextInBuffer = buffer;
        nextIn = from;
        availIn = length;
    }

    /** Gives the stream room to write, as setting {@code next_out} and {@code avail_out} does. */
    public void output(byte[] buffer, int from, int length) {
        nextOutBuffer = buffer;
        nextOut = from;
        availOut = length;
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

    /** Starts the stream again with the same settings, keeping the window as {@code deflateReset} keeps it. */
    public void reset() {
        ZlibNgDeflateState s = state;
        totalIn = 0;
        totalOut = 0;
        s.pending = 0;
        s.pendingOut = 0;
        if (s.wrap < 0) {
            s.wrap = -s.wrap;
        }
        s.status = s.wrap == 2 ? GZIP_STATE : INIT_STATE;
        s.crc.reset();
        s.adler.reset();
        s.lastFlush = FLUSH_MARKING_A_FRESH_STREAM;
        ZlibNgTrees.trInit(s);
        s.clearHash();
        setLevel(s.level);
        s.strstart = 0;
        s.blockStart = 0;
        s.lookahead = 0;
        s.insert = 0;
        s.prevLength = 0;
        s.matchAvailable = 0;
        s.matchStart = 0;
        s.insH = 0;
    }

    private void setLevel(int level) {
        ZlibNgDeflateState s = state;
        Configuration configuration = CONFIGURATION_TABLE[level];
        s.maxLazyMatch = configuration.maxLazy();
        s.goodMatch = configuration.goodLength();
        s.niceMatch = configuration.niceLength();
        s.maxChainLength = configuration.maxChain();
        s.isRollingHash = s.maxChainLength > CHAIN_ABOVE_WHICH_THE_HASH_ROLLS;
        s.level = level;
    }

    /** The most bytes {@code deflateBound} says compressing so many could take, given the stream as it is now. */
    public long bound(long sourceLen) {
        ZlibNgDeflateState s = state;
        long complen = sourceLen + ((sourceLen + 7) >>> 3) + ((sourceLen + 63) >>> 6) + 5;
        long wraplen = switch (s.wrap) {
            case 0 -> 0;
            case 1 -> ZLIB_WRAPLEN + (s.strstart != 0 ? 4 : 0);
            case 2 -> GZIP_WRAPLEN;
            default -> ZLIB_WRAPLEN;
        };
        if (s.wBits != MAX_WBITS) {
            if (s.level == 0) {
                complen = sourceLen + (sourceLen >>> 5) + (sourceLen >>> 7) + (sourceLen >>> 11) + 7;
            }
            return complen + wraplen;
        }
        return sourceLen
                + (sourceLen == 0 ? 1 : 0)
                + (sourceLen < 9 ? 1 : 0)
                + ((sourceLen + 7) >>> 3)
                + DEFLATE_BLOCK_OVERHEAD
                + wraplen;
    }

    private static int rank(int flush) {
        return (flush * 2) - (flush > 4 ? 9 : 0);
    }

    /** Compresses as much as it can, as {@code deflate} does, and answers its result code. */
    public int deflate(int flush) {
        ZlibNgDeflateState s = state;
        if (flush > Z_BLOCK || flush < 0) {
            return Z_STREAM_ERROR;
        }
        if (nextOutBuffer == null || (availIn != 0 && nextInBuffer == null)
                || (s.status == FINISH_STATE && flush != Z_FINISH)) {
            return Z_STREAM_ERROR;
        }
        if (availOut == 0) {
            return Z_BUF_ERROR;
        }
        int oldFlush = s.lastFlush;
        s.lastFlush = flush;
        if (s.pending != 0) {
            s.flushPending();
            if (availOut == 0) {
                s.lastFlush = FLUSH_MARKING_AN_UNFINISHED_OUTPUT;
                return Z_OK;
            }
        } else if (availIn == 0 && rank(flush) <= rank(oldFlush) && flush != Z_FINISH) {
            return Z_BUF_ERROR;
        }
        if (s.status == FINISH_STATE && availIn != 0) {
            return Z_BUF_ERROR;
        }
        if (s.status == INIT_STATE && s.wrap == 0) {
            s.status = BUSY_STATE;
        }
        if (s.status == INIT_STATE) {
            int header = (Z_DEFLATED + ((s.wBits - 8) << 4)) << 8;
            int levelFlags;
            if (s.strategy >= Z_HUFFMAN_ONLY || s.level < 2) {
                levelFlags = 0;
            } else if (s.level < 6) {
                levelFlags = 1;
            } else if (s.level == 6) {
                levelFlags = 2;
            } else {
                levelFlags = 3;
            }
            header |= (levelFlags << 6);
            if (s.strstart != 0) {
                header |= PRESET_DICT;
            }
            header += 31 - (header % 31);
            s.putShortMsb(header);
            if (s.strstart != 0) {
                s.putUint32Msb((int) s.adler.getValue());
            }
            s.adler.reset();
            s.status = BUSY_STATE;
            s.flushPending();
            if (s.pending != 0) {
                s.lastFlush = FLUSH_MARKING_AN_UNFINISHED_OUTPUT;
                return Z_OK;
            }
        }
        if (s.status == GZIP_STATE) {
            s.crc.reset();
            s.putByte(GZIP_ID1);
            s.putByte(GZIP_ID2);
            s.putByte(Z_DEFLATED);
            s.putUint32(0);
            s.putByte(0);
            s.putByte(s.level == 9 ? 2 : (s.strategy >= Z_HUFFMAN_ONLY || s.level < 2 ? 4 : 0));
            s.putByte(operatingSystem);
            s.status = BUSY_STATE;
            s.flushPending();
            if (s.pending != 0) {
                s.lastFlush = FLUSH_MARKING_AN_UNFINISHED_OUTPUT;
                return Z_OK;
            }
        }
        if (availIn != 0 || s.lookahead != 0 || (flush != Z_NO_FLUSH && s.status != FINISH_STATE)) {
            int bstate = s.level == 0 ? ZlibNgStrategies.deflateStored(s, flush)
                    : s.strategy == Z_HUFFMAN_ONLY ? ZlibNgStrategies.deflateHuff(s, flush)
                    : s.strategy == Z_RLE ? ZlibNgStrategies.deflateRle(s, flush)
                    : CONFIGURATION_TABLE[s.level].func().compress(s, flush);
            if (bstate == FINISH_STARTED || bstate == FINISH_DONE) {
                s.status = FINISH_STATE;
            }
            if (bstate == NEED_MORE || bstate == FINISH_STARTED) {
                if (availOut == 0) {
                    s.lastFlush = FLUSH_MARKING_AN_UNFINISHED_OUTPUT;
                }
                return Z_OK;
            }
            if (bstate == BLOCK_DONE) {
                if (flush == Z_PARTIAL_FLUSH) {
                    ZlibNgTrees.align(s);
                } else if (flush != Z_BLOCK) {
                    ZlibNgTrees.storedBlock(s, null, 0, 0, false);
                    if (flush == Z_FULL_FLUSH) {
                        s.clearHash();
                        if (s.lookahead == 0) {
                            s.strstart = 0;
                            s.blockStart = 0;
                            s.insert = 0;
                        }
                    }
                }
                s.flushPending();
                if (availOut == 0) {
                    s.lastFlush = FLUSH_MARKING_AN_UNFINISHED_OUTPUT;
                    return Z_OK;
                }
            }
        }
        if (flush != Z_FINISH) {
            return Z_OK;
        }
        if (s.wrap == 2) {
            s.putUint32((int) s.crc.getValue());
            s.putUint32((int) totalIn);
        } else if (s.wrap == 1) {
            s.putUint32Msb((int) s.adler.getValue());
        }
        s.flushPending();
        if (s.wrap > 0) {
            s.wrap = -s.wrap;
        }
        if (s.pending == 0) {
            return Z_STREAM_END;
        }
        return Z_OK;
    }
}
