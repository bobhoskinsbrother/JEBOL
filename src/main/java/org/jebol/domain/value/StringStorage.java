package org.jebol.domain.value;

/**
 * The mutable buffer behind every {@code any-string!} value.
 *
 * <p>Identity matters and equality does not, for the same reason as
 * {@link BlockStorage}: two buffers holding the same text are still two
 * buffers.
 */
public final class StringStorage {

    private final CodepointBuffer buffer;

    public StringStorage() {
        this.buffer = new CodepointBuffer();
    }

    public StringStorage(String text) {
        this.buffer = new CodepointBuffer(text);
    }

    public static StringStorage of(String text) {
        return new StringStorage(text);
    }

    public int length() {
        return buffer.length();
    }

    /** The codepoint at a 1-based position. */
    public int at(int oneBasedIndex) {
        return buffer.at(oneBasedIndex - 1);
    }

    public void set(int oneBasedIndex, int codepoint) {
        buffer.set(oneBasedIndex - 1, codepoint);
    }

    public void append(int codepoint) {
        buffer.append(codepoint);
    }

    public void insertAt(int oneBasedIndex, int codepoint) {
        buffer.insertAt(oneBasedIndex - 1, codepoint);
    }

    public int removeAt(int oneBasedIndex) {
        return buffer.removeAt(oneBasedIndex - 1);
    }

    /** The text from a 1-based position to the end. */
    public String textFrom(int oneBasedIndex) {
        return buffer.text(oneBasedIndex - 1, buffer.length());
    }

    public String text() {
        return buffer.text(0, buffer.length());
    }

    @Override
    public String toString() {
        return "StringStorage(" + buffer.length() + " codepoints)";
    }
}
