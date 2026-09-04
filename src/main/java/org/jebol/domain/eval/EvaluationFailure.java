package org.jebol.domain.eval;

import org.jebol.domain.value.ErrorCategory;

/** Why evaluation stopped, identified by id rather than by message text. */
public enum EvaluationFailure {
    NO_VALUE(ErrorCategory.SCRIPT, "no-value", "a word whose slot holds unset was evaluated"),
    NOT_DEFINED(ErrorCategory.SCRIPT, "not-defined", "a word with no binding was evaluated"),
    NOT_IN_CONTEXT(ErrorCategory.SCRIPT, "not-in-context",
            "a word the specified context does not hold"),
    NEED_VALUE(ErrorCategory.SCRIPT, "need-value", "a set-word with nothing after it to assign"),
    NO_ARG(ErrorCategory.SCRIPT, "no-arg", "a call reached the end of the block still short"),
    NO_OP_ARG(ErrorCategory.SCRIPT, "no-op-arg", "an operator with nothing on its left"),
    EXPECT_ARG(ErrorCategory.SCRIPT, "expect-arg", "an argument of the wrong datatype"),
    INVALID_PATH(ErrorCategory.SCRIPT, "invalid-path", "a path segment that selects nothing"),
    EXPECT_VAL(ErrorCategory.SCRIPT, "expect-val",
            "a value of the wrong kind where a spec block wanted one"),
    BAD_FIELD_SET(ErrorCategory.SCRIPT, "bad-field-set",
            "a field this thing has not got, or a value that field will not hold"),
    PAST_END(ErrorCategory.SCRIPT, "past-end",
            "a change at a position the series does not reach"),
    INVALID_HANDLE(ErrorCategory.SCRIPT, "invalid-handle",
            "a handle of the wrong kind for what was asked of it"),
    NOT_OPEN(ErrorCategory.ACCESS, "not-open",
            "a port asked to carry something before it was opened"),
    NO_CONNECT(ErrorCategory.ACCESS, "no-connect",
            "a connection that could not be made or that broke"),
    ALREADY_USED(ErrorCategory.SCRIPT, "already-used",
            "a name that something else in the same catalogue already holds"),
    BAD_MEDIA(ErrorCategory.SCRIPT, "bad-media",
            "data a codec could not read, or an action it does not do"),
    CANNOT_USE(ErrorCategory.SCRIPT, "cannot-use", "an operation this datatype does not support"),
    BAD_MAKE_ARG(ErrorCategory.SCRIPT, "bad-make-arg", "a value this datatype cannot be made from"),
    INVALID_SPEC(ErrorCategory.SCRIPT, "invalid-spec", "a spec this datatype cannot be built from"),
    INVALID_UTF(ErrorCategory.SCRIPT, "invalid-utf",
            "bytes that are not valid UTF-8 where text was wanted"),
    INVALID_DATA(ErrorCategory.SCRIPT, "invalid-data",
            "data that is not in the form the operation reads"),
    BAD_PRESS(ErrorCategory.SCRIPT, "bad-press",
            "compressed data that cannot be read back"),
    BAD_FUNC_ARG(ErrorCategory.SCRIPT, "bad-func-arg",
            "an argument a function will not accept in that position"),
    BAD_FUNC_DEF(ErrorCategory.SCRIPT, "bad-func-def", "invalid function definition"),
    DUP_VARS(ErrorCategory.SCRIPT, "dup-vars", "duplicate variable specified"),
    WRONG_TYPE(ErrorCategory.SCRIPT, "wrong-type",
            "a value whose datatype is not one the caller declared"),
    INVALID_PART(ErrorCategory.SCRIPT, "invalid-part",
            "a /part limit that names no length in the series being read"),
    INVALID_CHARS(ErrorCategory.SCRIPT, "invalid-chars", "characters that do not belong in the target"),
    TOO_SHORT(ErrorCategory.SCRIPT, "too-short", "nothing there to convert"),
    TOO_LONG(ErrorCategory.SCRIPT, "too-long", "more content than the target will hold"),
    LOCKED_WORD(ErrorCategory.SCRIPT, "locked-word", "an assignment to a protected slot"),
    INVALID_ARG(ErrorCategory.SCRIPT, "invalid-arg", "an argument that makes no sense here"),
    INVALID_COMPARE(ErrorCategory.SCRIPT, "invalid-compare",
            "two datatypes that cannot be put in an order"),
    BAD_PATH_SET(ErrorCategory.SCRIPT, "bad-path-set",
            "a path segment that cannot be written, or a value it will not hold"),
    OUT_OF_RANGE(ErrorCategory.SCRIPT, "out-of-range",
            "a number outside the range this operation allows"),
    MISSING_ARG(ErrorCategory.SCRIPT, "missing-arg",
            "missing a required argument or refinement"),
    INVALID_TYPE(ErrorCategory.SCRIPT, "invalid-type", "type is not allowed here"),
    NO_REFINE(ErrorCategory.SCRIPT, "no-refine", "a refinement this function does not have"),
    BAD_REFINES(ErrorCategory.SCRIPT, "bad-refines",
            "two refinements that contradict each other"),
    BAD_REFINE(ErrorCategory.SCRIPT, "bad-refine",
            "incompatible refinement:"),
    DIALECT(ErrorCategory.SCRIPT, "dialect",
            "a value a dialect has no meaning for at that point"),
    PARSE_END(ErrorCategory.SCRIPT, "parse-end", "a repeat count with no rule after it to repeat"),
    PARSE_RULE(ErrorCategory.SCRIPT, "parse-rule",
            "a value that cannot be used as a parse rule"),
    NO_SERVICE(ErrorCategory.ACCESS, "no-service",
            "a host service the script was not granted, or that nothing can offer"),
    CALL_FAIL(ErrorCategory.ACCESS, "call-fail", "external process failed"),
    NOT_HERE(ErrorCategory.ACCESS, "not-here",
            "something this machine's operating system does not offer"),
    PERMISSION_DENIED(ErrorCategory.ACCESS, "permission-denied",
            "something the operating system would not let this process do"),
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
    NOT_RELATED(ErrorCategory.SCRIPT, "not-related",
            "an operation and a datatype that do not go together"),
    NOT_SAME_CLASS(ErrorCategory.SCRIPT, "not-same-class",
            "two datatypes that hold different things, so neither can be read as the other"),
    TYPE_MISMATCH(ErrorCategory.SCRIPT, "type-mismatch",
            "two arguments that had to be the same datatype and were not"),
    /**
     * What a build cannot do, as opposed to what a script may not.
     *
     * <p>Rebol's own error for it, in the Internal category:
     * {@code feature-na: {feature not available}}. The C raises it where a
     * function's body is compiled out -- the six debug-only chants EVOKE
     * refuses in a release build -- so it already means "this build, not this
     * language", which is exactly what a JEBOL that has not got something needs
     * to say.
     */
    FEATURE_NA(ErrorCategory.INTERNAL, "feature-na", "feature not available"),
    /** A parse command the language reserves but has not yet implemented. */
    NOT_DONE(ErrorCategory.INTERNAL, "not-done",
            "reserved for future use (or not yet implemented)"),
    /** What MAKE raises for a construction block it cannot read. */
    MALCONSTRUCT(ErrorCategory.SYNTAX, "malconstruct",
            "a construction block this datatype cannot be made from"),
    /**
     * A size past what the datatype allows.
     *
     * <p>`size-limit: [{maximum limit reached:} :arg1]`, which an image raises
     * for a side past 0xFFFF: `Trap1(RE_SIZE_LIMIT, Get_Type(REB_IMAGE))`.
     */
    SIZE_LIMIT(ErrorCategory.INTERNAL, "size-limit", "maximum limit reached"),
    ZERO_DIVIDE(ErrorCategory.MATH, "zero-divide", "division by zero"),
    VECTOR_NOT_COMPATIBLE(ErrorCategory.SCRIPT, "vector-not-compatible",
            "two vectors that do not hold their numbers the same way"),
    NOT_SAME_TYPE(ErrorCategory.SCRIPT, "not-same-type",
            "two values that are not the same kind of thing"),
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
