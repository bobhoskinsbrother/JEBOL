package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.value.ImageValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class Blend2DTextFromTheSourceTest {

    private static final int ROWS = 112;

    private static final int GRADIENT_TOLERANCE = 2;

    private static final Path FONTS = Path.of("src", "testFixtures", "resources", "blend2d", "fonts");

    private record Row(String image, String setup, String block, boolean isExact, int[] pixels) {
    }

    private static byte[] readTable() throws IOException {
        try (InputStream in = Objects.requireNonNull(
                Blend2DTextFromTheSourceTest.class.getResourceAsStream("/blend2d/blend2d-text.tsv"))) {
            return in.readAllBytes();
        }
    }

    private static List<Row> rowsOf(byte[] table) {
        return new String(table, StandardCharsets.UTF_8).lines().skip(1)
                .map(line -> line.split("\t", -1))
                .map(fields -> new Row(fields[0], fields[2], fields[3], fields[4].equals("exact"),
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

    private static int countPixelsFurtherThan(int tolerance, int[] expected, int[] drawn) {
        int different = 0;
        for (int at = 0; at < expected.length; at++) {
            for (int shift = 0; shift < 32; shift += 8) {
                if (Math.abs(((expected[at] >>> shift) & 0xFF) - ((drawn[at] >>> shift) & 0xFF)) > tolerance) {
                    different++;
                    break;
                }
            }
        }
        return different;
    }

    @TempDir
    static Path fontFolder;

    private static final Map<String, String> KERN_FONT_CHECKSUMS = Map.of(
            "kern-windows", "0fb3ef9b587aac46903d380627c3beee12a036a6cfc640f09753478f6f74b3cb",
            "kern-unsorted", "3847819779bfd9ac2c81638710d31c7bb3bcd4efc255b88935e6bca836e9dc39",
            "kern-classes", "fffa40b25fbb8edafae40801582e8a34d62ee6c3476b38719196ef5f31f12fc6",
            "kern-mac", "79a9c8316350ac4e1c62d1ab88e4efdfcb41f8c05c44b319db7658399d58027f");

    private static final int ZEROS_AFTER_KERN = 65536;

    private static int readU16(byte[] bytes, int at) {
        return (bytes[at] & 0xFF) << 8 | (bytes[at + 1] & 0xFF);
    }

    private static byte[] withKernTable(byte[] font, byte[] kern) {
        int recordCount = readU16(font, 4);
        int padded = (font.length + 3) & ~3;
        byte[] out = Arrays.copyOf(font, padded + kern.length + ZEROS_AFTER_KERN);
        System.arraycopy(kern, 0, out, padded, kern.length);
        for (int record = 0; record < recordCount; record++) {
            int at = 12 + record * 16;
            String tag = new String(font, at, 4, StandardCharsets.US_ASCII);
            if (tag.equals("GPOS")) {
                System.arraycopy("gpos".getBytes(StandardCharsets.US_ASCII), 0, out, at, 4);
            } else if (tag.equals("STAT")) {
                System.arraycopy("kern".getBytes(StandardCharsets.US_ASCII), 0, out, at, 4);
                ByteBuffer.wrap(out, at + 8, 8).putInt(padded).putInt(kern.length);
            }
        }
        return out;
    }

    @BeforeAll
    static void layOutFonts() throws IOException, NoSuchAlgorithmException {
        Files.copy(FONTS.resolve("NotoSans-Regular.ttf"), fontFolder.resolve("NotoSans-Regular.ttf"));
        Files.copy(FONTS.resolve("NotoSans-Regular.otf"), fontFolder.resolve("NotoSans-Regular.otf"));
        byte[] carried = Files.readAllBytes(FONTS.resolve("NotoSans-Regular.ttf"));
        for (Map.Entry<String, String> kernFont : KERN_FONT_CHECKSUMS.entrySet()) {
            byte[] built = withKernTable(carried, Files.readAllBytes(FONTS.resolve(kernFont.getKey() + ".kern")));
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(built)))
                    .isEqualTo(kernFont.getValue());
            Files.write(fontFolder.resolve(kernFont.getKey() + ".ttf"), built);
        }
    }

    static Interpreter interpreterReachingFonts() {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withFiles(FileSystemPort.rootFileSystem(fontFolder.toAbsolutePath())),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.FILES));
        String importing = "b2d: import 'blend2d";
        interpreter.defineNewWordsIn(importing);
        interpreter.run(importing);
        return interpreter;
    }

    private static int[] draw(Interpreter interpreter, Row row) {
        String source = (row.setup().isEmpty() ? ""
                : row.setup().replace("font ", "b2d/font ").replace("path ", "b2d/path ") + "\n")
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
                .isEqualTo("bc0f2bf28e1b4175d8241cda39b7e4f59e1af38caa7678f281e73c3063b9f539");
    }

    @Test
    @DisplayName("every text in a solid colour draws byte for byte what the extension's own C drew with the same font")
    void drawsWhatTheExtensionsOwnCDrew() throws IOException {
        Interpreter interpreter = interpreterReachingFonts();
        List<Row> rows = rowsOf(readTable());
        List<String> different = new ArrayList<>();
        for (Row row : rows) {
            int count = countPixelsFurtherThan(row.isExact() ? 0 : GRADIENT_TOLERANCE, row.pixels(),
                    draw(interpreter, row));
            if (count > 0) {
                different.add(row.setup() + " [" + row.block() + "] differs in " + count + " pixels");
            }
        }
        assertThat(rows.stream().filter(Row::isExact)).hasSize(ROWS - 1);
        assertThat(different).isEmpty();
    }

    private static String answerTo(String source) {
        Interpreter interpreter = interpreterReachingFonts();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a font handle describes its face as Blend2D does: TrueType outlines, 3748 glyphs and its flags")
    void describesAFontFace() {
        assertThat(answerTo("b2d/info/of b2d/font %NotoSans-Regular.ttf")).isEqualTo("""
                {faceType:    1
                outlineType: 1
                glyphCount:  3748
                revision:    131596
                faceIndex:   0
                faceFlags:   13828
                diagFlags:   0
                }""");
    }

    @Test
    @DisplayName("an OpenType font with CFF outlines describes itself as outline type 2, with typographic metrics")
    void describesACffFontFace() {
        assertThat(answerTo("b2d/info/of b2d/font %NotoSans-Regular.otf")).isEqualTo("""
                {faceType:    1
                outlineType: 2
                glyphCount:  3884
                revision:    132055
                faceIndex:   0
                faceFlags:   13830
                diagFlags:   0
                }""");
    }

    @Test
    @DisplayName("a font that cannot be read is refused by the name of the call that failed")
    void refusesAFontItCannotRead() {
        assertThat(answerTo("e: try [b2d/font %missing.ttf] e/arg1"))
                .isEqualTo("\"Blend2D's blFontFaceCreateFromFile failed!\"");
    }

    @Test
    @DisplayName("AV is A, then V moved by A's advance of 639 and the pair's kern of -40, at 15.625 a 64th of a unit each")
    void kernsAPair() {
        assertThat(answerTo("""
                together: b2d/draw 40x20 [fill 0.0.0 font %NotoSans-Regular.ttf text 2x15 15.625 {AV}]
                kerned: b2d/draw 40x20 [fill 0.0.0 font %NotoSans-Regular.ttf text 2x15 15.625 {A}
                    text 11.359375x15 {V}]
                unkerned: b2d/draw 40x20 [fill 0.0.0 font %NotoSans-Regular.ttf text 2x15 15.625 {A}
                    text 11.984375x15 {V}]
                reduce [together = kerned  together = unkerned]""")).isEqualTo("[#(true) #(false)]");
    }

    @Test
    @DisplayName("with no GPOS kerning, a legacy kern table's pair of -80 moves V by A's advance less 80 units")
    void kernsFromALegacyTable() {
        assertThat(answerTo("""
                together: b2d/draw 40x20 [fill 0.0.0 font %kern-windows.ttf text 2x15 15.625 {AV}]
                placed: b2d/draw 40x20 [fill 0.0.0 font %kern-windows.ttf text 2x15 15.625 {A}
                    text 10.734375x15 {V}]
                together = placed""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a class kerning subtable is read as pairs, as Blend2D packs its format where it reads flags, so only -30 applies")
    void readsEveryKernSubTableAsPairs() {
        assertThat(answerTo("""
                together: b2d/draw 40x20 [fill 0.0.0 font %kern-classes.ttf text 2x15 15.625 {AV}]
                placed: b2d/draw 40x20 [fill 0.0.0 font %kern-classes.ttf text 2x15 15.625 {A}
                    text 11.515625x15 {V}]
                together = placed""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("text is drawn only in a font that was given, and a size given later rebuilds the font from the face")
    void drawsOnlyInAGivenFont() {
        assertThat(answerTo("""
                blank: make image! [30x20 255.255.255]
                none-given: b2d/draw 30x20 [fill 0.0.0 text 2x15 14 {Ok}]
                given: b2d/draw 30x20 [fill 0.0.0 font %NotoSans-Regular.ttf text 2x15 14 {Ok}]
                reset: b2d/draw 30x20 [fill 0.0.0 font %NotoSans-Regular.ttf font 3 text 2x15 {Ok}]
                rebuilt: b2d/draw 30x20 [fill 0.0.0 font %NotoSans-Regular.ttf font 3 text 2x15 14 {Ok}]
                reduce [none-given = blank  given = blank  reset = blank  rebuilt = given]"""))
                .isEqualTo("[#(true) #(false) #(true) #(true)]");
    }
}
