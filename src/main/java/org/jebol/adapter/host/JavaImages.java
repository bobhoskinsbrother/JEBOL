package org.jebol.adapter.host;

import org.jebol.domain.eval.ImagePort;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.w3c.dom.Node;

/**
 * The JVM's own image codec, which is the platform's as far as REBOL is
 * concerned.
 *
 * <p>Pixels cross the port as four bytes each, red first and alpha last, which
 * is how REBOL holds an image. Decision 20 in {@code docs/decisions.md} settles
 * that order; decision 21 records what ImageIO gets wrong and why parts of this
 * are written by hand.
 */
public final class JavaImages implements ImagePort {

    private static final Map<String, String> FORMAT_NAMES = Map.of(
            "png", "png",
            "jpeg", "jpeg",
            "jpg", "jpeg",
            "gif", "gif",
            "bmp", "bmp",
            "tiff", "tiff");

    private static final int CHANNELS_A_PIXEL = 4;

    private static final Set<String> HAVE_NO_ALPHA = Set.of("jpeg");

    private static final Set<String> STORES_AN_INDEX_INTO_A_PALETTE = Set.of("gif");

    private static final String WRITTEN_BY_HAND = "bmp";

    private static final int PALETTE_ENTRIES = 256;

    private static final int BITS_AN_INDEX_TAKES = 8;

    private static final Point AT_THE_CORNER = new Point(0, 0);

    private static final int FILE_HEADER_BYTES = 14;

    private static final int FIFTH_HEADER_BYTES = 124;

    private static final int EACH_CHANNEL_HAS_A_MASK = 3;

    private static final int THE_USUAL_COLOUR_SPACE = 0x73524742;

    private static final String THE_GIF_IMAGE_DESCRIPTOR_TREE =
            "javax_imageio_gif_image_1.0";

    @Override
    public boolean knows(String type) {
        return FORMAT_NAMES.containsKey(type.toLowerCase(Locale.ROOT));
    }

    @Override
    public Pixels decoded(byte[] encoded, String type, int frame) {
        try (ImageInputStream stream =
                     ImageIO.createImageInputStream(new ByteArrayInputStream(encoded))) {
            if (stream == null) {
                return null;
            }
            BufferedImage read = theFrameAt(stream, frame);
            return read == null ? null : pixelsOf(read);
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }

    private static BufferedImage theFrameAt(ImageInputStream stream, int frame)
            throws IOException {

        Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
        if (!readers.hasNext()) {
            return null;
        }
        ImageReader reader = readers.next();
        try {
            reader.setInput(stream);
            if (frame < 1 || frame > reader.getNumImages(true)) {
                return null;
            }
            if (frame == 1) {
                return reader.read(0);
            }
            return everyEarlierFrameLaidDownFirstBecauseEachIsOnlyAPatch(reader, frame);
        } finally {
            reader.dispose();
        }
    }

    private static BufferedImage everyEarlierFrameLaidDownFirstBecauseEachIsOnlyAPatch(
            ImageReader reader, int frame) throws IOException {

        BufferedImage first = reader.read(0);
        BufferedImage canvas = new BufferedImage(
                Math.max(first.getWidth(), reader.getWidth(0)),
                Math.max(first.getHeight(), reader.getHeight(0)),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D onto = canvas.createGraphics();
        try {
            onto.drawImage(first, 0, 0, null);
            for (int step = 1; step < frame; step++) {
                BufferedImage patch = reader.read(step);
                Point corner = whereItGoes(reader, step);
                onto.drawImage(patch, corner.x, corner.y, null);
            }
        } finally {
            onto.dispose();
        }
        return canvas;
    }

    private static Point whereItGoes(ImageReader reader, int frame) {
        try {
            Node tree = reader.getImageMetadata(frame)
                    .getAsTree(THE_GIF_IMAGE_DESCRIPTOR_TREE);
            for (Node child = tree.getFirstChild();
                    child != null; child = child.getNextSibling()) {
                if (child.getNodeName().equals("ImageDescriptor")) {
                    return new Point(
                            numberIn(child, "imageLeftPosition"),
                            numberIn(child, "imageTopPosition"));
                }
            }
        } catch (IOException | RuntimeException noMetadata) {
            return AT_THE_CORNER;
        }
        return AT_THE_CORNER;
    }

    private static int numberIn(Node descriptor, String attribute) {
        Node held = descriptor.getAttributes().getNamedItem(attribute);
        return held == null ? 0 : Integer.parseInt(held.getNodeValue());
    }

    private static Pixels pixelsOf(BufferedImage read) {
        int wide = read.getWidth();
        int high = read.getHeight();
        byte[] rgba = new byte[wide * high * CHANNELS_A_PIXEL];
        int at = 0;
        for (int down = 0; down < high; down++) {
            for (int across = 0; across < wide; across++) {
                int argb = read.getRGB(across, down);
                rgba[at++] = (byte) (argb >> 16);
                rgba[at++] = (byte) (argb >> 8);
                rgba[at++] = (byte) argb;
                rgba[at++] = (byte) (argb >> 24);
            }
        }
        return new Pixels(wide, high, rgba);
    }

    @Override
    public byte[] encoded(Pixels image, String type) {
        String format = FORMAT_NAMES.get(type.toLowerCase(Locale.ROOT));
        if (format == null) {
            return null;
        }
        if (format.equals(WRITTEN_BY_HAND)) {
            return aBitmapOfThirtyTwoBits(image);
        }
        BufferedImage written = bufferedFrom(image, HAVE_NO_ALPHA.contains(format));
        if (STORES_AN_INDEX_INTO_A_PALETTE.contains(format)) {
            written = withAPaletteOfItsOwnColours(written);
        }
        return theBytesWriting(written, format);
    }

    private static byte[] aBitmapOfThirtyTwoBits(Pixels image) {
        int pixelBytes = image.wide() * image.high() * CHANNELS_A_PIXEL;
        byte[] file = new byte[FILE_HEADER_BYTES + FIFTH_HEADER_BYTES + pixelBytes];
        file[0] = 'B';
        file[1] = 'M';
        putFourBytes(file, 2, file.length);
        putFourBytes(file, 10, FILE_HEADER_BYTES + FIFTH_HEADER_BYTES);

        int header = FILE_HEADER_BYTES;
        putFourBytes(file, header, FIFTH_HEADER_BYTES);
        putFourBytes(file, header + 4, image.wide());
        putFourBytes(file, header + 8, -image.high());
        putTwoBytes(file, header + 12, 1);
        putTwoBytes(file, header + 14, Byte.SIZE * CHANNELS_A_PIXEL);
        putFourBytes(file, header + 16, EACH_CHANNEL_HAS_A_MASK);
        putFourBytes(file, header + 20, pixelBytes);
        putFourBytes(file, header + 40, 0x00FF0000);
        putFourBytes(file, header + 44, 0x0000FF00);
        putFourBytes(file, header + 48, 0x000000FF);
        putFourBytes(file, header + 52, 0xFF000000);
        putFourBytes(file, header + 56, THE_USUAL_COLOUR_SPACE);

        byte[] rgba = image.rgba();
        int writing = FILE_HEADER_BYTES + FIFTH_HEADER_BYTES;
        for (int at = 0; at < pixelBytes; at += CHANNELS_A_PIXEL) {
            file[writing++] = rgba[at + 2];
            file[writing++] = rgba[at + 1];
            file[writing++] = rgba[at];
            file[writing++] = rgba[at + 3];
        }
        return file;
    }

    private static void putFourBytes(byte[] file, int at, int value) {
        file[at] = (byte) value;
        file[at + 1] = (byte) (value >> 8);
        file[at + 2] = (byte) (value >> 16);
        file[at + 3] = (byte) (value >> 24);
    }

    private static void putTwoBytes(byte[] file, int at, int value) {
        file[at] = (byte) value;
        file[at + 1] = (byte) (value >> 8);
    }

    private static byte[] theBytesWriting(BufferedImage written, String format) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            return null;
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(stream);
            ImageWriteParam asked = writer.getDefaultWriteParam();
            leaveTheRowsInOrderRatherThanInterlaced(asked);
            writer.write(null, new IIOImage(written, null, null), asked);
            stream.flush();
            return bytes.toByteArray();
        } catch (IOException | RuntimeException unwritable) {
            return null;
        } finally {
            writer.dispose();
        }
    }

    private static void leaveTheRowsInOrderRatherThanInterlaced(ImageWriteParam asked) {
        if (asked.canWriteProgressive()) {
            asked.setProgressiveMode(ImageWriteParam.MODE_DISABLED);
        }
    }

    private static BufferedImage withAPaletteOfItsOwnColours(BufferedImage picture) {
        LinkedHashSet<Integer> colours = new LinkedHashSet<>();
        for (int down = 0; down < picture.getHeight(); down++) {
            for (int across = 0; across < picture.getWidth(); across++) {
                colours.add(picture.getRGB(across, down) | 0xFF000000);
                if (colours.size() > PALETTE_ENTRIES) {
                    return leftForTheWritersOwnQuantiser(picture);
                }
            }
        }
        return laidOutAgainstThePalette(picture, colours);
    }

    private static BufferedImage leftForTheWritersOwnQuantiser(BufferedImage picture) {
        return picture;
    }

    private static BufferedImage laidOutAgainstThePalette(
            BufferedImage picture, Collection<Integer> colours) {

        byte[] reds = new byte[colours.size()];
        byte[] greens = new byte[colours.size()];
        byte[] blues = new byte[colours.size()];
        Map<Integer, Integer> whereEachSits = new HashMap<>();
        int at = 0;
        for (int colour : colours) {
            reds[at] = (byte) (colour >> 16);
            greens[at] = (byte) (colour >> 8);
            blues[at] = (byte) colour;
            whereEachSits.put(colour, at);
            at++;
        }
        IndexColorModel palette = new IndexColorModel(
                BITS_AN_INDEX_TAKES, colours.size(), reds, greens, blues);
        BufferedImage indexed = new BufferedImage(picture.getWidth(),
                picture.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, palette);
        WritableRaster raster = indexed.getRaster();
        for (int down = 0; down < picture.getHeight(); down++) {
            for (int across = 0; across < picture.getWidth(); across++) {
                raster.setSample(across, down, 0,
                        whereEachSits.get(picture.getRGB(across, down) | 0xFF000000));
            }
        }
        return indexed;
    }

    private static BufferedImage bufferedFrom(Pixels image, boolean withoutAlpha) {
        BufferedImage written = new BufferedImage(image.wide(), image.high(),
                withoutAlpha ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        byte[] rgba = image.rgba();
        int at = 0;
        for (int down = 0; down < image.high(); down++) {
            for (int across = 0; across < image.wide(); across++) {
                int red = rgba[at++] & 0xFF;
                int green = rgba[at++] & 0xFF;
                int blue = rgba[at++] & 0xFF;
                int alpha = rgba[at++] & 0xFF;
                written.setRGB(across, down,
                        (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        return written;
    }
}
