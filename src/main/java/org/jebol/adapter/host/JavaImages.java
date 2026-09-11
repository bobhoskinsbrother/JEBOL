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
import java.util.Iterator;
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
 * <p>{@code javax.imageio} arrives with the runtime and reads and writes PNG,
 * JPEG, GIF and BMP everywhere Java runs, so the "only on Windows and macOS so
 * far" the C says of its own shim is not a limit this platform has.
 *
 * <p>Pixels cross the port as four bytes each, red first and alpha last, which
 * is how REBOL holds an image and how {@code img/rgba} hands it out. Java's
 * own {@code TYPE_INT_ARGB} is the other order in a different container, so
 * the conversion is here rather than in the domain -- one place, and the side
 * that knows Java.
 *
 * <p>JPEG has no alpha channel to write into, and handing ImageIO a picture
 * that still has one gives back a picture with its colours rotated -- so the
 * channel comes off here and a JPEG comes back opaque, which is what it would
 * have been anyway.
 *
 * <p>A BMP does have somewhere to put one and is written by hand, because the
 * runtime's own BMP writer refuses every thirty-two-bit picture it is offered.
 */
public final class JavaImages implements ImagePort {

    /**
     * What each REBOL codec word is called in ImageIO, and the only four that
     * matter: Rebol's own codec-image.reb registers exactly these on a
     * platform without the Windows extras.
     */
    private static final Map<String, String> FORMAT_NAMES = Map.of(
            "png", "png",
            "jpeg", "jpeg",
            "jpg", "jpeg",
            "gif", "gif",
            "bmp", "bmp",
            "tiff", "tiff");

    private static final int CHANNELS_A_PIXEL = 4;

    /** The formats with nowhere to put an alpha channel. */
    private static final Set<String> HAVE_NO_ALPHA = Set.of("jpeg");

    /** The formats that store an index into a palette rather than a colour. */
    private static final Set<String> STORES_AN_INDEX_INTO_A_PALETTE = Set.of("gif");

    /** How many colours a GIF's table holds. */
    private static final int PALETTE_ENTRIES = 256;

    /**
     * How wide an index is, which is one byte because that is what
     * {@code TYPE_BYTE_INDEXED} stores whatever the palette's size.
     */
    private static final int BITS_AN_INDEX_TAKES = 8;

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

    /**
     * The nth image in a stream, counting from one, as a viewer would see it.
     *
     * <p>Going through a reader rather than {@code ImageIO.read} because that
     * one takes the first image and an animated GIF holds several. The reader
     * is asked for its own count first, so a frame past the end answers
     * nothing rather than throwing from inside the library.
     *
     * <p>Every frame up to the one wanted is drawn, not just that one. A GIF
     * frame after the first is usually a patch rather than a picture -- a
     * small rectangle, placed at an offset, covering only what changed -- so
     * reading it alone gives that patch on its own. What a viewer shows is the
     * patch laid over what was already on the canvas, and it is what the
     * checksums a real 3.22.5 answers are taken of.
     */
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
            return everyFrameUpTo(reader, frame);
        } finally {
            reader.dispose();
        }
    }

    private static BufferedImage everyFrameUpTo(ImageReader reader, int frame)
            throws IOException {

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

    /**
     * Where a frame sits on the canvas, which its own metadata carries.
     *
     * <p>{@code imageLeftPosition} and {@code imageTopPosition} of the GIF's
     * image descriptor. A reader that offers no metadata for the frame is
     * taken to mean the corner, which is what a single-image file has.
     */
    private static Point whereItGoes(ImageReader reader, int frame) {
        try {
            Node tree = reader.getImageMetadata(frame)
                    .getAsTree("javax_imageio_gif_image_1.0");
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

    private static final Point AT_THE_CORNER = new Point(0, 0);

    private static int numberIn(Node descriptor, String named) {
        Node held = descriptor.getAttributes().getNamedItem(named);
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
        if (format.equals("bmp")) {
            return aBitmapOfThirtyTwoBits(image);
        }
        BufferedImage written = bufferedFrom(image, HAVE_NO_ALPHA.contains(format));
        if (STORES_AN_INDEX_INTO_A_PALETTE.contains(format)) {
            written = withAPaletteOfItsOwnColours(written);
        }
        return theBytesWriting(written, format);
    }

    /**
     * A BMP written by hand, because the runtime's own writer will not write
     * one with an alpha channel.
     *
     * <p>It refuses thirty-two bits a pixel under every compression type it
     * offers -- "Image can not be encoded with compression type BI_RGB and 32
     * bits per pixel" -- so going through it means either writing
     * twenty-four bits and losing the transparency or not writing a BMP at
     * all. A real 3.22.5 loses nothing, and the format it uses is short
     * enough to write out directly.
     *
     * <p>The fifth version of the header is what makes the alpha possible: it
     * carries a mask per channel, so the file says which bits are which
     * rather than leaving a reader to assume. The runtime reads that back
     * perfectly; it is only writing it that is missing.
     */
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

    private static final int FILE_HEADER_BYTES = 14;

    /** {@code BITMAPV5HEADER}, the version that names a mask per channel. */
    private static final int FIFTH_HEADER_BYTES = 124;

    /** {@code BI_BITFIELDS}: the four masks below say which bits are which. */
    private static final int EACH_CHANNEL_HAS_A_MASK = 3;

    /** {@code LCS_sRGB}, written as the four letters {@code BGRs}. */
    private static final int THE_USUAL_COLOUR_SPACE = 0x73524742;

    /** Least significant byte first, which is the only order a BMP has. */
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

    /**
     * The encoded bytes, written a row at a time rather than interlaced.
     *
     * <p>Through a writer of its own rather than {@code ImageIO.write}, for
     * one setting: left to itself the GIF writer sets the interlace flag, and
     * its own reader then hands back a picture with the wrong rows -- a
     * two-by-two of four colours came back as three, and JEBOL could not read
     * a GIF it had just written even though a real 3.22.5 read it perfectly.
     * A real 3.22.5 writes those flags as nought, and so does this now.
     */
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
            if (asked.canWriteProgressive()) {
                asked.setProgressiveMode(ImageWriteParam.MODE_DISABLED);
            }
            writer.write(null, new IIOImage(written, null, null), asked);
            stream.flush();
            return bytes.toByteArray();
        } catch (IOException | RuntimeException unwritable) {
            return null;
        } finally {
            writer.dispose();
        }
    }

    /**
     * The same picture with a palette built from the colours it actually has.
     *
     * <p>Handed a full-colour image, ImageIO's GIF writer picks a palette by
     * quantising, and quantising merges colours that sit close together --
     * two dark reds a step apart came back as one, and the four colours of a
     * two-by-two came back as three. A picture with no more colours than the
     * table holds needs no quantiser at all: give it an entry apiece and every
     * colour survives.
     *
     * <p>Past that there is no room and something must go, so the picture is
     * left as it was and the writer quantises after all. Which colours it
     * loses is its own business, and pinning that would pin a version of
     * ImageIO rather than any behaviour of the language.
     */
    private static BufferedImage withAPaletteOfItsOwnColours(BufferedImage picture) {
        java.util.LinkedHashSet<Integer> colours = new java.util.LinkedHashSet<>();
        for (int down = 0; down < picture.getHeight(); down++) {
            for (int across = 0; across < picture.getWidth(); across++) {
                colours.add(picture.getRGB(across, down) | 0xFF000000);
                if (colours.size() > PALETTE_ENTRIES) {
                    return picture;
                }
            }
        }
        return laidOutAgainstThePalette(picture, colours);
    }

    private static BufferedImage laidOutAgainstThePalette(
            BufferedImage picture, java.util.Collection<Integer> colours) {

        byte[] reds = new byte[colours.size()];
        byte[] greens = new byte[colours.size()];
        byte[] blues = new byte[colours.size()];
        java.util.Map<Integer, Integer> whereEachSits = new java.util.HashMap<>();
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
