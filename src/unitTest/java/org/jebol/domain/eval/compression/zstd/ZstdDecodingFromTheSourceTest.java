package org.jebol.domain.eval.compression.zstd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ZstdDecodingFromTheSourceTest {

    private static List<String[]> rowsOf(String table) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                ZstdDecodingFromTheSourceTest.class.getResourceAsStream("/zstd/" + table))) {
            return new String(in.readAllBytes(), StandardCharsets.US_ASCII).lines().skip(1)
                    .map(line -> line.split("\t", -1))
                    .toList();
        }
    }

    private static byte[] octetsOf(String hex) {
        return "-".equals(hex) ? new byte[0] : HexFormat.of().parseHex(hex);
    }

    private static String digestOf(byte[] octets, int from, int length) {
        long running = 0xcbf29ce484222325L;
        for (int at = from; at < from + length; at++) {
            running ^= octets[at] & 0xFF;
            running *= 0x100000001b3L;
        }
        return "%016x".formatted(running);
    }

    private static String errorOf(ZstdFailure failure) {
        return "e" + failure.error().code();
    }

    private static List<String> mismatches(List<String[]> rows, Function<String[], String> answer,
                                           Function<String[], String> expected) {
        List<String> wrong = new ArrayList<>();
        for (String[] row : rows) {
            String got = answer.apply(row);
            String want = expected.apply(row);
            if (!got.equals(want)) {
                wrong.add(String.join(" ", row).substring(0, Math.min(120, String.join(" ", row).length()))
                        + " -> " + got + " wanted " + want);
            }
        }
        return wrong;
    }

    private static String stepOf(ZstdBitReader bits, String step) {
        char kind = step.charAt(0);
        int width = Integer.parseInt(step.substring(1, step.indexOf(':')));
        long value = 0;
        int status = -1;
        switch (kind) {
            case 'L' -> value = bits.look(width);
            case 'F' -> value = bits.lookFast(width);
            case 'R' -> value = bits.read(width);
            case 'Q' -> value = bits.readFast(width);
            case 'D' -> status = bits.reload();
            case 'U' -> status = bits.reloadFast();
            default -> bits.skip(width);
        }
        int pointer = bits.pointer() < 0 ? -1 : bits.pointer();
        return "%c%d:%x:%d:%d:%d:%d".formatted(kind, width, value, status, bits.consumed(), pointer,
                bits.isEndOfStream() ? 1 : 0);
    }

    private static String bitsAnswer(String[] row) {
        byte[] input = octetsOf(row[0]);
        ZstdBitReader bits = new ZstdBitReader();
        try {
            bits.open(input, 0, input.length);
        } catch (ZstdFailure failure) {
            return errorOf(failure) + "\t";
        }
        List<String> steps = new ArrayList<>();
        for (String step : row[2].split(" ")) {
            steps.add(stepOf(bits, step));
        }
        return input.length + "\t" + String.join(" ", steps);
    }

    @Test
    @DisplayName("The bit reader answers every look, read, skip and reload as zstd's BIT_DStream does")
    void bitReader() throws IOException {
        List<String[]> rows = rowsOf("zstd-bits.tsv");
        assertThat(rows).hasSize(660);
        assertThat(mismatches(rows, ZstdDecodingFromTheSourceTest::bitsAnswer, row -> row[1] + "\t" + row[2]))
                .isEmpty();
    }

    private static String normalizedCountAnswer(String[] row) {
        byte[] input = octetsOf(row[0]);
        ZstdNormalizedCount count = new ZstdNormalizedCount(256);
        try {
            int read = count.read(Integer.parseInt(row[1]), input, 0, input.length);
            byte[] counts = new byte[(count.maxSymbol + 1) * 2];
            for (int each = 0; each <= count.maxSymbol; each++) {
                counts[2 * each] = (byte) count.counts[each];
                counts[2 * each + 1] = (byte) (count.counts[each] >> 8);
            }
            return read + "\t" + count.maxSymbol + "\t" + count.tableLog + "\t" + digestOf(counts, 0, counts.length);
        } catch (ZstdFailure failure) {
            return errorOf(failure) + "\t-\t-\t-";
        }
    }

    @Test
    @DisplayName("Reading a normalized count, whole, cut, damaged or too wide, answers as FSE_readNCount does")
    void normalizedCount() throws IOException {
        List<String[]> rows = rowsOf("zstd-ncount.tsv");
        assertThat(rows).hasSize(3354);
        assertThat(rows).anyMatch(row -> row[0].length() < 16 && !row[2].startsWith("e"));
        assertThat(rows).anyMatch(row -> row[2].equals("e44"));
        assertThat(rows).anyMatch(row -> !row[2].startsWith("e") && Integer.parseInt(row[3]) == Integer.parseInt(row[1]));
        assertThat(mismatches(rows, ZstdDecodingFromTheSourceTest::normalizedCountAnswer,
                row -> String.join("\t", row[2], row[3], row[4], row[5]))).isEmpty();
    }

    private static String statsAnswer(String[] row) {
        byte[] input = octetsOf(row[0]);
        ZstdHuffmanDecoding huffman = new ZstdHuffmanDecoding();
        try {
            int read = huffman.readStats(input, 0, input.length);
            return read + "\t" + huffman.symbolCount() + "\t" + huffman.statsTableLog() + "\t"
                    + digestOf(huffman.weights(), 0, huffman.symbolCount());
        } catch (ZstdFailure failure) {
            return errorOf(failure) + "\t-\t-\t-";
        }
    }

    @Test
    @DisplayName("Reading a Huffman table's weights, direct or FSE-compressed, whole or damaged, answers as HUF_readStats does")
    void huffmanStats() throws IOException {
        List<String[]> rows = rowsOf("zstd-huffman-stats.tsv");
        assertThat(rows).hasSize(855);
        assertThat(rows).anyMatch(row -> (Integer.parseInt(row[0].substring(0, 2), 16) >= 128)
                && !row[1].startsWith("e"));
        assertThat(rows).anyMatch(row -> (Integer.parseInt(row[0].substring(0, 2), 16) < 128)
                && !row[1].startsWith("e"));
        assertThat(mismatches(rows, ZstdDecodingFromTheSourceTest::statsAnswer,
                row -> String.join("\t", row[1], row[2], row[3], row[4]))).isEmpty();
    }

    private static String huffmanAnswer(String[] row) {
        int decoder = Integer.parseInt(row[0]);
        int size = Integer.parseInt(row[1]);
        byte[] input = octetsOf(row[2]);
        byte[] decoded = new byte[size + 64];
        ZstdHuffmanDecoding huffman = new ZstdHuffmanDecoding();
        try {
            switch (decoder) {
                case 0 -> huffman.decompressFourStreamsWithNewTable(decoded, 0, size, input, 0, input.length);
                case 1 -> huffman.decompressSingleStreamWithNewTable(decoded, 0, size, input, 0, input.length);
                default -> huffman.decompressSingleStreamWithNewDoubleTable(decoded, 0, size, input, 0,
                        input.length);
            }
            return size + "\t" + digestOf(decoded, 0, size);
        } catch (ZstdFailure failure) {
            return errorOf(failure) + "\t-";
        }
    }

    @Test
    @DisplayName("Huffman decoding, one stream or four, single or double symbols, fast or slow, answers as zstd's HUF does")
    void huffmanDecoding() throws IOException {
        List<String[]> rows = rowsOf("zstd-huffman.tsv");
        assertThat(rows).hasSize(2975);
        for (String decoder : List.of("0", "1", "2")) {
            assertThat(rows).anyMatch(row -> row[0].equals(decoder) && !row[3].startsWith("e"));
            assertThat(rows).anyMatch(row -> row[0].equals(decoder) && row[3].startsWith("e"));
        }
        assertThat(mismatches(rows, ZstdDecodingFromTheSourceTest::huffmanAnswer,
                row -> row[3] + "\t" + row[4])).isEmpty();
    }

    private static String frameHeaderAnswer(String[] row) {
        byte[] input = octetsOf(row[0]);
        ZstdFrameHeader header = new ZstdFrameHeader();
        try {
            long read = header.read(input, 0, input.length);
            if (read != 0) {
                return read + "\t-\t-\t-\t-\t-\t-\t-";
            }
            return String.join("\t", "0", header.isSkippable ? "1" : "0",
                    Long.toUnsignedString(header.frameContentSize), Long.toUnsignedString(header.windowSize),
                    Integer.toString(header.blockSizeMax), Integer.toUnsignedString(header.dictionaryId),
                    header.hasChecksum ? "1" : "0", Integer.toString(header.headerSize));
        } catch (ZstdFailure failure) {
            return errorOf(failure) + "\t-\t-\t-\t-\t-\t-\t-";
        }
    }

    @Test
    @DisplayName("Every frame descriptor, cut at any length, and every magic answers as ZSTD_getFrameHeader does")
    void frameHeader() throws IOException {
        List<String[]> rows = rowsOf("zstd-frame-header.tsv");
        assertThat(rows).hasSize(3272);
        assertThat(rows).anyMatch(row -> row[1].equals("e14"));
        assertThat(rows).anyMatch(row -> row[1].equals("e10"));
        assertThat(rows).anyMatch(row -> row[2].equals("1"));
        assertThat(mismatches(rows, ZstdDecodingFromTheSourceTest::frameHeaderAnswer,
                row -> String.join("\t", List.of(row).subList(1, row.length)))).isEmpty();
    }
}
