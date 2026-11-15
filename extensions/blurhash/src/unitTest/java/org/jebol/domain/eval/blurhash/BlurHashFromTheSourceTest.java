package org.jebol.domain.eval.blurhash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class BlurHashFromTheSourceTest {

    private static final List<String> HASHES_THAT_BARELY_READ = List.of(
            "", "LEHV6", "LEHV6nWB2yk8pyo0adR*.7kCMdnj!", "LEHV6nWB2yk8pyo0adR*.7kCMdn",
            "~~~~~~", "00000000", "L~HV6nWB2yk8pyo0adR*.7kCMdnj", "LEHV6nWB2yk8pyo0adR*.7kCMdnj");

    private static final int UNTOUCHED = 7;

    private static final class Noise {

        private int seed;

        Noise(int seed) {
            this.seed = seed;
        }

        int nextByte() {
            seed = seed * 1103515245 + 12345;
            return (seed >>> 16) & 0xFF;
        }
    }

    private static byte[] picture(int wide, int high, int pattern) {
        byte[] pixels = new byte[wide * high * 4];
        Noise noise = new Noise(wide * 131 + high * 7 + pattern);
        for (int y = 0; y < high; y++) {
            for (int x = 0; x < wide; x++) {
                int at = 4 * (x + y * wide);
                int[] channels = switch (pattern) {
                    case 0 -> new int[] {noise.nextByte(), noise.nextByte(), noise.nextByte(), noise.nextByte()};
                    case 1 -> new int[] {wide > 1 ? x * 255 / (wide - 1) : 0, high > 1 ? y * 255 / (high - 1) : 0,
                            (x + y) & 255, 255};
                    case 2 -> new int[] {0, 0, 0, 255};
                    case 3 -> new int[] {255, 255, 255, 255};
                    case 4 -> new int[] {200, 10, 60, 0};
                    default -> new int[] {(x * 37) & 255, (y * 91) & 255, ((x ^ y) * 13) & 255, 128};
                };
                for (int channel = 0; channel < 4; channel++) {
                    pixels[at + channel] = (byte) channels[channel];
                }
            }
        }
        return pixels;
    }

    private static byte[] read(String table) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                BlurHashFromTheSourceTest.class.getResourceAsStream("/blurhash/" + table))) {
            return in.readAllBytes();
        }
    }

    private static List<String[]> rowsOf(byte[] table) {
        return new String(table, StandardCharsets.US_ASCII).lines().skip(1)
                .map(line -> line.split("\t", -1))
                .toList();
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String fnv1a(byte[] bytes, int length) {
        long hash = 0xcbf29ce484222325L;
        for (int at = 0; at < length; at++) {
            hash ^= bytes[at] & 0xFF;
            hash *= 0x100000001b3L;
        }
        return "%016x".formatted(hash);
    }

    private static List<String> encodedHashes() throws IOException {
        return rowsOf(read("blurhash-encode.tsv")).stream().map(row -> row[5]).toList();
    }

    @Nested
    @DisplayName("the tables")
    class TheTables {

        @Test
        @DisplayName("are the ones the README describes: their row counts and checksums")
        void areWhatTheySay() throws IOException, NoSuchAlgorithmException {
            byte[] encode = read("blurhash-encode.tsv");
            byte[] decode = read("blurhash-decode.tsv");
            assertThat(rowsOf(encode)).hasSize(1986).allMatch(row -> row.length == 6);
            assertThat(rowsOf(decode)).hasSize(2586).allMatch(row -> row.length == 6);
            assertThat(sha256(encode)).isEqualTo("8163db1f25d3e029085db28fb9fdfb9bedd21d7c38515962ce2659dc21cbcb2b");
            assertThat(sha256(decode)).isEqualTo("3f4cce5a13e7cb5954b8c5550112150f630044cbabaf33570c9041682a27f7f5");
        }
    }

    @Nested
    @DisplayName("encoding")
    class Encoding {

        @Test
        @DisplayName("writes the C's hash for every picture and every component count in the table")
        void writesTheCsHash() throws IOException {
            List<String> differing = new ArrayList<>();
            for (String[] row : rowsOf(read("blurhash-encode.tsv"))) {
                int wide = Integer.parseInt(row[0]);
                int high = Integer.parseInt(row[1]);
                String written = BlurHashEncoder.encode(Integer.parseInt(row[3]), Integer.parseInt(row[4]), wide, high,
                        picture(wide, high, Integer.parseInt(row[2])), 4 * wide).orElseThrow();
                if (!written.equals(row[5])) {
                    differing.add(String.join(" ", row) + " wrote " + written);
                }
            }
            assertThat(differing).isEmpty();
        }

        @Test
        @DisplayName("refuses a component count outside 1 to 9")
        void refusesAComponentCountOutOfRange() {
            byte[] pixels = picture(2, 2, 0);
            assertThat(BlurHashEncoder.encode(0, 3, 2, 2, pixels, 8)).isEmpty();
            assertThat(BlurHashEncoder.encode(3, 10, 2, 2, pixels, 8)).isEmpty();
            assertThat(BlurHashEncoder.encode(9, 9, 2, 2, pixels, 8)).isPresent();
        }

        @Test
        @DisplayName("encodes an empty picture, whose factors are all NaN, as the C's fmaxf and fminf make it")
        void encodesAnEmptyPicture() {
            assertThat(BlurHashEncoder.encode(3, 3, 0, 0, new byte[0], 0)).contains("K0TSUA~q~q~q~q~q~q~q~q");
        }

        @Test
        @DisplayName("ignores the fourth byte of a pixel")
        void ignoresTheFourthByte() {
            byte[] opaque = {10, (byte) 200, 30, (byte) 255, 40, 50, 60, (byte) 255};
            byte[] clear = {10, (byte) 200, 30, 0, 40, 50, 60, 0};
            assertThat(BlurHashEncoder.encode(3, 3, 2, 1, clear, 8))
                    .isEqualTo(BlurHashEncoder.encode(3, 3, 2, 1, opaque, 8));
        }
    }

    @Nested
    @DisplayName("decoding")
    class Decoding {

        @Test
        @DisplayName("paints the C's pixels for every hash, size and punch in the table, and refuses what it refuses")
        void paintsTheCsPixels() throws IOException {
            List<String> hashes = new ArrayList<>(encodedHashes());
            hashes.addAll(HASHES_THAT_BARELY_READ);
            List<String> differing = new ArrayList<>();
            for (String[] row : rowsOf(read("blurhash-decode.tsv"))) {
                String hash = hashes.get(Integer.parseInt(row[0]) - 1);
                int wide = Integer.parseInt(row[1]);
                int high = Integer.parseInt(row[2]);
                byte[] pixels = new byte[64 * 48 * 4];
                Arrays.fill(pixels, (byte) UNTOUCHED);
                boolean decoded = BlurHashDecoder.decodeInto(hash.getBytes(StandardCharsets.ISO_8859_1),
                        wide, high, Integer.parseInt(row[3]), 4, pixels);
                String digest = fnv1a(pixels, wide * high * 4);
                if (decoded != Boolean.parseBoolean(row[4]) || !digest.equals(row[5])) {
                    differing.add(String.join(" ", row) + " answered " + decoded + " " + digest);
                }
            }
            assertThat(differing).isEmpty();
        }

        @Test
        @DisplayName("reads the hash only up to its first zero byte, as strlen does")
        void stopsAtTheFirstZeroByte() {
            byte[] hash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj".getBytes(StandardCharsets.ISO_8859_1);
            byte[] withZeroInside = Arrays.copyOf(hash, hash.length + 3);
            byte[] cutShort = hash.clone();
            cutShort[10] = 0;
            assertThat(BlurHashDecoder.decodeInto(withZeroInside, 1, 1, 1, 4, new byte[4])).isTrue();
            assertThat(BlurHashDecoder.decodeInto(cutShort, 1, 1, 1, 4, new byte[4])).isFalse();
        }

        @Test
        @DisplayName("treats a punch below one as one")
        void treatsAPunchBelowOneAsOne() {
            byte[] hash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj".getBytes(StandardCharsets.ISO_8859_1);
            byte[] withOne = new byte[3 * 2 * 4];
            byte[] withNone = new byte[3 * 2 * 4];
            BlurHashDecoder.decodeInto(hash, 3, 2, 1, 4, withOne);
            BlurHashDecoder.decodeInto(hash, 3, 2, -5, 4, withNone);
            assertThat(withNone).isEqualTo(withOne);
        }

        @Test
        @DisplayName("writes three bytes a pixel and no alpha when asked for three channels")
        void writesThreeChannels() {
            byte[] hash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj".getBytes(StandardCharsets.ISO_8859_1);
            byte[] four = new byte[4];
            byte[] three = new byte[] {UNTOUCHED, UNTOUCHED, UNTOUCHED, UNTOUCHED};
            BlurHashDecoder.decodeInto(hash, 1, 1, 1, 4, four);
            BlurHashDecoder.decodeInto(hash, 1, 1, 1, 3, three);
            assertThat(Arrays.copyOf(three, 3)).isEqualTo(Arrays.copyOf(four, 3));
            assertThat(three[3]).isEqualTo((byte) UNTOUCHED);
            assertThat(four[3]).isEqualTo((byte) 255);
        }
    }
}
