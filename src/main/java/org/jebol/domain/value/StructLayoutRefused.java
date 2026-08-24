package org.jebol.domain.value;

/**
 * A layout block that does not declare a struct, and which of the two ways it
 * fails.
 *
 * <p>{@code Prepare_Struct} and {@code parse_field_type} refuse for different
 * reasons and Rebol reports them differently. A block whose shape is not
 * word-then-block at all -- {@code [a]}, {@code []}, {@code ["test" "test"]}
 * -- is a malconstruct. A block of the right shape naming a type that does not
 * exist, or carrying something after the type, is an invalid argument:
 * {@code [a [23]]} and {@code [a [int8! foo]]} both land here.
 *
 * <p>This is the value layer, which knows nothing about errors a script can
 * catch. The caller turns {@link #malconstructed} into the right one.
 */
public final class StructLayoutRefused extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient boolean malconstructed;

    private StructLayoutRefused(String why, boolean malconstructed) {
        super(why);
        this.malconstructed = malconstructed;
    }

    public static StructLayoutRefused becauseTheShapeIsWrong(String why) {
        return new StructLayoutRefused(why, true);
    }

    public static StructLayoutRefused becauseTheFieldIsWrong(String why) {
        return new StructLayoutRefused(why, false);
    }

    public boolean malconstructed() {
        return malconstructed;
    }
}
