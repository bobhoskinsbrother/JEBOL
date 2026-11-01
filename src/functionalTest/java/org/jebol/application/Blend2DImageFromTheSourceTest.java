package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.adapter.host.JavaImage;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.value.ImageValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class Blend2DImageFromTheSourceTest {

    private static final int ROWS = 154;

    private static final int TOLERANCE = 2;

    private static final Path IMAGES = Path.of("src", "testFixtures", "resources", "blend2d", "images");

    private record Row(String image, String source, String setup, String block, boolean isExact, int[] pixels) {
    }

    private static byte[] readTable() throws IOException {
        try (InputStream in = Objects.requireNonNull(
                Blend2DImageFromTheSourceTest.class.getResourceAsStream("/blend2d/blend2d-image.tsv"))) {
            return in.readAllBytes();
        }
    }

    private static List<Row> rowsOf(byte[] table) {
        return new String(table, StandardCharsets.UTF_8).lines().skip(1)
                .map(line -> line.split("\t", -1))
                .map(fields -> new Row(fields[0], fields[1], fields[2], fields[3], fields[4].equals("exact"),
                        parseWords(fields[6])))
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

    static Interpreter interpreterReachingImages() {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts()
                        .withFiles(FileSystemPort.rootFileSystem(IMAGES.toAbsolutePath()))
                        .withImages(new JavaImage()),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.FILES));
        String importing = "b2d: import 'blend2d";
        interpreter.defineNewWordsIn(importing);
        interpreter.run(importing);
        return interpreter;
    }

    private static int[] draw(Interpreter interpreter, Row row) {
        String source = (row.source().isEmpty() ? ""
                : "SOURCE: " + Blend2DDrawFromTheSourceTest.imageSource(row.source()) + "\n")
                + (row.setup().isEmpty() ? "" : row.setup().replace("image ", "b2d/image ")
                        .replace("path ", "b2d/path ") + "\n")
                + "b2d/draw " + Blend2DDrawFromTheSourceTest.imageSource(row.image()) + " [" + row.block() + "]";
        interpreter.defineNewWordsIn(source);
        return wordsOf((ImageValue) interpreter.run(source).value());
    }

    @Test
    @DisplayName("the table is the one the README describes: its row count and checksum")
    void isWhatItSays() throws IOException, NoSuchAlgorithmException {
        byte[] table = readTable();
        assertThat(rowsOf(table)).hasSize(ROWS);
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(table)))
                .isEqualTo("45b6875562c4618fb3c991dbff7d2956cd515bde676ba4051ec2cb96e5823797");
    }

    @Test
    @DisplayName("every blit lands within two levels a channel of what the extension's own C drew")
    void landsCloseToTheExtensionsOwnC() throws IOException {
        Interpreter interpreter = interpreterReachingImages();
        List<String> far = new ArrayList<>();
        for (Row row : rowsOf(readTable())) {
            int worst = measureWorstDifference(row.pixels(), draw(interpreter, row));
            if (worst > TOLERANCE) {
                far.add(row.source() + " " + row.setup() + " [" + row.block() + "] is off by " + worst);
            }
        }
        assertThat(far).isEmpty();
    }

    @Test
    @DisplayName("and every blit at whole pixels, from an R3 image or a lossless file, is exact")
    void isExactAtWholePixels() throws IOException {
        Interpreter interpreter = interpreterReachingImages();
        List<Row> rows = rowsOf(readTable()).stream().filter(Row::isExact).toList();
        List<String> inexact = new ArrayList<>();
        for (Row row : rows) {
            int worst = measureWorstDifference(row.pixels(), draw(interpreter, row));
            if (worst > 0) {
                inexact.add(row.source() + " " + row.setup() + " [" + row.block() + "] is off by " + worst);
            }
        }
        assertThat(rows).hasSize(121);
        assertThat(inexact).isEmpty();
    }

    private static String answerTo(String source) {
        Interpreter interpreter = interpreterReachingImages();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @ParameterizedTest(name = "{0} is format {1}, {2}")
    @CsvSource({
        "rgba.png, 1, 5x4", "rgb.png, 2, 5x4", "grey.png, 1, 5x4", "grey-alpha.png, 1, 5x4", "palette.png, 1, 5x4",
        "rgb.bmp, 2, 5x4", "alpha.bmp, 1, 5x4", "colour.jpg, 2, 16x16", "grey.jpg, 2, 16x16",
        "rgba.qoi, 1, 5x4", "rgb.qoi, 2, 5x4"})
    @DisplayName("an image read from a file has the format Blend2D's own decoder gives it: 2 where it has no alpha")
    void readsAFileAsItsFormat(String file, int format, String size) {
        String[] sides = size.split("x");
        assertThat(answerTo("b2d/info/of b2d/image %" + file)).isEqualTo("{width:  " + sides[0] + "\n"
                + "heigth: " + sides[1] + "\n"
                + "stride: " + Integer.parseInt(sides[0]) * 4 + "\n"
                + "format: " + format + "\n"
                + "flags:  0\n}");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"refused.gif", "refused.txt", "refused-v5.bmp", "missing.png"})
    @DisplayName("a GIF, a file that is no image, a V5 bitmap Blend2D's header check misses, and no file are refused")
    void refusesWhatBlend2DCannotRead(String file) {
        assertThat(answerTo("e: try [b2d/image %" + file + "] e/arg1"))
                .isEqualTo("\"Blend2D failed to make an image handle!\"");
    }

    @Test
    @DisplayName("draw stops where it meets an image it cannot read, keeps what it drew and raises nothing")
    void stopsAtAnImageItCannotRead() {
        assertThat(answerTo("""
                drawn: b2d/draw 4x4 [fill 255.0.0 box 0x0 2x2 image %missing.png 0x0 fill-all]
                reduce [drawn/(1x1) drawn/(4x4)]""")).isEqualTo("[255.0.0.255 255.255.255.255]");
    }

    @Test
    @DisplayName("a grey PNG drawn at whole pixels lands as the very levels image/load reads from it")
    void drawsGreyAsItIsStored() {
        assertThat(answerTo("""
                loaded: image/load %grey.png
                drawn: b2d/draw 5x4 [blend source-copy image %grey.png 0x0]
                reduce [equal? loaded drawn  loaded/(1x1)]""")).isEqualTo("[#(true) 206.206.206.255]");
    }

    @Test
    @DisplayName("an alpha bitmap's bytes are taken as already premultiplied, as Blend2D marks its alpha masks")
    void takesBitmapAlphaAsPremultiplied() {
        assertThat(answerTo("""
                loaded: image/load %alpha.bmp
                drawn: b2d/draw 5x4 [blend source-copy image %alpha.bmp 0x0]
                equal? loaded drawn""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a size after the corner scales the image to it, and the value after the size is skipped")
    void scalesToASizeAndSkipsTheValueAfterIt() {
        assertThat(answerTo("""
                one: make image! [1x1 10.20.30]
                drawn: b2d/draw 6x6 [image one 1x1 4x4 fill-all fill 0.0.255 box 0x0 1x1]
                reduce [drawn/(1x1) drawn/(3x3) drawn/(6x6)]""")).isEqualTo("[0.0.255.255 10.20.30.255 255.255.255.255]");
    }
}
