package org.jebol.domain.value;

/**
 * The single {@code none} value: a value that means nothing.
 *
 * <p>One of only two things REBOL treats as false, the other being a false
 * {@link LogicValue}.
 */
public record NoneValue() implements Value {

    private static final NoneValue INSTANCE = new NoneValue();

    /** The single none value. All none values are equal. */
    public static NoneValue none() {
        return INSTANCE;
    }

    @Override
    public Datatype datatype() {
        return Datatype.NONE;
    }

    @Override
    public boolean isTruthy() {
        return false;
    }

    @Override
    public String toString() {
        return "none";
    }
}
