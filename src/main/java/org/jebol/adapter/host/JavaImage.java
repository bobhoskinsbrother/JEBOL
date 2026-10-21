package org.jebol.adapter.host;

import org.jebol.domain.host.ImagePort;

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

public final class JavaImage implements ImagePort {

    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "png", "png",
            "jpeg", "jpeg",
            "jpg", "jpeg",
            "gif", "gif",
            "bmp", "bmp",
            "tiff", "tiff");

    private static final int CHANNELS_PIXEL = 4;

    private static final Set<String> HAVE_NO_ALPHA = Set.of("jpeg");

    private static final Set<String> STORES_INDEX_INTO_PALETTE = Set.of("gif");

    private static final String WRITTEN_BY_HAND = "bmp";

    private static final int PALETTE_ENTRIES = 256;

    private static final int BITS_INDEX_TAKES = 8;

    private static final Point AT_CORNER = new Point(0, 0);

    private static final int FILE_HEADER_BYTES = 14;

    private static final int FIFTH_HEADER_BYTES = 124;

    private static final int EACH_CHANNEL_HAS_MASK = 3;

    private static final int USUAL_COLOUR_SPACE = 0x73524742;

    private static final String GIF_IMAGE_DESCRIPTOR_TREE =
            "javax_imageio_gif_image_1.0";

    @Override
    public boolean hasImageType(String type) {
        return IMAGE_TYPES.containsKey(type.toLowerCase(Locale.ROOT));
    }

    @Override
    public Pixels decode(byte[] encoded, String type, int frame) {
        try (ImageInputStream stream =
                     ImageIO.createImageInputStream(new ByteArrayInputStream(encoded))) {
            if (stream == null) {
                return null;
            }
            BufferedImage read = readFrameAt(stream, frame);
            return read == null ? null : readPixels(read);
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }

    private BufferedImage readFrameAt(ImageInputStream stream, int frame)
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
            return composeEveryEarlierFrame(reader, frame);
        } finally {
            reader.dispose();
        }
    }

    private BufferedImage composeEveryEarlierFrame(
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
                Point corner = findFrame(reader, step);
                onto.drawImage(patch, corner.x, corner.y, null);
            }
        } finally {
            onto.dispose();
        }
        return canvas;
    }

    private Point findFrame(ImageReader reader, int frame) {
        try {
            Node tree = reader.getImageMetadata(frame)
                    .getAsTree(GIF_IMAGE_DESCRIPTOR_TREE);
            for (Node child = tree.getFirstChild();
                    child != null; child = child.getNextSibling()) {
                if (child.getNodeName().equals("ImageDescriptor")) {
                    return new Point(
                            readNumberIn(child, "imageLeftPosition"),
                            readNumberIn(child, "imageTopPosition"));
                }
            }
        } catch (IOException | RuntimeException noMetadata) {
            return AT_CORNER;
        }
        return AT_CORNER;
    }

    private int readNumberIn(Node descriptor, String attribute) {
        Node held = descriptor.getAttributes().getNamedItem(attribute);
        return held == null ? 0 : Integer.parseInt(held.getNodeValue());
    }

    private Pixels readPixels(BufferedImage read) {
        int wide = read.getWidth();
        int high = read.getHeight();
        byte[] rgba = new byte[wide * high * CHANNELS_PIXEL];
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
    public byte[] encode(Pixels image, String type) {
        String format = IMAGE_TYPES.get(type.toLowerCase(Locale.ROOT));
        if (format == null) {
            return null;
        }
        if (format.equals(WRITTEN_BY_HAND)) {
            return encode32BitBitmap(image);
        }
        BufferedImage written = bufferFrom(image, HAVE_NO_ALPHA.contains(format));
        if (STORES_INDEX_INTO_PALETTE.contains(format)) {
            written = quantiseToOwnPalette(written);
        }
        return writeBytes(written, format);
    }

    private byte[] encode32BitBitmap(Pixels image) {
        int pixelBytes = image.wide() * image.high() * CHANNELS_PIXEL;
        byte[] file = new byte[FILE_HEADER_BYTES + FIFTH_HEADER_BYTES + pixelBytes];
        file[0] = 'B';
        file[1] = 'M';
        addFourBytes(file, 2, file.length);
        addFourBytes(file, 10, FILE_HEADER_BYTES + FIFTH_HEADER_BYTES);

        int header = FILE_HEADER_BYTES;
        addFourBytes(file, header, FIFTH_HEADER_BYTES);
        addFourBytes(file, header + 4, image.wide());
        addFourBytes(file, header + 8, -image.high());
        addTwoBytes(file, header + 12, 1);
        addTwoBytes(file, header + 14, Byte.SIZE * CHANNELS_PIXEL);
        addFourBytes(file, header + 16, EACH_CHANNEL_HAS_MASK);
        addFourBytes(file, header + 20, pixelBytes);
        addFourBytes(file, header + 40, 0x00FF0000);
        addFourBytes(file, header + 44, 0x0000FF00);
        addFourBytes(file, header + 48, 0x000000FF);
        addFourBytes(file, header + 52, 0xFF000000);
        addFourBytes(file, header + 56, USUAL_COLOUR_SPACE);

        byte[] rgba = image.rgba();
        int writing = FILE_HEADER_BYTES + FIFTH_HEADER_BYTES;
        for (int at = 0; at < pixelBytes; at += CHANNELS_PIXEL) {
            file[writing++] = rgba[at + 2];
            file[writing++] = rgba[at + 1];
            file[writing++] = rgba[at];
            file[writing++] = rgba[at + 3];
        }
        return file;
    }

    private void addFourBytes(byte[] file, int at, int value) {
        file[at] = (byte) value;
        file[at + 1] = (byte) (value >> 8);
        file[at + 2] = (byte) (value >> 16);
        file[at + 3] = (byte) (value >> 24);
    }

    private void addTwoBytes(byte[] file, int at, int value) {
        file[at] = (byte) value;
        file[at + 1] = (byte) (value >> 8);
    }

    private byte[] writeBytes(BufferedImage written, String format) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            return null;
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(stream);
            ImageWriteParam asked = writer.getDefaultWriteParam();
            leaveRowsInOrderRatherThanInterlaced(asked);
            writer.write(null, new IIOImage(written, null, null), asked);
            stream.flush();
            return bytes.toByteArray();
        } catch (IOException | RuntimeException unwritable) {
            return null;
        } finally {
            writer.dispose();
        }
    }

    private void leaveRowsInOrderRatherThanInterlaced(ImageWriteParam param) {
        if (param.canWriteProgressive()) {
            param.setProgressiveMode(ImageWriteParam.MODE_DISABLED);
        }
    }

    private BufferedImage quantiseToOwnPalette(BufferedImage picture) {
        LinkedHashSet<Integer> colours = new LinkedHashSet<>();
        for (int down = 0; down < picture.getHeight(); down++) {
            for (int across = 0; across < picture.getWidth(); across++) {
                colours.add(picture.getRGB(across, down) | 0xFF000000);
                if (colours.size() > PALETTE_ENTRIES) {
                    return leaveToWritersOwnQuantiser(picture);
                }
            }
        }
        return layOutAgainstPalette(picture, colours);
    }

    private BufferedImage leaveToWritersOwnQuantiser(BufferedImage picture) {
        return picture;
    }

    private BufferedImage layOutAgainstPalette(
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
                BITS_INDEX_TAKES, colours.size(), reds, greens, blues);
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

    private BufferedImage bufferFrom(Pixels image, boolean withoutAlpha) {
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
