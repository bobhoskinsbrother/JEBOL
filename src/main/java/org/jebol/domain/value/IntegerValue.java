package org.jebol.domain.value;

/**
 * A 64-bit signed integer, as R3-Alpha's {@code integer!} is.
 *
 * <p>Zero is a value and therefore true. That catches everyone once.
 */
public record IntegerValue(long magnitude) implements Value {

    public static IntegerValue of(long magnitude) {
        return new IntegerValue(magnitude);
    }

    @Override
    public Datatype datatype() {
        return Datatype.INTEGER;
    }

    @Override
    public String toString() {
        return Long.toString(magnitude);
    }
}
