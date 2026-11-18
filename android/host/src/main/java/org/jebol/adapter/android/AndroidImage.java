package org.jebol.adapter.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.jebol.domain.host.ImagePort;
import org.jebol.domain.image.BitmapWriter;
import org.jebol.domain.image.GifDecoder;
import org.jebol.domain.image.GifEncoder;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AndroidImage implements ImagePort {

    private static final Set<String> DECODED = Set.of("png", "jpeg", "jpg", "gif", "bmp", "webp", "heif");

    private static final Map<String, Bitmap.CompressFormat> COMPRESSED_BY_ANDROID = Map.of(
            "png", Bitmap.CompressFormat.PNG,
            "jpeg", Bitmap.CompressFormat.JPEG,
            "jpg", Bitmap.CompressFormat.JPEG,
            "webp", Bitmap.CompressFormat.WEBP_LOSSLESS);

    private static final Set<String> WITHOUT_ALPHA = Set.of("jpeg", "jpg");

    private static final String WRITTEN_AS_BITMAP = "bmp";

    private static final String WRITTEN_AS_GIF = "gif";

    private static final int JPEG_QUALITY = 75;

    private static final int LOSSLESS = 100;

    private static final int CHANNELS = 4;

    private static final int OPAQUE = 0xFF;

    private static final int ONLY_FRAME_ANDROID_DECODES = 1;

    @Override
    public boolean hasImageType(String type) {
        return DECODED.contains(type.toLowerCase(Locale.ROOT));
    }

    @Override
    public Pixels decode(byte[] encoded, String type, int frame) {
        if (GifDecoder.isGif(encoded)) {
            return GifDecoder.decodeFrame(encoded, frame).orElse(null);
        }
        if (frame != ONLY_FRAME_ANDROID_DECODES) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inPremultiplied = false;
        Bitmap decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.length, options);
        if (decoded == null) {
            return null;
        }
        try {
            return readStoredPixels(decoded);
        } finally {
            decoded.recycle();
        }
    }

    private static Pixels readStoredPixels(Bitmap decoded) {
        Bitmap eightBits = decoded.getConfig() == Bitmap.Config.ARGB_8888
                ? decoded
                : decoded.copy(Bitmap.Config.ARGB_8888, false);
        try {
            eightBits.setPremultiplied(false);
            ByteBuffer stored = ByteBuffer.allocate(eightBits.getWidth() * eightBits.getHeight() * CHANNELS);
            eightBits.copyPixelsToBuffer(stored);
            return new Pixels(eightBits.getWidth(), eightBits.getHeight(), stored.array());
        } finally {
            if (eightBits != decoded) {
                eightBits.recycle();
            }
        }
    }

    @Override
    public byte[] encode(Pixels image, String type) {
        String format = type.toLowerCase(Locale.ROOT);
        if (format.equals(WRITTEN_AS_BITMAP)) {
            return BitmapWriter.encode32BitBitmap(image);
        }
        if (format.equals(WRITTEN_AS_GIF)) {
            return GifEncoder.encode(image);
        }
        Bitmap.CompressFormat compressed = COMPRESSED_BY_ANDROID.get(format);
        if (compressed == null || image.wide() == 0 || image.high() == 0) {
            return null;
        }
        Bitmap bitmap = bitmapFrom(image, WITHOUT_ALPHA.contains(format));
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int quality = compressed == Bitmap.CompressFormat.JPEG ? JPEG_QUALITY : LOSSLESS;
            return bitmap.compress(compressed, quality, out) ? out.toByteArray() : null;
        } finally {
            bitmap.recycle();
        }
    }

    private static Bitmap bitmapFrom(Pixels image, boolean opaque) {
        byte[] rgba = image.rgba();
        int[] argb = new int[image.wide() * image.high()];
        for (int at = 0; at < argb.length; at++) {
            int alpha = opaque ? OPAQUE : rgba[at * CHANNELS + 3] & 0xFF;
            argb[at] = alpha << 24 | (rgba[at * CHANNELS] & 0xFF) << 16
                    | (rgba[at * CHANNELS + 1] & 0xFF) << 8 | (rgba[at * CHANNELS + 2] & 0xFF);
        }
        Bitmap bitmap = Bitmap.createBitmap(image.wide(), image.high(), Bitmap.Config.ARGB_8888);
        bitmap.setPremultiplied(false);
        bitmap.setPixels(argb, 0, image.wide(), 0, 0, image.wide(), image.high());
        return bitmap;
    }
}
