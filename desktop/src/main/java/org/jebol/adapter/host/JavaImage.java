package org.jebol.adapter.host;

import org.jebol.domain.host.ImagePort;
import org.jebol.domain.image.BitmapWriter;
import org.jebol.domain.image.GifDecoder;
import org.jebol.domain.image.GifEncoder;

import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
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

public final class JavaImage implements ImagePort {

    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "png", "png",
            "jpeg", "jpeg",
            "jpg", "jpeg",
            "gif", "gif",
            "bmp", "bmp",
            "tiff", "tiff");

    private static final int CHANNELS_PIXEL = 4;

    private static final int OPAQUE_ALPHA = 0xFF;

    private static final Set<String> HAVE_NO_ALPHA = Set.of("jpeg");

    private static final String WRITTEN_AS_BITMAP = "bmp";

    private static final String WRITTEN_AS_GIF = "gif";

    @Override
    public boolean hasImageType(String type) {
        return IMAGE_TYPES.containsKey(type.toLowerCase(Locale.ROOT));
    }

    @Override
    public Pixels decode(byte[] encoded, String type, int frame) {
        if (GifDecoder.isGif(encoded)) {
            return GifDecoder.decodeFrame(encoded, frame).orElse(null);
        }
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
            return drawEveryEarlierPageOver(reader, frame);
        } finally {
            reader.dispose();
        }
    }

    private BufferedImage drawEveryEarlierPageOver(
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
                onto.drawImage(reader.read(step), 0, 0, null);
            }
        } finally {
            onto.dispose();
        }
        return canvas;
    }

    private Pixels readPixels(BufferedImage read) {
        if (read.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY) {
            return readGreyPixels(read);
        }
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

    private Pixels readGreyPixels(BufferedImage read) {
        int wide = read.getWidth();
        int high = read.getHeight();
        Raster raster = read.getRaster();
        ColorModel model = read.getColorModel();
        boolean hasAlpha = raster.getNumBands() > 1;
        byte[] rgba = new byte[wide * high * CHANNELS_PIXEL];
        int at = 0;
        for (int down = 0; down < high; down++) {
            for (int across = 0; across < wide; across++) {
                byte grey = (byte) toEightBits(raster.getSample(across, down, 0), model.getComponentSize(0));
                rgba[at++] = grey;
                rgba[at++] = grey;
                rgba[at++] = grey;
                rgba[at++] = hasAlpha
                        ? (byte) toEightBits(raster.getSample(across, down, 1), model.getComponentSize(1))
                        : (byte) OPAQUE_ALPHA;
            }
        }
        return new Pixels(wide, high, rgba);
    }

    private static int toEightBits(int sample, int bits) {
        if (bits >= Byte.SIZE) {
            return sample >>> (bits - Byte.SIZE);
        }
        return sample * OPAQUE_ALPHA / ((1 << bits) - 1);
    }

    @Override
    public byte[] encode(Pixels image, String type) {
        String format = IMAGE_TYPES.get(type.toLowerCase(Locale.ROOT));
        if (format == null) {
            return null;
        }
        if (format.equals(WRITTEN_AS_BITMAP)) {
            return BitmapWriter.encode32BitBitmap(image);
        }
        if (format.equals(WRITTEN_AS_GIF)) {
            return GifEncoder.encode(image);
        }
        return writeBytes(bufferFrom(image, HAVE_NO_ALPHA.contains(format)), format);
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
