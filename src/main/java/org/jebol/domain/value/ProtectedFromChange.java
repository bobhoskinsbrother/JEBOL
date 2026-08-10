package org.jebol.domain.value;

/**
 * Thrown when something tries to change protected storage.
 *
 * <p>Unchecked and carrying nothing, because the layer that catches it
 * knows which native was running and can raise a REBOL error naming it.
 * The value layer has no business building errors; it only knows that
 * the change is not allowed.
 */
public final class ProtectedFromChange extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProtectedFromChange() {
        super("this value is protected from changing", null, false, false);
    }
}
