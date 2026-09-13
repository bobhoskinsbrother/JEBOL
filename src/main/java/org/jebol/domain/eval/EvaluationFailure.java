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
    CANNOT_OPEN(ErrorCategory.ACCESS, "cannot-open",
            "a port that could not be opened on what it names"),
    NO_DELETE(ErrorCategory.ACCESS, "no-delete",
            "a file the host would not remove"),
    NO_RENAME(ErrorCategory.ACCESS, "no-rename",
            "a file the host would not move to the name asked for"),
    BAD_FILE_MODE(ErrorCategory.ACCESS, "bad-file-mode",
            "a file to be made new that nobody may write to"),
    INVALID_PORT(ErrorCategory.ACCESS, "invalid-port",
            "a port whose own fields are not what a port keeps there"),
    PROCESS_NOT_FOUND(ErrorCategory.ACCESS, "process-not-found",
            "a number no process on this machine is running under"),
    BAD_SERIES(ErrorCategory.INTERNAL, "bad-series",
            "a series carrying a zero where only the end may hold one"),
    NO_MEMORY(ErrorCategory.INTERNAL, "no-memory",
            "room the count cannot hold, or that the host could not give"),
    BAD_SYS_FUNC(ErrorCategory.INTERNAL, "bad-sys-func",
            "a function the interpreter calls by name that is no longer one"),
    WRITE_ERROR(ErrorCategory.ACCESS, "write-error",
            "a write the port would not carry"),
    READ_ONLY(ErrorCategory.ACCESS, "read-only",
            "a change asked of something opened only to read"),
    NOT_OPEN(ErrorCategory.ACCESS, "not-open",
            "a port asked to carry something before it was opened"),
    ALREADY_OPEN(ErrorCategory.ACCESS, "already-open",
            "a port asked to open when it was open already"),
    NO_CONNECT(ErrorCategory.ACCESS, "no-connect",
            "a connection that could not be made or that broke"),
    ALREADY_USED(ErrorCategory.SCRIPT, "already-used",
            "a name that something else in the same catalogue already holds"),
    BAD_MEDIA(ErrorCategory.ACCESS, "bad-media",
            "data a codec could not read, or an action it does not do"),
    NO_CODEC(ErrorCategory.ACCESS, "no-codec",
            "bytes no image codec here could read, or a format none can write"),
    CANNOT_USE(ErrorCategory.SCRIPT, "cannot-use", "an operation this datatype does not support"),
    BAD_MAKE_ARG(ErrorCategory.SCRIPT, "bad-make-arg", "a value this datatype cannot be made from"),
    INVALID_SPEC(ErrorCategory.ACCESS, "invalid-spec", "a spec this datatype cannot be built from"),
    INVALID_UTF(ErrorCategory.ACCESS, "invalid-utf",
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
    BAD_CHAR(ErrorCategory.SYNTAX, "bad-char",
            "a single character that spells no word on its own"),
    TOO_SHORT(ErrorCategory.SCRIPT, "too-short", "nothing there to convert"),
    TOO_LONG(ErrorCategory.SCRIPT, "too-long", "more content than the target will hold"),
    LOCKED_WORD(ErrorCategory.SCRIPT, "locked-word", "an assignment to a protected slot"),
    INVALID_ARG(ErrorCategory.SCRIPT, "invalid-arg", "an argument that makes no sense here"),
    INVALID_COMPARE(ErrorCategory.SCRIPT, "invalid-compare",
            "two datatypes that cannot be put in an order"),
    BAD_PATH_SET(ErrorCategory.SCRIPT, "bad-path-set",
            "a path segment that cannot be written, or a value it will not hold"),
    /**
     * A path into a datatype that has no parts to select from.
     *
     * <p>{@code bad-path-type: [{path} :arg1 {is not valid for} :arg2 {type}]},
     * and the two arguments are the whole path and the datatype it ran into.
     * One line apart from {@code invalid-path} in the C and asking a different
     * question: that one is a part a value has not got, this one is a value
     * that could never have had parts.
     */
    BAD_PATH_TYPE(ErrorCategory.SCRIPT, "bad-path-type",
            "a path into a datatype that has no parts"),
    OUT_OF_RANGE(ErrorCategory.SCRIPT, "out-of-range",
            "a number outside the range this operation allows"),
    /**
     * A value a datatype cannot hold, as opposed to a number a call will not
     * take.
     *
     * <p>{@code type-limit: [:arg1 {overflow/underflow}]}. Arithmetic on times
     * raises it where the answer would be longer than a duration can be --
     * {@code Add_Max} traps rather than clamping whenever it is given a type
     * to name.
     */
    TYPE_LIMIT(ErrorCategory.SCRIPT, "type-limit", "overflow/underflow"),
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
    PARSE_VARIABLE(ErrorCategory.SCRIPT, "parse-variable",
            "a place to put what was matched that is not a variable"),
    PARSE_COMMAND(ErrorCategory.SCRIPT, "parse-command",
            "a word the dialect reserves, written where a variable belongs"),
    PARSE_SERIES(ErrorCategory.SCRIPT, "parse-series",
            "an input the parse was switched to that is not a series"),
    /**
     * A verb this port's actor has no function for.
     *
     * <p>{@code no-port-action: [{this port does not support:} :arg1]}, and
     * the argument is the action's own word as the action table spells it,
     * which is a set-word.
     */
    NO_PORT_ACTION(ErrorCategory.ACCESS, "no-port-action",
            "this port does not support"),

    /**
     * A scheme whose actor is neither a word naming something built in nor an
     * object of functions, which is a scheme built wrongly rather than a port
     * used wrongly.
     */
    INVALID_ACTOR(ErrorCategory.ACCESS, "invalid-actor",
            "invalid port actor (must be native or object)"),

    NO_SERVICE(ErrorCategory.ACCESS, "no-service",
            "a host service the script was not granted, or that nothing can offer"),
    CALL_FAIL(ErrorCategory.ACCESS, "call-fail", "external process failed"),
    NOT_HERE(ErrorCategory.INTERNAL, "not-here",
            "something this machine's operating system does not offer"),
    PERMISSION_DENIED(ErrorCategory.ACCESS, "permission-denied",
            "something the operating system would not let this process do"),
    HIDDEN(ErrorCategory.SCRIPT, "hidden",
            "a field the object keeps to itself, reached from outside"),
    PARSE_NO_COLLECT(ErrorCategory.SCRIPT, "parse-no-collect",
            "a KEEP with no COLLECT around it to keep into"),
    PARSE_INTO_TYPE(ErrorCategory.SCRIPT, "parse-into-type",
            "a COLLECT INTO target that cannot hold what the parse yields"),
    STACK_OVERFLOW(ErrorCategory.INTERNAL, "stack-overflow",
            "deeper than this interpreter goes, by recursion or by nesting"),
    NO_RETURN(ErrorCategory.SCRIPT, "no-return", "block did not return a value"),
    SELF_PROTECTED(ErrorCategory.SCRIPT, "self-protected",
            "cannot set/unset self - it is protected"),
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
     *
     * <p>A script failure and not an internal one. `errors.reb` files it under
     * Script, which is where a mistake in what was asked for goes -- a caller
     * who wants a picture seventy thousand across can catch this. Internal is
     * where a defect in the interpreter goes, and saying so of a size somebody
     * typed points the blame at the wrong side.
     */
    SIZE_LIMIT(ErrorCategory.SCRIPT, "size-limit", "maximum limit reached"),

    /**
     * Something that cannot be made at the size or shape asked for.
     *
     * <p>`no-create: [{cannot create:} :arg1]`, filed under Access. RESIZE
     * raises it for a width so small the height works out at nothing: a tenth
     * of a row is not a picture, and there is no picture to answer with.
     */
    NO_CREATE(ErrorCategory.ACCESS, "no-create", "cannot create"),
    ZERO_DIVIDE(ErrorCategory.MATH, "zero-divide", "division by zero"),
    VECTOR_NOT_COMPATIBLE(ErrorCategory.SCRIPT, "vector-not-compatible",
            "two vectors that do not hold their numbers the same way"),
    NOT_SAME_TYPE(ErrorCategory.SCRIPT, "not-same-type",
            "two values that are not the same kind of thing"),
    OVERFLOW(ErrorCategory.MATH, "overflow", "arithmetic outside the representable range");

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
