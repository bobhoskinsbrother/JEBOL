package org.jebol.domain.eval;

import org.jebol.domain.value.ErrorCategory;

/** Why evaluation stopped, identified by id rather than by message text. */
public enum EvaluationFailure {
    NO_VALUE(ErrorCategory.SCRIPT, "no-value", "a word whose slot holds unset was evaluated"),
    NOT_DEFINED(ErrorCategory.SCRIPT, "not-defined", "a word with no binding was evaluated"),
    NEED_VALUE(ErrorCategory.SCRIPT, "need-value", "nothing left to supply a value"),
    EXPECT_ARG(ErrorCategory.SCRIPT, "expect-arg", "an argument of the wrong datatype"),
    INVALID_PATH(ErrorCategory.SCRIPT, "invalid-path", "a path segment that selects nothing"),
    CANNOT_USE(ErrorCategory.SCRIPT, "cannot-use", "an operation this datatype does not support"),
    PROTECTED_WORD(ErrorCategory.SCRIPT, "protected-word", "an assignment to a protected slot"),
    TOO_DEEP(ErrorCategory.SCRIPT, "too-deep", "nesting past the evaluation depth limit"),
    ZERO_DIVIDE(ErrorCategory.MATH, "zero-divide", "division by zero"),
    OVERFLOW(ErrorCategory.MATH, "overflow", "arithmetic outside the representable range"),
    THROWN(ErrorCategory.SCRIPT, "thrown", "an error value reached during evaluation");

    private final ErrorCategory category;
    private final String errorId;
    private final String description;

    EvaluationFailure(ErrorCategory category, String errorId, String description) {
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
