package org.jebol.domain.eval;

/**
 * The image codec the host carries, which turns encoded bytes into pixels and
 * back.
 *
 * <p>A port the domain owns and an adapter fills, for the same reason the
 * filesystem is one: reading a PNG means an image library, and an image
 * library is not arithmetic. The domain says what it wants -- these bytes, as
 * that format, that frame -- and never learns how it was done.
 *
 * <p>The C reaches its platform's through {@code OS_Load_Image} and compiles
 * the whole of IMAGE only where {@code INCLUDE_IMAGE_OS_CODEC} is defined,
 * "only on Windows and macOS so far". A JVM has one wherever it runs, so this
 * platform is one of the ones that has it -- but which platform it is stays
 * the host's business, and an interpreter given no port refuses by the name
 * the C uses where there is no codec.
 *
 * <p>It matters more than one native. Rebol's own {@code codec-image.reb}
 * registers png, jpeg, gif and bmp in {@code system/codecs} and every one of
 * them is {@code func [data][lib/image/load/as data 'PNG]}, so a build that
 * refuses here does not merely lack IMAGE -- it lists four codecs in its
 * catalogue that cannot do anything.
 *
 * <p>Specified in {@code spec/natives.allium}.
 */
public interface ImagePort {

    /** An image the codec read: its size, and four bytes for every pixel. */
    record Pixels(int wide, int high, byte[] rgba) {
    }

    /**
     * Whether this codec knows a format by the name /AS gives it.
     *
     * <p>Asked before anything is read, because a name the codec has not got
     * is the caller's mistake and a different failure from bytes it cannot
     * make sense of.
     */
    boolean knows(String type);

    /**
     * The pixels of an encoded image, or null where the bytes are not one.
     *
     * <p>{@code frame} counts from one, for the formats that hold several
     * images; asking past the end answers null the same way unreadable bytes
     * do, and the caller decides which failure that is.
     *
     * <p>{@code type} may be empty, which means the bytes should say what they
     * are.
     */
    Pixels decoded(byte[] encoded, String type, int frame);

    /** The bytes of an image in the named format. */
    byte[] encoded(Pixels image, String type);

    /** A port that answers nothing, which is what a script gets by default. */
    static ImagePort none() {
        return new ImagePort() {
            @Override
            public boolean knows(String type) {
                throw refuse();
            }

            @Override
            public Pixels decoded(byte[] encoded, String type, int frame) {
                throw refuse();
            }

            @Override
            public byte[] encoded(Pixels image, String type) {
                throw refuse();
            }

            private Raised refuse() {
                return Raised.of(EvaluationFailure.FEATURE_NA,
                        "image encoding through the operating system");
            }
        };
    }
}
