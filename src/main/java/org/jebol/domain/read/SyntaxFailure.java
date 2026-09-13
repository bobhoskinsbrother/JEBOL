package org.jebol.domain.read;

import org.jebol.domain.value.ErrorCategory;

/**
 * Why a read failed, identified by {@link #errorId()} rather than by message
 * text, because wording differs between REBOL versions and is not behaviour.
 *
 * <p>The category is whichever one Rebol's catalogue files the id under, and
 * that is not always syntax: {@code invalid-arg} and {@code past-end} are
 * script failures even when it is the reader raising them. The category
 * decides the error's code as well as its type word, and a script can read
 * both, so the two must agree.
 *
 * <p>Several constants share an id on purpose, because the catalogue has
 * fewer names than the reader has mistakes: the three unbalanced-delimiter
 * failures all report {@code missing}, and an unterminated string, a bad
 * caret escape and an integer past the range all report {@code invalid}.
 * The constants stay apart because the reader and the console tell them
 * apart -- another line mends an unterminated string and mends nothing
 * else -- and because an id no Rebol names is worse than a coarse one.
 */
public enum SyntaxFailure {
    INVALID_LEXEME("invalid", "characters that begin no known literal"),
    MISSING_CLOSE("missing", "end of input inside an open series"),
    EXTRA_CLOSE("missing", "a closing delimiter with no opener"),
    MISMATCHED_CLOSE("missing", "a bracket closing a parenthesis, or the reverse"),
    UNTERMINATED_STRING("invalid", "end of input inside a string"),
    INVALID_ESCAPE("invalid", "a caret escape naming no known character"),
    INTEGER_OUT_OF_RANGE("invalid", "digits beyond the 64-bit range"),
    INVALID_BINARY("invalid", "contents that are not a binary in the base given"),
    MALCONSTRUCT("malconstruct", "a construct whose datatype cannot be built that way"),
    INVALID_ARG(ErrorCategory.SCRIPT, "invalid-arg",
            "a map literal whose keys and values do not pair up"),
    NESTED_PAST_THE_STACK(ErrorCategory.INTERNAL, "stack-overflow",
            "blocks nested deeper than the reader accepts"),
    NEEDS("needs", "a script whose needs: header this interpreter does not meet"),
    PAST_END(ErrorCategory.SCRIPT, "past-end",
            "a read asked for a value where the source has none left");

    private final ErrorCategory category;
    private final String errorId;
    private final String description;

    SyntaxFailure(String errorId, String description) {
        this(ErrorCategory.SYNTAX, errorId, description);
    }

    SyntaxFailure(ErrorCategory category, String errorId, String description) {
        this.category = category;
        this.errorId = errorId;
        this.description = description;
    }

    public ErrorCategory category() {
        return category;
    }

    public String errorId() {
        return errorId;
    }

    public String description() {
        return description;
    }
}
