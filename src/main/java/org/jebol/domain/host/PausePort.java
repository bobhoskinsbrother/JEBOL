package org.jebol.domain.host;

/**
 * How the interpreter lets time pass while a script waits.
 *
 * <p>A port the domain owns and an adapter implements, because no two
 * platforms stop for time the same way: a desktop lets the thread sleep, a
 * browser's main thread must keep running its event loop, and a worker can
 * be stopped outright. The interpreter keeps the length and the check
 * between slices, because a deadline is the run's and no platform can know
 * about one.
 *
 * <p>One slice at a time. A caller asks for at most fifty milliseconds and
 * looks at its own reason to stop before asking again, so a long wait is
 * ended by a deadline rather than outliving it.
 *
 * <p>Specified in {@code spec/natives.allium}.
 */
@FunctionalInterface
public interface PausePort {

    /**
     * Lets the given time pass, or less when the platform wakes the caller
     * early.
     *
     * @return true when the whole slice passed, false when it did not, which
     *     ends the wait rather than failing it
     */
    boolean pauseFor(long milliseconds);

    /**
     * A pause that cannot happen, which is what a bare evaluator gets.
     *
     * <p>Not a stand-in that answers yes: a wait that quietly did nothing
     * would look like a wait that finished, and a script cannot tell those
     * apart. An interpreter a host built installs its own pause before any
     * script runs, so this is only met by an evaluator built by hand.
     */
    static PausePort none() {
        return milliseconds -> {
            throw new FilePort.Denied("no-port",
                    "this interpreter was given no way to let time pass");
        };
    }
}
