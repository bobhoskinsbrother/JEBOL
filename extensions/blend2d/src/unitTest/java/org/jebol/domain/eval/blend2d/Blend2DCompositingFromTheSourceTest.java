package org.jebol.domain.eval.blend2d;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

class Blend2DCompositingFromTheSourceTest {

    private static final int SIDE = 64;

    private static final int PIXELS = SIDE * SIDE;

    private static final class Noise {

        private int state;

        Noise(int seed) {
            state = seed * 0x9E3779B1 + 1;
        }

        int next() {
            state = state * 1103515245 + 12345;
            int mixed = state ^ (state >>> 15);
            state = state * 1103515245 + 12345;
            return mixed ^ (state << 7);
        }
    }

    private record Row(CompositingOperator operator, int colour, double alpha, int seed, long crc) {

        static Row from(String[] fields) {
            return new Row(CompositingOperator.values()[Integer.parseInt(fields[0])],
                    Integer.parseUnsignedInt(fields[1], 16), Double.parseDouble(fields[2]),
                    Integer.parseUnsignedInt(fields[3]), Long.parseLong(fields[4], 16));
        }
    }

    private static byte[] read(String table) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                Blend2DCompositingFromTheSourceTest.class.getResourceAsStream("/blend2d/" + table))) {
            return in.readAllBytes();
        }
    }

    private static List<Row> rowsOf(byte[] table) {
        return new String(table, StandardCharsets.US_ASCII).lines().skip(1)
                .map(line -> Row.from(line.split("\t", -1)))
                .toList();
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static long crcOf(int[] pixels) {
        byte[] bytes = new byte[pixels.length * 4];
        for (int at = 0; at < pixels.length; at++) {
            for (int octet = 0; octet < 4; octet++) {
                bytes[4 * at + octet] = (byte) (pixels[at] >>> (8 * octet));
            }
        }
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return crc.getValue();
    }

    private static int[] destinationAndMask(Noise noise, int[] mask) {
        int[] pixels = new int[PIXELS];
        for (int at = 0; at < PIXELS; at++) {
            pixels[at] = noise.next();
        }
        for (int at = 0; at < PIXELS; at++) {
            int random = noise.next();
            int row = at / SIDE;
            mask[at] = row < 4 ? 0 : row < 8 ? 0xFF : row < 10 ? ((random & 1) != 0 ? 0xFF : 0) : random >>> 24;
        }
        return pixels;
    }

    private static List<String> differingRows(String table, boolean isMasked) throws IOException {
        List<String> differing = new ArrayList<>();
        for (Row row : rowsOf(read(table))) {
            int[] mask = new int[PIXELS];
            int[] pixels = destinationAndMask(new Noise(row.seed()), mask);
            Optional<SolidFill> fill = SolidFill.resolve(row.operator(), row.colour(), SolidFill.scaleAlpha(row.alpha()));
            if (fill.isPresent()) {
                for (int at = 0; at < PIXELS; at++) {
                    pixels[at] = isMasked ? fill.get().composite(pixels[at], mask[at]) : fill.get().composite(pixels[at]);
                }
            }
            if (crcOf(pixels) != row.crc()) {
                differing.add(row.toString());
            }
        }
        return differing;
    }

    @Nested
    @DisplayName("the tables")
    class TheTables {

        @Test
        @DisplayName("are the ones the README describes: their row counts and checksums")
        void areWhatTheySay() throws IOException, NoSuchAlgorithmException {
            byte[] fills = read("blend2d-fill.tsv");
            byte[] masks = read("blend2d-mask.tsv");
            assertThat(rowsOf(fills)).hasSize(6264);
            assertThat(rowsOf(masks)).hasSize(1740);
            assertThat(sha256(fills)).isEqualTo("70972dda499b86251dae120bf41e8c1cdd846ef7eb22aec077f9dcbb99f6962f");
            assertThat(sha256(masks)).isEqualTo("007e3e26da46ec005a22fd09ba686159ad8a39d40cd0523efa1c222ddcb00470");
        }

        @Test
        @DisplayName("are drawn over the noise table.c makes, whose first pixels from seed 7 are known")
        void drawOverTheSameNoise() {
            Noise noise = new Noise(7);
            assertThat(List.of(noise.next(), noise.next(), noise.next(), noise.next()))
                    .containsExactly(0xb0c79bd6, 0xbaf56eab, 0xd717dd53, 0x3dc92067);
        }
    }

    @Nested
    @DisplayName("a solid fill")
    class ASolidFill {

        @Test
        @DisplayName("over a whole picture answers the JIT's pixels for every operator, colour and alpha in the table")
        void fillsAsTheJitDoes() throws IOException {
            assertThat(differingRows("blend2d-fill.tsv", false)).isEmpty();
        }

        @Test
        @DisplayName("through a mask answers the JIT's pixels where the mask is empty, full, either, or anything between")
        void fillsThroughAMaskAsTheJitDoes() throws IOException {
            assertThat(differingRows("blend2d-mask.tsv", true)).isEmpty();
        }

        @Test
        @DisplayName("draws nothing when the alpha scales to zero, though the colour is opaque")
        void drawsNothingAtZeroAlpha() {
            assertThat(SolidFill.resolve(CompositingOperator.SOURCE_COPY, 0xFFFFFFFF, 0)).isEmpty();
        }

        @Test
        @DisplayName("of transparent black over the destination changes nothing, while a cleared one empties it")
        void transparentBlackIsANoOperationExceptWhereItClears() {
            assertThat(SolidFill.resolve(CompositingOperator.SOURCE_OVER, 0, 255)).isEmpty();
            assertThat(SolidFill.resolve(CompositingOperator.CLEAR, 0xFF123456, 255).orElseThrow()
                    .composite(0xFFABCDEF)).isZero();
        }

        @Test
        @DisplayName("source-over of an opaque colour copies it, whatever is under it")
        void opaqueSourceOverCopies() {
            assertThat(SolidFill.resolve(CompositingOperator.SOURCE_OVER, 0xFF102030, 255).orElseThrow()
                    .composite(0x80FFFFFF)).isEqualTo(0xFF102030);
        }

        @Test
        @DisplayName("leaves a pixel its mask does not cover exactly as it was")
        void leavesAnUncoveredPixel() {
            assertThat(SolidFill.resolve(CompositingOperator.XOR, 0x80FF0000, 200).orElseThrow()
                    .composite(0x12345678, 0)).isEqualTo(0x12345678);
        }
    }

    @Nested
    @DisplayName("the global alpha")
    class TheGlobalAlpha {

        @Test
        @DisplayName("is scaled to 255 and rounded half up, not to even")
        void roundsHalfUp() {
            assertThat(SolidFill.scaleAlpha(127.5 / 255)).isEqualTo(128);
            assertThat(SolidFill.scaleAlpha(126.5 / 255)).isEqualTo(127);
            assertThat(SolidFill.scaleAlpha(0.49 / 255)).isZero();
        }

        @Test
        @DisplayName("is clamped to 0..1 first")
        void isClamped() {
            assertThat(SolidFill.scaleAlpha(-3)).isZero();
            assertThat(SolidFill.scaleAlpha(7)).isEqualTo(255);
        }
    }

    @Nested
    @DisplayName("premultiplying a colour")
    class Premultiplying {

        @Test
        @DisplayName("rounds each channel times alpha over 255 to nearest and keeps the alpha")
        void roundsEachChannel() {
            assertThat(SolidFill.premultiply(0x80FF0000)).isEqualTo(0x80800000);
            assertThat(SolidFill.premultiply(0x01FFFFFF)).isEqualTo(0x01010101);
            assertThat(SolidFill.premultiply(0x00FFFFFF)).isZero();
            assertThat(SolidFill.premultiply(0xFF7F8081)).isEqualTo(0xFF7F8081);
        }
    }
}
