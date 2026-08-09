package org.jebol.domain.value;

import java.util.Set;

/**
 * Every datatype JEBOL represents, as specified in {@code spec/values.allium}.
 *
 * <p>The spelling is the REBOL name without its trailing exclamation mark, so
 * {@code SET_WORD} reports itself as {@code set-word}. The typeset predicates
 * mirror the standard R3-Alpha typesets rather than being invented here.
 */
public enum Datatype {
    UNSET("unset"),
    NONE("none"),
    LOGIC("logic"),
    INTEGER("integer"),
    DECIMAL("decimal"),
    PERCENT("percent"),
    MONEY("money"),
    CHAR("char"),
    PAIR("pair"),
    TUPLE("tuple"),
    TIME("time"),
    DATE("date"),
    STRING("string"),
    FILE("file"),
    URL("url"),
    EMAIL("email"),
    TAG("tag"),
    BINARY("binary"),
    WORD("word"),
    SET_WORD("set-word"),
    GET_WORD("get-word"),
    LIT_WORD("lit-word"),
    REFINEMENT("refinement"),
    ISSUE("issue"),
    BLOCK("block"),
    PAREN("paren"),
    PATH("path"),
    SET_PATH("set-path"),
    GET_PATH("get-path"),
    LIT_PATH("lit-path"),
    DATATYPE("datatype"),
    TYPESET("typeset"),
    NATIVE("native"),
    FUNCTION("function"),
    OP("op"),
    OBJECT("object"),
    ERROR("error"),
    JAVA_OBJECT("java-object");

    private static final Set<Datatype> ANY_STRING =
            Set.of(STRING, FILE, URL, EMAIL, TAG);
    private static final Set<Datatype> ANY_BLOCK =
            Set.of(BLOCK, PAREN, PATH, SET_PATH, GET_PATH, LIT_PATH);
    private static final Set<Datatype> ANY_PATH =
            Set.of(PATH, SET_PATH, GET_PATH, LIT_PATH);
    private static final Set<Datatype> ANY_WORD =
            Set.of(WORD, SET_WORD, GET_WORD, LIT_WORD, REFINEMENT, ISSUE);
    private static final Set<Datatype> NUMBER =
            Set.of(INTEGER, DECIMAL, PERCENT);
    private static final Set<Datatype> SCALAR =
            Set.of(INTEGER, DECIMAL, PERCENT, MONEY, CHAR, PAIR, TUPLE, TIME, DATE);
    private static final Set<Datatype> ANY_FUNCTION =
            Set.of(NATIVE, FUNCTION, OP);

    private final String spelling;

    Datatype(String spelling) {
        this.spelling = spelling;
    }

    /** The REBOL name without its trailing exclamation mark. */
    public String spelling() {
        return spelling;
    }

    /** The REBOL name as written in source, including the exclamation mark. */
    public String literalSpelling() {
        return spelling + "!";
    }

    public boolean isAnyString() {
        return ANY_STRING.contains(this);
    }

    public boolean isAnyBlock() {
        return ANY_BLOCK.contains(this);
    }

    public boolean isAnyPath() {
        return ANY_PATH.contains(this);
    }

    public boolean isAnyWord() {
        return ANY_WORD.contains(this);
    }

    public boolean isSeries() {
        return isAnyString() || isAnyBlock() || this == BINARY;
    }

    public boolean isNumber() {
        return NUMBER.contains(this);
    }

    public boolean isScalar() {
        return SCALAR.contains(this);
    }

    public boolean isAnyFunction() {
        return ANY_FUNCTION.contains(this);
    }
}
