package org.jebol.domain.eval;

import org.jebol.domain.value.ErrorCategory;

/** Why evaluation stopped, identified by id rather than by message text. */
public enum EvaluationFailure {
    NO_VALUE(ErrorCategory.SCRIPT, "no-value", "a word whose slot holds unset was evaluated"),
    NOT_DEFINED(ErrorCategory.SCRIPT, "not-defined", "a word with no binding was evaluated"),
    NEED_VALUE(ErrorCategory.SCRIPT, "need-value", "a set-word with nothing after it to assign"),
    NO_ARG(ErrorCategory.SCRIPT, "no-arg", "a call reached the end of the block still short"),
    NO_OP_ARG(ErrorCategory.SCRIPT, "no-op-arg", "an operator with nothing on its left"),
    EXPECT_ARG(ErrorCategory.SCRIPT, "expect-arg", "an argument of the wrong datatype"),
    INVALID_PATH(ErrorCategory.SCRIPT, "invalid-path", "a path segment that selects nothing"),
    CANNOT_USE(ErrorCategory.SCRIPT, "cannot-use", "an operation this datatype does not support"),
    BAD_MAKE_ARG(ErrorCategory.SCRIPT, "bad-make-arg", "a value this datatype cannot be made from"),
    INVALID_CHARS(ErrorCategory.SCRIPT, "invalid-chars", "characters that do not belong in the target"),
    TOO_SHORT(ErrorCategory.SCRIPT, "too-short", "nothing there to convert"),
    LOCKED_WORD(ErrorCategory.SCRIPT, "locked-word", "an assignment to a protected slot"),
    INVALID_ARG(ErrorCategory.SCRIPT, "invalid-arg", "an argument that makes no sense here"),
    INVALID_COMPARE(ErrorCategory.SCRIPT, "invalid-compare",
            "two datatypes that cannot be put in an order"),
    BAD_PATH_SET(ErrorCategory.SCRIPT, "bad-path-set",
            "a path segment that cannot be written, or a value it will not hold"),
    OUT_OF_RANGE(ErrorCategory.SCRIPT, "out-of-range",
            "a number outside the range this operation allows"),
    NO_REFINE(ErrorCategory.SCRIPT, "no-refine", "a refinement this function does not have"),
    BAD_REFINES(ErrorCategory.SCRIPT, "bad-refines",
            "two refinements that contradict each other"),
    PARSE_END(ErrorCategory.SCRIPT, "parse-end", "a repeat count with no rule after it to repeat"),
    PARSE_RULE(ErrorCategory.SCRIPT, "parse-rule",
            "a value that cannot be used as a parse rule"),
    NO_SERVICE(ErrorCategory.ACCESS, "no-service",
            "a host service the script was not granted, or that nothing can offer"),
    HIDDEN(ErrorCategory.SCRIPT, "hidden",
            "a field the object keeps to itself, reached from outside"),
    PARSE_NO_COLLECT(ErrorCategory.SCRIPT, "parse-no-collect",
            "a KEEP with no COLLECT around it to keep into"),
    PARSE_INTO_TYPE(ErrorCategory.SCRIPT, "parse-into-type",
            "a COLLECT INTO target that cannot hold what the parse yields"),
    TOO_DEEP(ErrorCategory.SCRIPT, "too-deep", "nesting past the evaluation depth limit"),
    INVALID_CHAR(ErrorCategory.ACCESS, "invalid-char",
            "a code point outside the range Unicode defines"),
    ASSERT_FAILED(ErrorCategory.SCRIPT, "assert-failed",
            "an assertion that did not hold"),
    PROTECTED(ErrorCategory.SCRIPT, "protected",
            "a change to a value that was protected from changing"),
    NOT_SAME_CLASS(ErrorCategory.SCRIPT, "not-same-class",
            "two datatypes that hold different things, so neither can be read as the other"),
    TYPE_MISMATCH(ErrorCategory.SCRIPT, "type-mismatch",
            "two arguments that had to be the same datatype and were not"),
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
