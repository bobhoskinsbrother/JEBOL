package org.jebol.domain.value;

/**
 * The mutable buffer behind an {@code image!} value: pixels, and the shape they
 * are arranged in.
 *
 * <p>Four bytes a pixel, so this is a series whose element is a quad. The C says
 * it in one line -- {@code QUAD_SKIP(s, n)} is {@code data + n * 4} -- and every
 * navigation action an image has follows from it: a position is a pixel index and
 * the width lives here rather than in the position, so skipping into an image
 * does not change its shape.
 *
 * <p>The width is fixed and the height is derived. {@code Reset_Height} runs after
 * anything that changes the pixel count -- {@code VAL_IMAGE_HIGH(value) = w ?
 * (VAL_TAIL(value) / w) : 0} -- so REMOVE and CLEAR make an image shorter rather
 * than narrower, and a count that is not a whole number of rows leaves a row
 * partly there with the height rounding down over it.
 *
 * <p>The bytes are red, green, blue, alpha in that order, on every platform.
 * Rebol's own order is not: {@code include/reb-c.h} picks ARGB on a big-endian
 * host, RGBA on Android and BGRA elsewhere. What a script sees is fixed either
 * way -- molding writes {@code RRGGBB} and a pixel is the tuple {@code r.g.b.a}
 * -- so the fixed order is the language and the varying one is storage. Decision
 * 20 in {@code docs/decisions.md} says why that matters more here than there: the
 * plan is desktop, the web and Android, and Android is the platform Rebol treats
 * differently.
 *
 * <p>Alpha is kept for every pixel whether the image uses one or not, which is
 * why {@link #hasAlpha()} walks the pixels rather than reading a flag. The C does
 * the same, and its comment about the flag it used to keep is still there.
 */
public final class ImageStorage {

    /** {@code if (w > 0xFFFF || h > 0xFFFF)} refuses. */
    public static final int LONGEST_SIDE = 0xFFFF;

    /** How many bytes one pixel takes. {@code sizeof(u32)}. */
    public static final int BYTES_A_PIXEL = 4;

    /** {@code CLEAR_IMAGE} is a memset of this: white, and opaque with it. */
    private static final byte FRESH = (byte) 0xFF;

    private byte[] pixels;
    private int length;
    private int wide;
    private int high;
    private boolean isProtected;

    private ImageStorage(byte[] pixels, int wide, int high, int length) {
        this.pixels = pixels;
        this.wide = wide;
        this.high = high;
        this.length = length;
    }

    /**
     * An image of this size, filled opaque white.
     *
     * <p>{@code CLEAR_IMAGE(img->data, w, h); // Makes the default image white}
     * -- and the same memset gives every alpha byte 0xFF, so a fresh image molds
     * without an alpha binary.
     */
    public static ImageStorage of(int wide, int high) {
        byte[] pixels = new byte[wide * high * BYTES_A_PIXEL];
        java.util.Arrays.fill(pixels, FRESH);
        return new ImageStorage(pixels, wide, high, wide * high);
    }

    /** The same pixels in a buffer of their own, for COPY and MAKE of an image. */
    public ImageStorage copy() {
        return new ImageStorage(
                java.util.Arrays.copyOf(pixels, length * BYTES_A_PIXEL),
                wide, high, length);
    }

    public int wide() {
        return wide;
    }

    /**
     * Lays the same pixels out at another width, which is what `img/size:`
     * does.
     *
     * <p>Not a byte is moved. {@code VAL_IMAGE_WIDE(data) = VAL_PAIR_X_INT(val);
     * VAL_IMAGE_HIGH(data) = MIN(VAL_PAIR_Y_INT(val), VAL_TAIL(data) / x)} --
     * the height asked for, or however many whole rows the pixels there make,
     * whichever is smaller.
     */
    public void reshape(int across, int down) {
        refuseIfProtected();
        this.wide = across;
        this.high = Math.max(0, down);
    }

    /**
     * How tall the image is, worked out rather than stored.
     *
     * <p>`Reset_Height` is the whole rule: `VAL_IMAGE_HIGH(value) = w ?
     * (VAL_TAIL(value) / w) : 0`. The width is fixed and the height follows the
     * pixel count, so shortening an image with REMOVE or CLEAR makes it shorter
     * rather than narrower, and a count that is not a whole number of rows leaves
     * the last row partly there -- which the C allows and the height rounds down
     * to hide.
     */
    public int high() {
        return high;
    }

    /**
     * `Reset_Height`, run after anything that changes the pixel count.
     *
     * <p>`VAL_IMAGE_HIGH(value) = w ? (VAL_TAIL(value) / w) : 0`. Only after a
     * change: a zero-width image made as `-2x2` keeps the height it was given,
     * because nothing has recomputed it.
     */
    private void resetHeight() {
        high = wide == 0 ? 0 : length / wide;
    }

    /** How many pixels there are: {@code img->tail}. */
    public int length() {
        return length;
    }

    /** Drops pixels from a position, as `Remove_Series` does. */
    public void removeFrom(int oneBasedIndex, int howMany) {
        refuseIfProtected();
        int dropped = Math.min(howMany, length - oneBasedIndex + 1);
        if (dropped <= 0) {
            return;
        }
        int from = (oneBasedIndex - 1 + dropped) * BYTES_A_PIXEL;
        int to = (oneBasedIndex - 1) * BYTES_A_PIXEL;
        System.arraycopy(pixels, from, pixels, to, length * BYTES_A_PIXEL - from);
        length -= dropped;
        resetHeight();
    }

    /** Drops everything from a position on, which is what CLEAR does to an image. */
    public void clearFrom(int oneBasedIndex) {
        refuseIfProtected();
        if (oneBasedIndex <= length) {
            length = oneBasedIndex - 1;
            resetHeight();
        }
    }

    /** Puts a pixel in at a position, growing the buffer when it has to. */
    public void insertAt(int oneBasedIndex, int red, int green, int blue, int alpha) {
        refuseIfProtected();
        int needed = (length + 1) * BYTES_A_PIXEL;
        if (needed > pixels.length) {
            pixels = java.util.Arrays.copyOf(pixels, Math.max(needed, pixels.length * 2));
        }
        int at = (oneBasedIndex - 1) * BYTES_A_PIXEL;
        System.arraycopy(pixels, at, pixels, at + BYTES_A_PIXEL,
                length * BYTES_A_PIXEL - at);
        pixels[at] = (byte) red;
        pixels[at + 1] = (byte) green;
        pixels[at + 2] = (byte) blue;
        pixels[at + 3] = (byte) alpha;
        length++;
        resetHeight();
    }

    public boolean isProtected() {
        return isProtected;
    }

    public void protectFromChange(boolean protectedNow) {
        this.isProtected = protectedNow;
    }

    /**
     * One byte of one pixel, both counted from one.
     *
     * <p>Channel 1 is red and channel 4 is alpha, which is the order the C's
     * {@code img/1/2: 100} case writes in: {@code case 2: bp[C_G] = ...}.
     */
    public int channelAt(int pixel, int channel) {
        return pixels[(pixel - 1) * BYTES_A_PIXEL + channel - 1] & 0xFF;
    }

    public void setChannelAt(int pixel, int channel, int octet) {
        refuseIfProtected();
        pixels[(pixel - 1) * BYTES_A_PIXEL + channel - 1] = (byte) octet;
    }

    /** A pixel as red, green, blue, alpha. */
    public int[] pixelAt(int pixel) {
        int at = (pixel - 1) * BYTES_A_PIXEL;
        return new int[] {
                pixels[at] & 0xFF, pixels[at + 1] & 0xFF,
                pixels[at + 2] & 0xFF, pixels[at + 3] & 0xFF};
    }

    /** Writes red, green and blue, leaving the alpha this pixel already had. */
    public void setColourAt(int pixel, int red, int green, int blue) {
        refuseIfProtected();
        int at = (pixel - 1) * BYTES_A_PIXEL;
        pixels[at] = (byte) red;
        pixels[at + 1] = (byte) green;
        pixels[at + 2] = (byte) blue;
    }

    public void setAlphaAt(int pixel, int alpha) {
        setChannelAt(pixel, 4, alpha);
    }

    /**
     * Whether any pixel is less than fully opaque.
     *
     * <p>{@code Image_Has_Alpha} walks every pixel of the whole image looking for
     * an alpha that is not 0xFF -- {@code if (~*p++ & 0xff000000)} -- rather than
     * reading a flag, and molding asks it before writing the second binary. So an
     * image becomes one that needs an alpha channel by having a pixel written,
     * not by being told.
     */
    public boolean hasAlpha() {
        for (int pixel = 1; pixel <= length(); pixel++) {
            if (channelAt(pixel, 4) != 0xFF) {
                return true;
            }
        }
        return false;
    }

    private void refuseIfProtected() {
        if (isProtected) {
            throw new ProtectedFromChange();
        }
    }

    @Override
    public String toString() {
        return "ImageStorage(" + wide + "x" + high() + ")";
    }
}
