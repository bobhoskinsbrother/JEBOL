package org.jebol.domain.value;

/**
 * A position into an image, counted in pixels.
 *
 * <p>{@code VAL_INDEX} is the pixel and {@code QUAD_SKIP} turns it into a byte
 * offset, so an image navigates exactly as a block does. The width belongs to the
 * storage rather than to the position, which is what makes {@code at img 3} the
 * third pixel of the same image rather than a smaller image.
 */
public record ImageValue(ImageStorage storage, int index) implements SeriesValue {

    public ImageValue {
        if (storage == null) {
            throw new IllegalArgumentException("an image value needs storage");
        }
        if (index < 1 || index > storage.length() + 1) {
            throw new IllegalArgumentException(
                    "index " + index + " is outside 1.." + (storage.length() + 1));
        }
    }

    /** An image of this size, filled opaque white, standing at its head. */
    public static ImageValue of(int wide, int high) {
        return new ImageValue(ImageStorage.of(wide, high), 1);
    }

    @Override
    public Datatype datatype() {
        return Datatype.IMAGE;
    }

    @Override
    public int storageLength() {
        return storage.length();
    }

    @Override
    public ImageValue atIndex(int oneBasedIndex) {
        return new ImageValue(storage, oneBasedIndex);
    }

    @Override
    public ImageValue head() {
        return atIndex(1);
    }

    @Override
    public ImageValue tail() {
        return atIndex(storage.length() + 1);
    }

    @Override
    public boolean sharesStorageWith(SeriesValue other) {
        return other instanceof ImageValue image && image.storage == storage;
    }

    /** The pixel here, as red, green, blue, alpha. */
    public int[] pixelAt(int offsetFromHere) {
        return storage.pixelAt(index + offsetFromHere - 1);
    }

    /**
     * The size, as the pair {@code img/size} answers.
     *
     * <p>The whole image's size, not what is left from here: the C reads
     * {@code VAL_IMAGE_WIDE} and {@code VAL_IMAGE_HIGH} off the series and never
     * consults the index.
     */
    public PairValue size() {
        return PairValue.of(storage.wide(), storage.high());
    }

    /** REBOL's {@code ==}: the same shape and the same remaining pixels. */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ImageValue image)) {
            return false;
        }
        if (storage.wide() != image.storage.wide()
                || storage.high() != image.storage.high()
                || lengthFromHere() != image.lengthFromHere()) {
            return false;
        }
        for (int offset = 1; offset <= lengthFromHere(); offset++) {
            if (!java.util.Arrays.equals(pixelAt(offset), image.pixelAt(offset))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = storage.wide() * 31 + storage.high();
        for (int offset = 1; offset <= lengthFromHere(); offset++) {
            hash = hash * 31 + java.util.Arrays.hashCode(pixelAt(offset));
        }
        return hash;
    }

    @Override
    public String toString() {
        return "image " + storage.wide() + "x" + storage.high() + " @" + index;
    }
}
