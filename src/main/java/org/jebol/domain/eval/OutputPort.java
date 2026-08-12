package org.jebol.domain.eval;

/**
 * Where {@code print} and {@code prin} send their text.
 *
 * <p>A port the domain owns and an adapter implements, so the evaluator never
 * touches a stream. That is what lets a test capture output without a
 * temporary file, and what would let the same interpreter write to a socket
 * or a log without the domain knowing.
 */
public interface OutputPort {

    /** Writes text with no line ending. */
    void write(String text);

    /** Writes text followed by a line ending. */
    default void writeLine(String text) {
        write(text);
        write(System.lineSeparator());
    }

    /**
     * Pushes anything buffered out to wherever it is really going.
     *
     * <p>What FLUSH asks for. A no-op by default, because a port that writes
     * straight through has nothing held back, and only a buffered adapter has
     * anything to do.
     */
    default void flush() {
    }

    /** A port that discards everything, for when nothing is watching. */

    static OutputPort discarding() {
        return text -> {
        };
    }
}
