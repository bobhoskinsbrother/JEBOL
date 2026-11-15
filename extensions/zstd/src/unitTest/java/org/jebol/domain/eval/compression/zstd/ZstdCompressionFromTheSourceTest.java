package org.jebol.domain.eval.compression.zstd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ZstdCompressionFromTheSourceTest {

    private static final int CONTINUE = 0;
    private static final int FLUSH = 1;
    private static final int END = 2;
    private static final long FNV_START = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private static List<String[]> rowsOf(String table) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                ZstdCompressionFromTheSourceTest.class.getResourceAsStream("/zstd/" + table))) {
            return new String(in.readAllBytes(), StandardCharsets.US_ASCII).lines().skip(1)
                    .map(line -> line.split("\t", -1))
                    .toList();
        }
    }

    private static byte[] inputOf(String[] row) {
        return ZstdTestInputs.make(Integer.parseInt(row[0]), Integer.parseInt(row[1]), Integer.parseInt(row[2]),
                Integer.parseInt(row[3]), (int) Long.parseLong(row[4]));
    }

    private static String oneShotAnswer(String[] row) {
        byte[] input = inputOf(row);
        String inputDigest = ZstdTestInputs.digest(input, 0, input.length);
        if (!inputDigest.equals(row[5])) {
            return "input " + inputDigest;
        }
        byte[] compressed = ZstdCompressionContext.compressOnce(input, Integer.parseInt(row[6]));
        return compressed.length + "\t" + ZstdTestInputs.digest(compressed, 0, compressed.length);
    }

    private static long foldWord(long running, long word) {
        long hash = running;
        for (int at = 0; at < 8; at++) {
            hash ^= (word >>> (8 * at)) & 0xFF;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    private static String streamAnswer(String[] row) {
        byte[] input = inputOf(row);
        String inputDigest = ZstdTestInputs.digest(input, 0, input.length);
        if (!inputDigest.equals(row[5])) {
            return "input " + inputDigest;
        }
        int level = Integer.parseInt(row[6]);
        int[] schedule = {(int) Long.parseLong(row[7])};
        int maxLog = Integer.parseInt(row[8]);
        int capacity = (int) (ZstdCompressionContext.compressBound(input.length) * 2 + 4096 + input.length / 16);
        byte[] output = new byte[capacity];
        ZstdCompressionStream stream = new ZstdCompressionStream(level);
        ZstdStreamBuffer out = new ZstdStreamBuffer(output, capacity, 0);
        int at = 0;
        long operations = FNV_START;
        int steps = 0;
        while (at < input.length) {
            int chunkLog = Integer.remainderUnsigned(ZstdTestInputs.nextRandom(schedule), maxLog % 100);
            int chunk = ZstdTestInputs.nextRandom(schedule) & ((1 << chunkLog) - 1);
            chunk = Math.min(chunk, input.length - at);
            int pick = Integer.remainderUnsigned(ZstdTestInputs.nextRandom(schedule), 10);
            int operation = pick < 7 ? CONTINUE : pick < 9 ? FLUSH : END;
            if (maxLog >= 100 && operation == END) {
                operation = FLUSH;
            }
            ZstdStreamBuffer in = new ZstdStreamBuffer(input, at + chunk, at);
            while (in.position < in.size) {
                stream.compress(out, in);
            }
            at += chunk;
            if (operation == FLUSH) {
                while (stream.flush(out) != 0) {
                    assertThat(out.position).isLessThan(capacity);
                }
            }
            if (operation == END) {
                while (stream.end(out) != 0) {
                    assertThat(out.position).isLessThan(capacity);
                }
            }
            operations = foldWord(operations, ((long) chunk << 2) | operation);
            steps++;
        }
        while (stream.end(out) != 0) {
            assertThat(out.position).isLessThan(capacity);
        }
        return steps + "\t%016x\t".formatted(operations) + out.position + "\t"
                + ZstdTestInputs.digest(output, 0, out.position);
    }

    private static List<String> mismatches(List<String[]> rows, boolean isStream) {
        List<String> wrong = new ArrayList<>();
        for (String[] row : rows) {
            String want = isStream ? String.join("\t", row[9], row[10], row[11], row[12])
                    : row[7] + "\t" + row[8];
            String got;
            try {
                got = isStream ? streamAnswer(row) : oneShotAnswer(row);
            } catch (RuntimeException failure) {
                got = failure.toString();
            }
            if (!got.equals(want)) {
                wrong.add(String.join(" ", java.util.Arrays.copyOfRange(row, 0, isStream ? 9 : 7)) + " -> " + got
                        + " wanted " + want);
            }
        }
        return wrong;
    }

    @Test
    @DisplayName("Every input zstd compressed in one shot, at every level, compresses to the same octets")
    void everyOneShotCompressionMatchesZstd() throws IOException {
        List<String[]> rows = rowsOf("zstd-compress.tsv");
        assertThat(rows).hasSize(1529);
        assertThat(mismatches(rows, false)).isEmpty();
    }

    @Test
    @DisplayName("Every stream zstd compressed, with writes, flushes and finishes, compresses to the same octets")
    void everyStreamedCompressionMatchesZstd() throws IOException {
        List<String[]> rows = rowsOf("zstd-compress-stream.tsv");
        assertThat(rows).hasSize(250);
        assertThat(mismatches(rows, true)).isEmpty();
    }
}
