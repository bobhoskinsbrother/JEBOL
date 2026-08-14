package org.jebol.domain.read;

/**
 * Why a read failed. Each maps to an {@code error!} of category
 * {@code syntax}, identified by {@link #errorId()} rather than by message
 * text, because wording differs between REBOL versions and is not behaviour.
 */
public enum SyntaxFailure {
    INVALID_LEXEME("invalid", "characters that begin no known literal"),
    MISSING_CLOSE("missing", "end of input inside an open series"),
    EXTRA_CLOSE("missing", "a closing delimiter with no opener"),
    MISMATCHED_CLOSE("missing", "a bracket closing a parenthesis, or the reverse"),
    UNTERMINATED_STRING("unterminated-string", "end of input inside a string"),
    INVALID_ESCAPE("invalid-escape", "a caret escape naming no known character"),
    INTEGER_OUT_OF_RANGE("integer-out-of-range", "digits beyond the 64-bit range"),
    INVALID_BINARY("invalid", "contents that are not a binary in the base given"),
    MALCONSTRUCT("malconstruct", "a construct whose datatype cannot be built that way"),
    INVALID_ARG("invalid-arg", "a map literal whose keys and values do not pair up"),
    NESTING_TOO_DEEP("nesting-too-deep", "blocks nested deeper than the reader accepts"),
    NEEDS("needs", "a script whose needs: header this interpreter does not meet"),
    PAST_END("past-end", "a read asked for a value where the source has none left");

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
