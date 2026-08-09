package org.jebol.domain.read;

/**
 * Why a read failed. Each maps to an {@code error!} of category
 * {@code syntax}, identified by {@link #errorId()} rather than by message
 * text, because wording differs between REBOL versions and is not behaviour.
 */
public enum SyntaxFailure {
    INVALID_LEXEME("invalid-lexeme", "characters that begin no known literal"),
    MISSING_CLOSE("missing-close", "end of input inside an open series"),
    EXTRA_CLOSE("extra-close", "a closing delimiter with no opener"),
    MISMATCHED_CLOSE("mismatched-close", "a bracket closing a parenthesis, or the reverse"),
    UNTERMINATED_STRING("unterminated-string", "end of input inside a string"),
    INVALID_ESCAPE("invalid-escape", "a caret escape naming no known character"),
    INTEGER_OUT_OF_RANGE("integer-out-of-range", "digits beyond the 64-bit range"),
    INVALID_DATATYPE("invalid-datatype", "a datatype spelling that names no datatype"),
    INVALID_BINARY("invalid-binary", "contents that are not valid base-16"),
    NESTING_TOO_DEEP("nesting-too-deep", "blocks nested deeper than the reader accepts");

    private final String errorId;
    private final String description;

    SyntaxFailure(String errorId, String description) {
        this.errorId = errorId;
        this.description = description;
    }

    public String errorId() {
        return errorId;
    }

    public String description() {
        return description;
    }
}
