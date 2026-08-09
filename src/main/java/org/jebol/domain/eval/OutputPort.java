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

    /** A port that discards everything, for when nothing is watching. */
    static OutputPort discarding() {
        return text -> {
        };
    }
}
