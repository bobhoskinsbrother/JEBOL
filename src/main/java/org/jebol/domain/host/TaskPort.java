package org.jebol.domain.host;

import org.jebol.domain.eval.Crossing;

/**
 * Whether a task's body runs, and where.
 *
 * <p>A port the domain owns and an adapter implements, because the answer
 * differs by platform. A browser's worker has one execution context and the
 * script's run occupies it, so nothing can run a body elsewhere; a JVM host
 * keeps one interpreter per thread, so a body runs in a fresh interpreter on
 * a thread of its own. Every interpreter starts with the port that starts
 * nothing, which is also what a real 3.22.5 does: {@code DO} of a task
 * answers the task and the body produces nothing observable.
 *
 * <p>The body arrives as a {@link Crossing}: already checked for anything
 * that cannot cross, and still the sender's own. The port is called from the
 * sender's thread when DO is called, and copies the body into the interpreter
 * that will run it before returning, so a change the sender makes afterwards
 * never reaches the copy. Whatever the body writes goes to the output port
 * it is handed, and nothing crosses back.
 *
 * <p>Specified in {@code spec/natives.allium} and, for the copy,
 * {@code spec/values.allium}.
 */
public interface TaskPort {

    /**
     * Copies the body into an interpreter of its own and runs it somewhere
     * this call does not wait for.
     *
     * @param body the body block, checked and ready to copy
     * @param output where the body's own writing goes
     */
    void runBody(Crossing body, OutputPort output);

    /** Whether this port runs a body at all; the sender prepares one to cross only when it does. */
    default boolean runsBodies() {
        return true;
    }

    /** The port every interpreter starts with: DO of a task starts nothing. */
    static TaskPort none() {
        return new TaskPort() {
            @Override
            public void runBody(Crossing body, OutputPort output) {
            }

            @Override
            public boolean runsBodies() {
                return false;
            }
        };
    }
}
