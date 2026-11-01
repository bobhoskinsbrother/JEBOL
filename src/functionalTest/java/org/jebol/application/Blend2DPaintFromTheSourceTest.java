package org.jebol.application;

import org.jebol.domain.value.ImageValue;
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.assertThat;

class Blend2DPaintFromTheSourceTest {

    private static final int ROWS = 65;

    private static final int TOLERANCE = 2;

    private record Row(String image, String pattern, String block, String size, int[] pixels) {
    }

    private static byte[] readTable() throws IOException {
        try (InputStream in = Objects.requireNonNull(
                Blend2DPaintFromTheSourceTest.class.getResourceAsStream("/blend2d/blend2d-paint.tsv"))) {
            return in.readAllBytes();
        }
    }

    private static List<Row> rowsOf(byte[] table) {
        return new String(table, StandardCharsets.UTF_8).lines().skip(1)
                .map(line -> line.split("\t", -1))
                .map(fields -> new Row(fields[0], fields[1], fields[2], fields[3], parseWords(fields[4])))
                .toList();
    }

    private static int[] parseWords(String words) {
        String[] parts = words.split(" ");
        int[] pixels = new int[parts.length];
        for (int at = 0; at < parts.length; at++) {
            pixels[at] = Integer.parseUnsignedInt(parts[at], 16);
        }
        return pixels;
    }

    private static int[] wordsOf(ImageValue image) {
        int count = image.storage().wide() * image.storage().high();
        int[] words = new int[count];
        for (int pixel = 0; pixel < count; pixel++) {
            int[] rgba = image.storage().readPixelAt(pixel + 1);
            words[pixel] = rgba[3] << 24 | rgba[0] << 16 | rgba[1] << 8 | rgba[2];
        }
        return words;
    }

    private static int measureWorstDifference(int[] expected, int[] drawn) {
        int worst = 0;
        for (int at = 0; at < expected.length; at++) {
            for (int shift = 0; shift < 32; shift += 8) {
                int difference = Math.abs(((expected[at] >>> shift) & 0xFF) - ((drawn[at] >>> shift) & 0xFF));
                worst = Math.max(worst, difference);
            }
        }
        return worst;
    }

    @Test
    @DisplayName("the table is the one the README describes: its row count and checksum")
    void isWhatItSays() throws IOException, NoSuchAlgorithmException {
        byte[] table = readTable();
        assertThat(rowsOf(table)).hasSize(ROWS);
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(table)))
                .isEqualTo("356bfb4a15bd7da1df94e934fd387ca24ce656930cccfc9168617f6956fbcfb8");
    }

    @Test
    @DisplayName("every gradient and pattern lands within two levels a channel of what the extension's own C drew")
    void landsCloseToTheExtensionsOwnC() throws IOException {
        Interpreter interpreter = AnInterpreter.create();
        String importing = "b2d: import 'blend2d";
        interpreter.defineNewWordsIn(importing);
        interpreter.run(importing);
        List<String> far = new ArrayList<>();
        for (Row row : rowsOf(readTable())) {
            String source = (row.pattern().isEmpty() ? ""
                    : "PATTERN: " + Blend2DDrawFromTheSourceTest.imageSource(row.pattern()) + "\n")
                    + "b2d/draw " + Blend2DDrawFromTheSourceTest.imageSource(row.image()) + " [" + row.block() + "]";
            interpreter.defineNewWordsIn(source);
            ImageValue drawn = (ImageValue) interpreter.run(source).value();
            int worst = measureWorstDifference(row.pixels(), wordsOf(drawn));
            if (worst > TOLERANCE) {
                far.add(row.image() + " [" + row.block() + "] is off by " + worst);
            }
        }
        assertThat(far).isEmpty();
    }
}
