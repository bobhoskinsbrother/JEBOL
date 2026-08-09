package org.jebol.domain.value;

/**
 * One named binding inside a {@link Context}.
 *
 * <p>Mutable, because assignment through a set-word changes what a name holds
 * without producing a new context. Starts {@code unset} rather than
 * {@code none}: a name that exists but has not been assigned is precisely
 * what {@code unset!} means, and the evaluator reports that differently from
 * a name that was never bound at all.
 */
public final class ContextSlot {

    private final Context context;
    private final String spelling;
    private final String canonical;

    private Value value = UnsetValue.unset();
    private boolean protectedFromAssignment;

    ContextSlot(Context context, String spelling, String canonical) {
        this.context = context;
        this.spelling = spelling;
        this.canonical = canonical;
    }

    public Context context() {
        return context;
    }

    /** As first written, case preserved. */
    public String spelling() {
        return spelling;
    }

    /** Lowercased, the form used for lookup. */
    public String canonical() {
        return canonical;
    }

    public Value value() {
        return value;
    }

    public void setValue(Value replacement) {
        if (replacement == null) {
            throw new IllegalArgumentException(
                    "a slot holds unset, never null: use UnsetValue.unset()");
        }
        if (protectedFromAssignment) {
            throw new IllegalStateException(
                    "\"" + spelling + "\" is protected and cannot be assigned");
        }
        this.value = replacement;
    }

    public boolean isProtected() {
        return protectedFromAssignment;
    }

    public void protectFromAssignment() {
        this.protectedFromAssignment = true;
    }

    public void allowAssignment() {
        this.protectedFromAssignment = false;
    }

    public boolean holdsUnset() {
        return value.datatype() == Datatype.UNSET;
    }

    @Override
    public String toString() {
        return spelling + ": " + value;
    }
}
