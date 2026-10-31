package org.jebol.application;

import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.Value;
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
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

class Blend2DDrawFromTheSourceTest {

    private static final int ROWS = 1187;

    private record Row(String image, String block, String size, long crc) {
    }

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

    private static byte[] readTable() throws IOException {
        try (InputStream in = Objects.requireNonNull(
                Blend2DDrawFromTheSourceTest.class.getResourceAsStream("/blend2d/blend2d-draw.tsv"))) {
            return in.readAllBytes();
        }
    }

    private static List<Row> rowsOf(byte[] table) {
        return new String(table, StandardCharsets.UTF_8).lines().skip(1)
                .map(line -> line.split("\t", -1))
                .map(fields -> new Row(fields[0], fields[1], fields[2], Long.parseLong(fields[3], 16)))
                .toList();
    }

    private static String imageSource(String image) {
        String[] parts = image.split(" ");
        if (parts[0].equals("pair")) {
            return parts[1];
        }
        String[] size = parts[1].split("x");
        int wide = Integer.parseInt(size[0]);
        int high = Integer.parseInt(size[1]);
        Noise noise = new Noise(Integer.parseInt(parts[2]));
        StringBuilder colours = new StringBuilder();
        StringBuilder alphas = new StringBuilder();
        HexFormat hex = HexFormat.of();
        for (int pixel = 0; pixel < wide * high; pixel++) {
            int word = noise.next();
            colours.append(hex.toHexDigits((byte) (word >>> 16))).append(hex.toHexDigits((byte) (word >>> 8)))
                    .append(hex.toHexDigits((byte) word));
            alphas.append(hex.toHexDigits((byte) (word >>> 24)));
        }
        return "make image! [" + wide + "x" + high + " #{" + colours + "} #{" + alphas + "}]";
    }

    private static Interpreter interpreterWithBlend2D() {
        Interpreter interpreter = AnInterpreter.create();
        String importing = "b2d: import 'blend2d";
        interpreter.defineNewWordsIn(importing);
        interpreter.run(importing);
        return interpreter;
    }

    private static Value runCase(Interpreter interpreter, Row row) {
        String source = "b2d/draw " + imageSource(row.image()) + " [" + row.block() + "]";
        interpreter.defineNewWordsIn(source);
        return interpreter.run(source).value();
    }

    private static long crcOf(ImageValue image) {
        int count = image.storage().wide() * image.storage().high();
        byte[] bytes = new byte[count * 4];
        for (int pixel = 0; pixel < count; pixel++) {
            int[] rgba = image.storage().readPixelAt(pixel + 1);
            bytes[4 * pixel] = (byte) rgba[2];
            bytes[4 * pixel + 1] = (byte) rgba[1];
            bytes[4 * pixel + 2] = (byte) rgba[0];
            bytes[4 * pixel + 3] = (byte) rgba[3];
        }
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return crc.getValue();
    }

    private static String answerTo(String source) {
        Interpreter interpreter = interpreterWithBlend2D();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("the table")
    class TheTable {

        @Test
        @DisplayName("is the one the README describes: its row count and checksum")
        void isWhatItSays() throws IOException, NoSuchAlgorithmException {
            byte[] table = readTable();
            assertThat(rowsOf(table)).hasSize(ROWS);
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(table)))
                    .isEqualTo("2b1dfae1e1d654d0a3dd1a4cb1aa53d12e9a40faf12c297eadb0c1af49413941");
        }

        @Test
        @DisplayName("draws every row the extension's own C drew, pixel for pixel, but the two whose edges hang on glibc's pow")
        void drawsEveryRow() throws IOException {
            Interpreter interpreter = interpreterWithBlend2D();
            List<String> differing = new ArrayList<>();
            for (Row row : rowsOf(readTable())) {
                if (HANG_ON_GLIBCS_POW.contains(row.block())) {
                    continue;
                }
                Value drawn;
                try {
                    drawn = runCase(interpreter, row);
                } catch (RuntimeException refused) {
                    differing.add(row.image() + " [" + row.block() + "] raised " + refused.getMessage());
                    continue;
                }
                if (!(drawn instanceof ImageValue image)) {
                    differing.add(row.image() + " [" + row.block() + "] answered " + drawn);
                    continue;
                }
                String size = image.storage().wide() + "x" + image.storage().high();
                if (!size.equals(row.size()) || crcOf(image) != row.crc()) {
                    differing.add(row.image() + " [" + row.block() + "] drew " + size);
                }
            }
            assertThat(differing).isEmpty();
        }

        @Test
        @DisplayName("draws the two rows whose edges hang on glibc's pow, an image of the right size, without an error")
        void drawsTheRowsThatHangOnGlibcsPow() throws IOException {
            Interpreter interpreter = interpreterWithBlend2D();
            List<String> drawn = new ArrayList<>();
            for (Row row : rowsOf(readTable())) {
                if (HANG_ON_GLIBCS_POW.contains(row.block())) {
                    ImageValue image = (ImageValue) runCase(interpreter, row);
                    drawn.add(image.storage().wide() + "x" + image.storage().high());
                }
            }
            assertThat(drawn).containsExactly("40x30", "40x30");
        }
    }

    private static final List<String> HANG_ON_GLIBCS_POW = List.of(
            "pen 200.30.40.220 line-join miter round line-cap 5 cubic 2x15 15x2 15x28 2x15",
            "pen 200.30.40.220 line-width 4 line-join round line-cap 5 1 cubic 2x15 15x2 15x28 2x15");

    @Nested
    @DisplayName("the module")
    class TheModule {

        @Test
        @DisplayName("is the release's: its name, version, exports, the words it was given, and init-words hidden")
        void isTheReleases() {
            assertThat(answerTo("""
                    reduce [b2d/words/1 length? b2d/words in b2d 'init-words select spec-of b2d 'version select spec-of b2d 'exports]"""))
                    .isEqualTo("[move 40 _ 0.11.5.0 [draw]]");
        }

        @Test
        @DisplayName("an empty block leaves the image as it was")
        void anEmptyBlockDrawsNothing() {
            assertThat(answerTo("""
                    img: make image! [2x1 1.2.3.4]
                    img: b2d/draw img []
                    reduce [img/1 img/2]""")).isEqualTo("[1.2.3.4 1.2.3.4]");
        }

        @Test
        @DisplayName("draw onto a pair makes an opaque white image of that size, rounded, and draws on it")
        void drawsOntoANewWhiteImage() {
            assertThat(answerTo("""
                    img: b2d/draw 3.4x1.6 [fill 255.0.0 box 0x0 1x1]
                    reduce [img/size img/1 img/2]""")).isEqualTo("[3x2 255.0.0.255 255.255.255.255]");
        }

        @Test
        @DisplayName("draw onto an image draws into that image and answers it")
        void drawsIntoTheImageItIsGiven() {
            assertThat(answerTo("""
                    img: make image! [2x1 255.255.255]
                    drawn: b2d/draw img [fill 0.0.255 box 1x0 1x1]
                    reduce [same? img drawn img/2]""")).isEqualTo("[#(true) 0.0.255.255]");
        }

        @Test
        @DisplayName("an image of no size cannot be drawn on, as blImageCreateFromData refuses it")
        void refusesAnEmptyImage() {
            assertThat(answerTo("""
                    e: try [b2d/draw 0x0 [fill-all]]
                    reduce [e/arg1 'end]""")).isEqualTo("""
                    ["Blend2D's blImageCreateFromData failed!" end]""");
        }

        @Test
        @DisplayName("image from a pair fails, as the C reads the pair's floats as integers")
        void anImageFromAPairFails() {
            assertThat(answerTo("""
                    e: try [b2d/image 3x2]
                    reduce [e/arg1 'end]""")).isEqualTo("""
                    ["Blend2D failed to make an image handle!" end]""");
        }

        @Test
        @DisplayName("image from an image is a handle, and info/of tells its size, stride and format, heigth and all")
        void describesAnImageHandle() {
            assertThat(answerTo("""
                    h: b2d/image make image! [3x2]
                    reduce [type? h b2d/info/of h]"""))
                    .isEqualTo("""
                            [#(handle!) {width:  3
                            heigth: 2
                            stride: 12
                            format: 1
                            flags:  0
                            }]""");
        }

        @Test
        @DisplayName("set-threads answers its count and changes nothing a script can see")
        void setsThreads() {
            assertThat(answerTo("b2d/set-threads 4")).isEqualTo("4");
        }

        @Test
        @DisplayName("info names the Blend2D version the port follows")
        void namesTheVersion() {
            assertThat(answerTo("reduce [copy/part b2d/info 25 'end]")).isEqualTo("""
                    ["Version:     0.12.0^/Build" end]""");
        }

        @Test
        @DisplayName("triangles read from a vector of 64-bit decimals draw what the same corners given as pairs draw")
        void drawsTrianglesFromAVector() {
            assertThat(answerTo("""
                    from-pairs: b2d/draw 12x10 [fill 200.0.0 triangle 1.5x1 11x2.5 4x9.5 9x9 2x6 10x5]
                    corners: make vector! [decimal! 64 [1.5 1 11 2.5 4 9.5 9 9 2 6 10 5]]
                    from-vector: b2d/draw 12x10 [fill 200.0.0 triangle corners]
                    reduce [equal? from-pairs from-vector  equal? from-pairs make image! [12x10 255.255.255]]"""))
                    .isEqualTo("[#(true) #(false)]");
        }

        @Test
        @DisplayName("triangles read from a vector of narrower numbers are refused, as the C would read past them as doubles")
        void refusesTrianglesFromANarrowVector() {
            assertThat(answerTo("""
                    corners: make vector! [integer! 32 [1 1 11 2 4 9]]
                    e: try [b2d/draw 12x10 [fill 200.0.0 triangle corners]]
                    e/id""")).isEqualTo("command-fail");
        }

        @Test
        @DisplayName("a polygon with a pen is filled first and stroked over the fill, as the C fills before it strokes")
        void fillsAPolygonThenStrokesItsOutline() {
            assertThat(answerTo("""
                    img: make image! [8x8 255.255.255]
                    b2d/draw img [fill 0.0.255 pen 255.0.0 line-width 2 polygon 1x1 7x1 7x7 1x7]
                    reduce [img/(4x4) img/(1x4) img/(0x4)]"""))
                    .isEqualTo("[0.0.255.255 255.0.0.255 255.0.0.255]");
        }

        @Test
        @DisplayName("a line of points from a vector, joined by the edges another vector names, strokes what the same points as pairs stroke")
        void strokesLinesJoinedByEdges() {
            assertThat(answerTo("""
                    from-pairs: b2d/draw 20x20 [pen 0.0.0 line-width 2 line 2x2 18x2 18x18]
                    points: make vector! [decimal! 64 [2 2 18 2 18 18]]
                    edges: make vector! [integer! 32 [0 1 1 2]]
                    from-vector: b2d/draw 20x20 [pen 0.0.0 line-width 2 line points edges]
                    reduce [equal? from-pairs from-vector  from-pairs/(10x2)]"""))
                    .isEqualTo("[#(true) 0.0.0.255]");
        }

        @Test
        @DisplayName("what is not ported yet is refused by name, not drawn wrongly")
        void refusesWhatItCannotDrawYet() {
            assertThat(answerTo("""
                    e: try [b2d/draw 4x4 [fill 255.0.0 point 2x2]]
                    reduce [e/id e/arg1]"""))
                    .isEqualTo("""
                            [command-fail {blend2d cannot yet draw a point in JEBOL: that part of Blend2D is not ported}]""");
        }

        @Test
        @DisplayName("a path made once draws what the same commands given to shape draw, and can be drawn again and again")
        void drawsAPathHandleAsItsBlock() {
            assertThat(answerTo("""
                    commands: [move 3x10 qcurve 8x2 13x10 qcurv 23x10 arc 3x10 10 6 0 sweep close]
                    handle: b2d/path commands
                    from-block: b2d/draw 26x14 [fill 200.0.0 pen 0.0.0 shape commands]
                    from-handle: b2d/draw 26x14 [fill 200.0.0 pen 0.0.0 shape handle]
                    twice: b2d/draw 26x14 [fill 200.0.0 pen 0.0.0 shape handle]
                    reduce [type? handle  equal? from-block from-handle  equal? from-handle twice
                        equal? from-block make image! [26x14 255.255.255]]"""))
                    .isEqualTo("[#(handle!) #(true) #(true) #(false)]");
        }

        @Test
        @DisplayName("a path handle is a BLPath, and shape refuses a handle of any other kind as the C does")
        void refusesAHandleThatIsNotAPath() {
            assertThat(answerTo("""
                    img: b2d/image make image! [2x2]
                    drawn: b2d/draw 6x6 [fill 200.0.0 shape img fill-all]
                    reduce [drawn/(1x1)  (form b2d/path [move 1x1])]"""))
                    .isEqualTo("""
                            [200.0.0.255 "#(handle! BLPath)"]""");
        }

        @Test
        @DisplayName("a circle given a second radius is an ellipse, as the C draws an ellipse when the optional radius is there")
        void drawsACircleWithTwoRadiiAsAnEllipse() {
            assertThat(answerTo("""
                    circle: b2d/draw 20x14 [fill 200.0.0 circle 10x7 8 5]
                    ellipse: b2d/draw 20x14 [fill 200.0.0 ellipse 2x2 16x10]
                    reduce [equal? circle ellipse  circle/(10x7)  circle/(10x1)]"""))
                    .isEqualTo("[#(true) 200.0.0.255 255.255.255.255]");
        }
    }
}
