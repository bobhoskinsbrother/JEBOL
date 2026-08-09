package org.jebol.domain.eval;

import java.util.Optional;

/**
 * Whether a running script should stop, and why.
 *
 * <p>A port the domain owns. The evaluator asks; something outside decides.
 * That keeps clocks and cancellation flags out of the domain while still
 * letting a bound be enforced rather than advertised.
 *
 * <p>Cooperative on purpose. The evaluator checks between steps rather than
 * being interrupted where it stands, because stopping a thread mid-mutation
 * would leave a series half-changed, and series are shared, so the damage
 * would outlive the script that caused it.
 */
public interface Interruption {

    /** Why the script should stop, or empty if it should carry on. */
    Optional<String> reasonToStop();

    /** An interruption that never fires, for a script nothing is watching. */
    static Interruption never() {
        return Optional::empty;
    }
}
