package org.jebol.domain.host;

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
 * <p>The text is the body as it molds. Running it -- {@code do} and all --
 * is the adapter's job, because how a body is made to evaluate is not the
 * language's business. The port is called from the interpreter's own thread
 * and returns before the body has produced anything; whatever the body
 * writes goes to the output port it is handed, and nothing crosses back.
 *
 * <p>Specified in {@code spec/natives.allium}.
 */
public interface TaskPort {

    /**
     * Runs the body somewhere this call does not wait for.
     *
     * @param bodyAsWritten the body block as it molds, ready to be evaluated
     * @param output where the body's own writing goes
     */
    void runTheBody(String bodyAsWritten, OutputPort output);

    /** The port every interpreter starts with: DO of a task starts nothing. */
    static TaskPort none() {
        return (bodyAsWritten, output) -> {
        };
    }
}
