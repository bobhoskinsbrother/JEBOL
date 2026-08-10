package org.jebol.domain.value;

/**
 * Thrown when something tries to assign to a protected slot.
 *
 * <p>The sibling of {@link ProtectedFromChange}, and separate from it
 * because REBOL reports the two differently: assigning through a name is
 * {@code locked-word} and changing a container is {@code protected}. The
 * error names the route rather than the value, so the value layer cannot
 * choose it -- this says only that the assignment is refused, and carries
 * the name so the layer above can say which slot.
 */
public final class SlotIsProtected extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient String spelling;

    public SlotIsProtected(String spelling) {
        super("\"" + spelling + "\" is protected and cannot be assigned",
                null, false, false);
        this.spelling = spelling;
    }

    /** The slot's name, as first written. */
    public String spelling() {
        return spelling;
    }
}
