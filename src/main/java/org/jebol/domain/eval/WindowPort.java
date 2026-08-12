package org.jebol.domain.eval;

import java.util.List;
import java.util.Optional;

/**
 * How a script asks the operator for something through a window on a screen.
 *
 * <p>A port the domain owns and an adapter implements, so the evaluator never
 * touches a desktop. There is no default implementation on purpose: a script
 * with no port reaches nothing, because a host that has not thought about
 * whether a script may put a window on the operator's screen has not decided
 * that it may.
 *
 * <p>Five requests and one grant. A host that will put one dialog on the
 * screen will put any of them there, so a grant per dialog would let the host
 * say which verb and not which screen, and the verb was never the interesting
 * half.
 *
 * <p>Every request answers empty when the operator declines. That is an
 * answer and not a refusal, and the two must never be reported the same way:
 * a person who closes a file chooser has answered the question, and a script
 * that cannot tell "no file" from "no permission" retries the wrong one.
 *
 * <p>Specified in {@code spec/embed.allium}.
 */
public interface WindowPort {

    /** Opens a URL or a local file in a web browser. */
    void browse(String target);

    /**
     * Asks the operator to choose a file, or empty if they declined.
     *
     * @param forSaving whether the dialog is a save dialog rather than an open
     *     one, which changes what the operator is allowed to name
     * @param allowingMany whether more than one file may be chosen
     * @param suggestedName a file or directory to start at, or empty
     * @param title the window's heading, or empty for the host's own
     * @param filterPairs a display name then the pattern it selects, over and
     *     over, or empty for no filtering
     */
    List<String> chooseFiles(
            boolean forSaving, boolean allowingMany,
            Optional<String> suggestedName, Optional<String> title,
            List<String> filterPairs);

    /** Asks the operator to choose a directory, or empty if they declined. */
    Optional<String> chooseDirectory(
            Optional<String> startingAt, Optional<String> title);

    /**
     * Asks the operator to choose a colour, as three octets, or empty if they
     * declined.
     */
    Optional<int[]> chooseColour(Optional<int[]> suggested);

    /**
     * Asks the operator for a secret, or empty if they declined.
     *
     * <p>What the operator types must not appear on the screen. That is the
     * whole reason this is a separate request rather than a text dialog.
     *
     * <p>No prompt to pass on: the C declares REQUEST-PASSWORD with no argument
     * at all, and a caller that wants a question prints one first. Rebol's own
     * ASK-PASSWORD is exactly that -- `prin question` and then this.
     */
    Optional<String> askForPassword();

    /** Why a port refused. Carries an error id the boundary reports. */
    final class Denied extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient String errorId;

        public Denied(String errorId, String because) {
            super(because, null, false, false);
            this.errorId = errorId;
        }

        public String errorId() {
            return errorId;
        }
    }

    /**
     * A port with no screen behind it, which is what a script gets by default.
     *
     * <p>It refuses rather than answering empty, and the difference matters: an
     * empty answer means the operator declined, and there is no operator here
     * to decline. A host that granted the windows service and supplied no port
     * has no such service to give, which is {@code not_present} rather than
     * {@code not_granted}.
     */
    static WindowPort none() {
        return new WindowPort() {
            @Override
            public void browse(String target) {
                throw refuse();
            }

            @Override
            public List<String> chooseFiles(
                    boolean forSaving, boolean allowingMany,
                    Optional<String> suggestedName, Optional<String> title,
                    List<String> filterPairs) {
                throw refuse();
            }

            @Override
            public Optional<String> chooseDirectory(
                    Optional<String> startingAt, Optional<String> title) {
                throw refuse();
            }

            @Override
            public Optional<int[]> chooseColour(Optional<int[]> suggested) {
                throw refuse();
            }

            @Override
            public Optional<String> askForPassword() {
                throw refuse();
            }

            private Denied refuse() {
                return new Denied("no-service",
                        "this interpreter was given no screen to put a window on");
            }
        };
    }
}
