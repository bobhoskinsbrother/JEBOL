package org.jebol.domain.eval.compression.deflate;

import java.util.Arrays;
import java.util.zip.Adler32;
import java.util.zip.CRC32;

public final class DeflateCompressor {

    private static final int HIGHEST_LEVEL = 12;

    private static final int PASSTHROUGH_AT_NO_EFFORT = 55;

    private static final int PASSTHROUGH_SHRINKS_PER_LEVEL = 4;

    private static final int READ_AHEAD_PADDING = 8;

    private static final int FINAL_STORED_BLOCK = 1;

    private static final int ZLIB_DEFLATE_WITH_A_32K_WINDOW = 0x7800;

    private static final int ZLIB_LEVEL_HINT_SHIFT = 6;

    private static final int ZLIB_CHECK_DIVISOR = 31;

    private static final int GZIP_FIRST_MAGIC = 0x1F;

    private static final int GZIP_SECOND_MAGIC = 0x8B;

    private static final int GZIP_DEFLATE = 8;

    private static final int GZIP_SLOWEST = 0x02;

    private static final int GZIP_FASTEST = 0x04;

    private static final int GZIP_UNKNOWN_SYSTEM = 0xFF;

    private static final int GZIP_TIME_FIELD_LENGTH = 4;

    private final int level;

    public DeflateCompressor(int levelAsked) {
        level = levelAsked < 0 || levelAsked > HIGHEST_LEVEL ? HIGHEST_LEVEL : levelAsked;
    }

    public byte[] deflate(byte[] input) {
        BitWriter out = new BitWriter(input.length + input.length / 8 + 16);
        deflateInto(out, input);
        return out.toArray();
    }

    public byte[] zlib(byte[] input) {
        BitWriter out = new BitWriter(input.length + input.length / 8 + 32);
        int header = ZLIB_DEFLATE_WITH_A_32K_WINDOW | (zlibLevelHint() << ZLIB_LEVEL_HINT_SHIFT);
        header |= ZLIB_CHECK_DIVISOR - (header % ZLIB_CHECK_DIVISOR);
        out.write(header >>> Byte.SIZE);
        out.write(header);
        deflateInto(out, input);
        Adler32 checksum = new Adler32();
        checksum.update(input);
        writeBigEndian(out, (int) checksum.getValue());
        return out.toArray();
    }

    public byte[] gzip(byte[] input) {
        BitWriter out = new BitWriter(input.length + input.length / 8 + 32);
        out.write(GZIP_FIRST_MAGIC);
        out.write(GZIP_SECOND_MAGIC);
        out.write(GZIP_DEFLATE);
        out.write(0);
        for (int each = 0; each < GZIP_TIME_FIELD_LENGTH; each++) {
            out.write(0);
        }
        out.write(gzipEffortFlags());
        out.write(GZIP_UNKNOWN_SYSTEM);
        deflateInto(out, input);
        CRC32 checksum = new CRC32();
        checksum.update(input);
        writeLittleEndian(out, (int) checksum.getValue());
        writeLittleEndian(out, input.length);
        return out.toArray();
    }

    private int zlibLevelHint() {
        if (level < 2) {
            return 0;
        }
        if (level < 6) {
            return 1;
        }
        return level < 8 ? 2 : 3;
    }

    private int gzipEffortFlags() {
        if (level < 2) {
            return GZIP_FASTEST;
        }
        return level >= 8 ? GZIP_SLOWEST : 0;
    }

    private void writeBigEndian(BitWriter out, int value) {
        for (int shift = 24; shift >= 0; shift -= Byte.SIZE) {
            out.write(value >>> shift);
        }
    }

    private void writeLittleEndian(BitWriter out, int value) {
        for (int shift = 0; shift < Integer.SIZE; shift += Byte.SIZE) {
            out.write(value >>> shift);
        }
    }

    private void deflateInto(BitWriter out, byte[] input) {
        if (input.length <= passthroughLength()) {
            storeUncompressed(out, input);
            return;
        }
        byte[] padded = Arrays.copyOf(input, input.length + READ_AHEAD_PADDING);
        parserFor(input.length).compress(padded, input.length, out);
        out.finish();
    }

    private int passthroughLength() {
        return level == 0 ? Integer.MAX_VALUE : PASSTHROUGH_AT_NO_EFFORT - level * PASSTHROUGH_SHRINKS_PER_LEVEL;
    }

    private void storeUncompressed(BitWriter out, byte[] input) {
        int at = 0;
        do {
            boolean last = input.length - at <= DeflateTables.LONGEST_STORED_BLOCK;
            int count = last ? input.length - at : DeflateTables.LONGEST_STORED_BLOCK;
            out.write(last ? FINAL_STORED_BLOCK : 0);
            out.writeLittleEndianShort(count);
            out.writeLittleEndianShort(~count);
            out.write(input, at, count);
            at += count;
        } while (at != input.length);
    }

    private DeflateParser parserFor(int inputLength) {
        BlockEncoder encoder = new BlockEncoder();
        return switch (level) {
            case 1 -> new FastestParser(encoder, 32);
            case 2 -> new GreedyParser(encoder, 6, 10);
            case 3 -> new GreedyParser(encoder, 12, 14);
            case 4 -> new GreedyParser(encoder, 16, 30);
            case 5 -> new LazyParser(encoder, 16, 30, false);
            case 6 -> new LazyParser(encoder, 35, 65, false);
            case 7 -> new LazyParser(encoder, 100, 130, false);
            case 8 -> new LazyParser(encoder, 300, DeflateTables.MAX_MATCH_LENGTH, true);
            case 9 -> new LazyParser(encoder, 600, DeflateTables.MAX_MATCH_LENGTH, true);
            case 10 -> new NearOptimalParser(encoder, inputLength, 35, 75, 2, 32, 32, 0);
            case 11 -> new NearOptimalParser(encoder, inputLength, 100, 150, 4, 16, 16, 1000);
            default -> new NearOptimalParser(encoder, inputLength, 300, DeflateTables.MAX_MATCH_LENGTH,
                    10, 1, 1, 10000);
        };
    }
}
