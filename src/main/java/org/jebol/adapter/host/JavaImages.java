package org.jebol.adapter.host;

import org.jebol.domain.eval.ImagePort;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
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
 * <p>JPEG and BMP have no alpha channel to write into. Handing ImageIO an
 * image that still has one gives a refusal for BMP and a picture with its
 * colours rotated for JPEG, so the channel comes off here and those two come
 * back opaque -- which is what they would have been anyway.
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
    private static final Set<String> HAVE_NO_ALPHA = Set.of("jpeg", "bmp");

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
        BufferedImage written = bufferedFrom(image, HAVE_NO_ALPHA.contains(format));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            return ImageIO.write(written, format, bytes) ? bytes.toByteArray() : null;
        } catch (IOException | RuntimeException unwritable) {
            return null;
        }
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
