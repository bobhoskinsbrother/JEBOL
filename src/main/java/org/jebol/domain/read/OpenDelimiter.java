package org.jebol.domain.read;

/** A delimiter the reader must find an end for. */
public enum OpenDelimiter {
    BRACKET("[", "]"),
    PARENTHESIS("(", ")"),
    BRACE("{", "}"),
    QUOTE("\"", "\""),
    TAG("<", ">"),
    BINARY_BRACE("#{", "}");

    private final String opening;
    private final String closing;

    OpenDelimiter(String opening, String closing) {
        this.opening = opening;
        this.closing = closing;
    }

    public String opening() {
        return opening;
    }

    public String closing() {
        return closing;
    }
}
