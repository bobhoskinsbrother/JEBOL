package org.jebol.domain.value;

/**
 * The absence of a value.
 *
 * <p>Distinct from {@link NoneValue}, which is a value meaning "nothing".
 * {@code unset!} is what an unassigned word holds and what a function that
 * returned nothing gives back. Using one as a condition is an error rather
 * than a question with an answer, which is why {@link #isConditional()} is
 * false.
 */
public record UnsetValue() implements Value {

    private static final UnsetValue INSTANCE = new UnsetValue();

    /** The single unset value. All unset values are equal. */
    public static UnsetValue unset() {
        return INSTANCE;
    }

    @Override
    public Datatype datatype() {
        return Datatype.UNSET;
    }

    @Override
    public boolean isConditional() {
        return false;
    }

    @Override
    public String toString() {
        return "unset";
    }
}
