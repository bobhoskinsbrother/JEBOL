package org.jebol.adapter.host;

import org.jebol.domain.host.ImagePort.Pixels;
import org.jebol.domain.image.GifDecoder;
import org.jebol.domain.image.GifEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TheCoresGifCodecAgreesWithImageIoTest {

    private static final Path REBOLS_OWN = Path.of("src/testFixtures/resources/rebol-suite/units/files");

    private static int[] palette(int entries) {
        int[] palette = new int[entries];
        for (int at = 0; at < entries; at++) {
            palette[at] = ((at * 53) & 0xFF) << 16 | ((at * 97 + 31) & 0xFF) << 8 | ((at * 191 + 7) & 0xFF);
        }
        return palette;
    }

    private static byte[] noise(int count, int colours, long seed) {
        byte[] indices = new byte[count];
        long state = seed;
        for (int at = 0; at < count; at++) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            indices[at] = (byte) ((state >>> 33) % colours);
        }
        return indices;
    }

    private static byte[] filled(int count, int index) {
        byte[] indices = new byte[count];
        java.util.Arrays.fill(indices, (byte) index);
        return indices;
    }

    private static int frameCount(byte[] gif) throws IOException {
        ImageReader reader = readerFor(gif);
        try {
            return reader.getNumImages(true);
        } finally {
            reader.dispose();
        }
    }

    private static ImageReader readerFor(byte[] gif) throws IOException {
        ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(gif));
        Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
        ImageReader reader = readers.next();
        reader.setInput(stream);
        return reader;
    }

    private static BufferedImage imageIoFrame(byte[] gif, int frame) throws IOException {
        ImageReader reader = readerFor(gif);
        try {
            BufferedImage first = reader.read(0);
            if (frame == 1) {
                return first;
            }
            BufferedImage canvas = new BufferedImage(first.getWidth(), first.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D onto = canvas.createGraphics();
            onto.drawImage(first, 0, 0, null);
            for (int step = 1; step < frame; step++) {
                Node descriptor = reader.getImageMetadata(step).getAsTree("javax_imageio_gif_image_1.0").getFirstChild();
                while (!descriptor.getNodeName().equals("ImageDescriptor")) {
                    descriptor = descriptor.getNextSibling();
                }
                int left = Integer.parseInt(descriptor.getAttributes().getNamedItem("imageLeftPosition").getNodeValue());
                int top = Integer.parseInt(descriptor.getAttributes().getNamedItem("imageTopPosition").getNodeValue());
                onto.drawImage(reader.read(step), left, top, null);
            }
            onto.dispose();
            return canvas;
        } finally {
            reader.dispose();
        }
    }

    private static void assertSamePicture(Pixels ours, BufferedImage theirs, String which) {
        assertThat(ours.wide()).as(which + " width").isEqualTo(theirs.getWidth());
        assertThat(ours.high()).as(which + " height").isEqualTo(theirs.getHeight());
        byte[] rgba = ours.rgba();
        for (int down = 0; down < ours.high(); down++) {
            for (int across = 0; across < ours.wide(); across++) {
                int argb = theirs.getRGB(across, down);
                int at = (down * ours.wide() + across) * 4;
                int ourAlpha = rgba[at + 3] & 0xFF;
                int theirAlpha = argb >>> 24;
                assertThat(ourAlpha).as(which + " alpha at " + across + "x" + down).isEqualTo(theirAlpha);
                if (theirAlpha != 0) {
                    int ourRgb = (rgba[at] & 0xFF) << 16 | (rgba[at + 1] & 0xFF) << 8 | (rgba[at + 2] & 0xFF);
                    assertThat(Integer.toHexString(ourRgb)).as(which + " colour at " + across + "x" + down)
                            .isEqualTo(Integer.toHexString(argb & 0xFFFFFF));
                }
            }
        }
    }

    private static void assertEveryFrameAgrees(byte[] gif, String which) throws IOException {
        int frames = frameCount(gif);
        for (int frame = 1; frame <= frames; frame++) {
            Optional<Pixels> ours = GifDecoder.decodeFrame(gif, frame);
            assertThat(ours).as(which + " frame " + frame).isPresent();
            assertSamePicture(ours.orElseThrow(), imageIoFrame(gif, frame), which + " frame " + frame);
        }
        assertThat(GifDecoder.decodeFrame(gif, frames + 1)).as(which + " past its last frame").isEmpty();
    }

    @ParameterizedTest(name = "LZW minimum code size {0}")
    @ValueSource(ints = {2, 3, 4, 5, 6, 7, 8})
    @DisplayName("each LZW minimum code size from 2 to 8 decodes as ImageIO decodes it")
    void eachCodeSize(int codeSize) throws IOException {
        int colours = 1 << codeSize;
        HandBuiltGif built = HandBuiltGif.screen(96, 96).globalPalette(palette(colours))
                .frame(HandBuiltGif.whole(96, 96, noise(96 * 96, colours, codeSize), codeSize));
        byte[] gif = built.bytes();

        assertThat(gif[13 + 3 * colours + 8 + 10]).as("the code size byte written").isEqualTo((byte) codeSize);
        assertThat(built.written().getFirst().widestCode()).as("codes grow to twelve bits").isEqualTo(12);
        assertEveryFrameAgrees(gif, "code size " + codeSize);
    }

    @Test
    @DisplayName("a stream that clears its table when it fills decodes as ImageIO decodes it")
    void aClearCodeMidStream() throws IOException {
        HandBuiltGif built = HandBuiltGif.screen(128, 128).globalPalette(palette(256))
                .frame(HandBuiltGif.whole(128, 128, noise(128 * 128, 256, 11), 8));
        byte[] gif = built.bytes();

        assertThat(built.written().getFirst().clearsAfterTheFirst()).as("clears after the first").isPositive();
        assertEveryFrameAgrees(gif, "a clear mid-stream");
    }

    @Test
    @DisplayName("a stream whose table fills at 4,096 and goes on with no clear decodes as ImageIO decodes it")
    void aFullTableWithNoClear() throws IOException {
        HandBuiltGif built = HandBuiltGif.screen(128, 128).globalPalette(palette(16))
                .frame(HandBuiltGif.deferringClear(HandBuiltGif.whole(128, 128, noise(128 * 128, 16, 5), 4)));
        byte[] gif = built.bytes();

        assertThat(built.written().getFirst().clearsAfterTheFirst()).isZero();
        assertThat(built.written().getFirst().codesWrittenWithTableFull()).as("codes with the table full")
                .isGreaterThan(100);
        assertEveryFrameAgrees(gif, "a full table with no clear");
    }

    @ParameterizedTest(name = "interlaced, {0} rows high")
    @ValueSource(ints = {1, 2, 3, 4, 5, 7, 8, 9, 16, 17})
    @DisplayName("an interlaced frame of every height around the passes' steps puts each row back where it was")
    void interlacedFramesPutEachRowBack(int high) {
        byte[] rows = noise(5 * high, 4, high);
        int[] colours = palette(4);
        byte[] gif = HandBuiltGif.screen(5, high).globalPalette(colours)
                .frame(HandBuiltGif.interlaced(HandBuiltGif.whole(5, high, rows, 2))).bytes();

        assertThat(gif[13 + 12 + 8 + 9] & 0x40).as("the interlace flag").isEqualTo(0x40);
        Pixels decoded = GifDecoder.decodeFrame(gif, 1).orElseThrow();
        for (int at = 0; at < rows.length; at++) {
            assertThat(pixelAt(decoded, at)).as("pixel " + at).isEqualTo(opaque(colours[rows[at]]));
        }
    }

    @ParameterizedTest(name = "interlaced, {0} rows high")
    @ValueSource(ints = {5, 7, 8, 9, 16, 17})
    @DisplayName("an interlaced frame five rows high or more decodes as ImageIO decodes it")
    void interlacedFramesAgreeWithImageIo(int high) throws IOException {
        byte[] gif = HandBuiltGif.screen(5, high).globalPalette(palette(4))
                .frame(HandBuiltGif.interlaced(HandBuiltGif.whole(5, high, noise(5 * high, 4, high), 2))).bytes();

        assertEveryFrameAgrees(gif, "interlaced " + high);
    }

    @Test
    @DisplayName("ImageIO misplaces the rows of an interlaced frame under five rows high, which the core does not")
    void imageIoMisreadsShortInterlacedFrames() throws IOException {
        int[] colours = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00};
        byte[] gif = HandBuiltGif.screen(1, 3).globalPalette(colours)
                .frame(HandBuiltGif.interlaced(HandBuiltGif.whole(1, 3, new byte[] {0, 1, 2}, 2))).bytes();

        BufferedImage theirs = ImageIO.read(new ByteArrayInputStream(gif));
        Pixels ours = GifDecoder.decodeFrame(gif, 1).orElseThrow();

        assertThat(theirs.getRGB(0, 1) & 0xFFFFFF).isEqualTo(0xFF0000);
        assertThat(pixelAt(ours, 1)).isEqualTo(opaque(0x00FF00));
        assertThat(pixelAt(ours, 2)).isEqualTo(opaque(0x0000FF));
    }

    @Test
    @DisplayName("a frame with a local palette and none global decodes as ImageIO decodes it")
    void aLocalPaletteAlone() throws IOException {
        byte[] gif = HandBuiltGif.screen(8, 8)
                .frame(HandBuiltGif.withLocalPalette(HandBuiltGif.whole(8, 8, noise(64, 8, 3), 3), palette(8)))
                .bytes();

        assertThat(gif[10] & 0x80).as("no global palette").isZero();
        assertEveryFrameAgrees(gif, "a local palette alone");
    }

    @Test
    @DisplayName("a local palette takes the place of the global one for its own frame only")
    void aLocalPaletteOverTheGlobal() throws IOException {
        int[] other = palette(16);
        for (int at = 0; at < other.length; at++) {
            other[at] = ~other[at] & 0xFFFFFF;
        }
        byte[] gif = HandBuiltGif.screen(8, 8).globalPalette(palette(16))
                .frame(HandBuiltGif.whole(8, 8, noise(64, 16, 1), 4))
                .frame(HandBuiltGif.withLocalPalette(HandBuiltGif.placed(2, 2, 4, 4, noise(16, 16, 2), 4, 1, -1), other))
                .frame(HandBuiltGif.placed(4, 4, 4, 4, noise(16, 16, 3), 4, 1, -1))
                .bytes();

        assertEveryFrameAgrees(gif, "local over global");
    }

    @Test
    @DisplayName("a frame with neither palette paints black, where the specification leaves it to the decoder")
    void neitherPalette() {
        byte[] gif = HandBuiltGif.screen(2, 1).frame(HandBuiltGif.whole(2, 1, new byte[] {0, 1}, 2)).bytes();

        Pixels decoded = GifDecoder.decodeFrame(gif, 1).orElseThrow();

        assertThat(decoded.rgba()).containsExactly(0, 0, 0, -1, 0, 0, 0, -1);
    }

    @Test
    @DisplayName("a transparent index shows what is beneath it, on the first frame and on a later one")
    void transparency() throws IOException {
        byte[] gif = HandBuiltGif.screen(6, 6).globalPalette(palette(4))
                .frame(HandBuiltGif.placed(0, 0, 6, 6, noise(36, 4, 9), 2, 1, 2))
                .frame(HandBuiltGif.placed(1, 1, 4, 4, noise(16, 4, 10), 2, 1, 0))
                .bytes();

        assertEveryFrameAgrees(gif, "transparency");
    }

    @Test
    @DisplayName("frames smaller than the screen and offset within it compose as ImageIO composes them, under disposal 0 and 1")
    void offsetFramesUnderDisposalNoneAndKeep() throws IOException {
        byte[] gif = HandBuiltGif.screen(10, 8).globalPalette(palette(8))
                .frame(HandBuiltGif.whole(10, 8, filled(80, 1), 3))
                .frame(HandBuiltGif.placed(3, 2, 4, 3, filled(12, 2), 3, 0, -1))
                .frame(HandBuiltGif.placed(6, 5, 4, 3, filled(12, 3), 3, 1, -1))
                .frame(HandBuiltGif.placed(0, 0, 2, 2, filled(4, 4), 3, 1, -1))
                .bytes();

        assertEveryFrameAgrees(gif, "offset frames");
    }

    @Test
    @DisplayName("disposal 2 clears the frame's own rectangle to transparent before the next frame")
    void disposalRestoresTheBackground() {
        int[] colours = palette(4);
        byte[] gif = HandBuiltGif.screen(3, 1).globalPalette(colours)
                .frame(HandBuiltGif.whole(3, 1, filled(3, 1), 2))
                .frame(HandBuiltGif.placed(1, 0, 1, 1, filled(1, 2), 2, 2, -1))
                .frame(HandBuiltGif.placed(2, 0, 1, 1, filled(1, 3), 2, 1, -1))
                .bytes();

        Pixels third = GifDecoder.decodeFrame(gif, 3).orElseThrow();

        assertThat(pixelAt(third, 0)).isEqualTo(opaque(colours[1]));
        assertThat(pixelAt(third, 1)).isEqualTo(0);
        assertThat(pixelAt(third, 2)).isEqualTo(opaque(colours[3]));
    }

    @Test
    @DisplayName("disposal 3 puts back what was there before the frame, before the next frame")
    void disposalRestoresThePrevious() {
        int[] colours = palette(4);
        byte[] gif = HandBuiltGif.screen(3, 1).globalPalette(colours)
                .frame(HandBuiltGif.whole(3, 1, filled(3, 1), 2))
                .frame(HandBuiltGif.placed(0, 0, 2, 1, filled(2, 2), 2, 3, -1))
                .frame(HandBuiltGif.placed(2, 0, 1, 1, filled(1, 3), 2, 1, -1))
                .bytes();

        Pixels second = GifDecoder.decodeFrame(gif, 2).orElseThrow();
        Pixels third = GifDecoder.decodeFrame(gif, 3).orElseThrow();

        assertThat(pixelAt(second, 0)).isEqualTo(opaque(colours[2]));
        assertThat(pixelAt(third, 0)).isEqualTo(opaque(colours[1]));
        assertThat(pixelAt(third, 1)).isEqualTo(opaque(colours[1]));
        assertThat(pixelAt(third, 2)).isEqualTo(opaque(colours[3]));
    }

    @Test
    @DisplayName("a frame disposing by 1 leaves itself for the next, as a frame disposing by 0 does")
    void disposalKeep() {
        int[] colours = palette(4);
        byte[] gif = HandBuiltGif.screen(2, 1).globalPalette(colours)
                .frame(HandBuiltGif.placed(0, 0, 2, 1, filled(2, 1), 2, 1, -1))
                .frame(HandBuiltGif.placed(1, 0, 1, 1, filled(1, 2), 2, 0, -1))
                .frame(HandBuiltGif.placed(0, 0, 1, 1, filled(1, 3), 2, 0, -1))
                .bytes();

        Pixels third = GifDecoder.decodeFrame(gif, 3).orElseThrow();

        assertThat(pixelAt(third, 0)).isEqualTo(opaque(colours[3]));
        assertThat(pixelAt(third, 1)).isEqualTo(opaque(colours[2]));
    }

    @ParameterizedTest(name = "{0} frames")
    @ValueSource(ints = {1, 2, 100})
    @DisplayName("one frame, two and a hundred each decode as ImageIO decodes them")
    void frameCounts(int frames) throws IOException {
        HandBuiltGif built = HandBuiltGif.screen(12, 12).globalPalette(palette(16))
                .frame(HandBuiltGif.whole(12, 12, noise(144, 16, 0), 4));
        for (int frame = 1; frame < frames; frame++) {
            built.frame(HandBuiltGif.placed(frame % 9, (frame * 7) % 9, 3, 3, noise(9, 16, frame), 4, 1,
                    frame % 3 == 0 ? 5 : -1));
        }
        byte[] gif = built.bytes();

        assertThat(frameCount(gif)).isEqualTo(frames);
        assertEveryFrameAgrees(gif, frames + " frames");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"flower.gif", "rbgw.gif", "animation.gif"})
    @DisplayName("Rebol's own GIFs decode, every frame, as ImageIO decodes them")
    void rebolsOwnGifs(String name) throws IOException {
        assertEveryFrameAgrees(Files.readAllBytes(REBOLS_OWN.resolve(name)), name);
    }

    @Test
    @DisplayName("bytes that are not a GIF, or stop short, decode to nothing")
    void notAGif() {
        assertThat(GifDecoder.decodeFrame(new byte[0], 1)).isEmpty();
        assertThat(GifDecoder.decodeFrame("GIF89a".getBytes(), 1)).isEmpty();
        assertThat(GifDecoder.decodeFrame(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14}, 1)).isEmpty();
    }

    @ParameterizedTest(name = "{0} colours")
    @ValueSource(ints = {1, 2, 3, 255, 256})
    @DisplayName("a picture of 256 colours or fewer is written against its own palette, and ImageIO reads back the same pixels")
    void ownPaletteRoundTrips(int colours) throws IOException {
        Pixels picture = pictureOf(17, 19, colours);

        byte[] gif = GifEncoder.encode(picture);

        assertSamePicture(picture, ImageIO.read(new ByteArrayInputStream(gif)), colours + " colours");
        assertSamePicture(GifDecoder.decodeFrame(gif, 1).orElseThrow(),
                ImageIO.read(new ByteArrayInputStream(gif)), colours + " colours, decoded by the core");
    }

    @Test
    @DisplayName("a picture of more than 256 colours is written in at most 256, and ImageIO and the core read the same pixels")
    void manyColoursAreQuantised() throws IOException {
        Pixels picture = gradient(64, 64);

        byte[] gif = GifEncoder.encode(picture);
        Pixels decoded = GifDecoder.decodeFrame(gif, 1).orElseThrow();

        assertSamePicture(decoded, ImageIO.read(new ByteArrayInputStream(gif)), "quantised");
        assertThat(distinctColours(decoded)).isLessThanOrEqualTo(256).isGreaterThan(128);
        assertThat(averageDistance(picture, decoded)).isLessThan(6.0);
    }

    @Test
    @DisplayName("a large picture spans many LZW tables, and ImageIO reads back what the core wrote")
    void aLargePicture() throws IOException {
        Pixels picture = pictureOf(400, 300, 200);

        byte[] gif = GifEncoder.encode(picture);

        assertSamePicture(picture, ImageIO.read(new ByteArrayInputStream(gif)), "large");
    }

    static Pixels pictureOf(int wide, int high, int colours) {
        byte[] rgba = new byte[wide * high * 4];
        byte[] chosen = noise(wide * high, colours, colours);
        int[] palette = palette(Math.max(colours, 1));
        for (int at = 0; at < wide * high; at++) {
            int colour = palette[chosen[at] & 0xFF];
            rgba[at * 4] = (byte) (colour >> 16);
            rgba[at * 4 + 1] = (byte) (colour >> 8);
            rgba[at * 4 + 2] = (byte) colour;
            rgba[at * 4 + 3] = (byte) 0xFF;
        }
        return new Pixels(wide, high, rgba);
    }

    static Pixels gradient(int wide, int high) {
        byte[] rgba = new byte[wide * high * 4];
        for (int down = 0; down < high; down++) {
            for (int across = 0; across < wide; across++) {
                int at = (down * wide + across) * 4;
                rgba[at] = (byte) (across * 255 / (wide - 1));
                rgba[at + 1] = (byte) (down * 255 / (high - 1));
                rgba[at + 2] = (byte) ((across + down) * 255 / (wide + high - 2));
                rgba[at + 3] = (byte) 0xFF;
            }
        }
        return new Pixels(wide, high, rgba);
    }

    private static int distinctColours(Pixels picture) {
        List<Integer> seen = new ArrayList<>();
        java.util.Set<Integer> colours = new java.util.HashSet<>();
        for (int at = 0; at < picture.rgba().length; at += 4) {
            colours.add(pixelAt(picture, at / 4));
        }
        seen.addAll(colours);
        return seen.size();
    }

    private static double averageDistance(Pixels wanted, Pixels got) {
        double total = 0;
        for (int at = 0; at < wanted.rgba().length; at += 4) {
            for (int channel = 0; channel < 3; channel++) {
                total += Math.abs((wanted.rgba()[at + channel] & 0xFF) - (got.rgba()[at + channel] & 0xFF));
            }
        }
        return total / (wanted.rgba().length / 4 * 3.0);
    }

    private static int pixelAt(Pixels picture, int index) {
        byte[] rgba = picture.rgba();
        int at = index * 4;
        return (rgba[at] & 0xFF) << 24 | (rgba[at + 1] & 0xFF) << 16 | (rgba[at + 2] & 0xFF) << 8 | (rgba[at + 3] & 0xFF);
    }

    private static int opaque(int rgb) {
        return rgb << 8 | 0xFF;
    }
}
