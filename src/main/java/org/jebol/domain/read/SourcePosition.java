package org.jebol.domain.read;

/**
 * Where in the source something was found, so a syntax error can point at the
 * offending text rather than at the file as a whole.
 *
 * @param line 1-based
 * @param column 1-based, counted in codepoints rather than UTF-16 units
 * @param offset 0-based codepoint offset from the start
 */
public record SourcePosition(int line, int column, int offset) {

    public SourcePosition {
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException(
                    "line and column are 1-based, got " + line + ":" + column);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset is 0-based, got " + offset);
        }
    }

    public static SourcePosition start() {
        return new SourcePosition(1, 1, 0);
    }

    @Override
    public String toString() {
        return line + ":" + column;
    }
}
