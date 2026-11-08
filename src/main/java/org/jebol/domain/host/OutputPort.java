package org.jebol.domain.host;

public interface OutputPort {

    void write(String text);
    default void writeLine(String text) {
        write(text);
        write(System.lineSeparator());
    }

    default void writeError(String text) {
        write(text);
    }

    default void flush() {
    }

    static OutputPort discard() {
        return text -> {
        };
    }
}
