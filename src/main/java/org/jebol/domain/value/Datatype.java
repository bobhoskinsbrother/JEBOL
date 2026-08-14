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
    END("end"),
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
    REF("ref"),
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
    MAP("map"),
    ERROR("error"),
    ACTION("action"),
    BITSET("bitset"),
    CLOSURE("closure"),
    COMMAND("command"),
    EVENT("event"),
    FRAME("frame"),
    GOB("gob"),
    HANDLE("handle"),
    HASH("hash"),
    IMAGE("image"),
    LIBRARY("library"),
    MODULE("module"),
    PORT("port"),
    REBCODE("rebcode"),
    STRUCT("struct"),
    TASK("task"),
    UTYPE("utype"),
    VECTOR("vector"),
    JAVA_OBJECT("java-object");

    private static final Set<Datatype> ANY_STRING =
            Set.of(STRING, FILE, URL, EMAIL, TAG, REF);
    private static final Set<Datatype> ANY_BLOCK =
            Set.of(BLOCK, PAREN, PATH, SET_PATH, GET_PATH, LIT_PATH, HASH);
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

    /**
     * The datatype a name stands for, with or without its exclamation
     * mark. Empty when the name is not a datatype's, because a bare word
     * in a block is far more often something else.
     */
    public static java.util.Optional<Datatype> named(String spelling) {
        String wanted = spelling.endsWith("!")
                ? spelling.substring(0, spelling.length() - 1)
                : spelling;
        for (Datatype datatype : values()) {
            if (datatype.spelling().equalsIgnoreCase(wanted)) {
                return java.util.Optional.of(datatype);
            }
        }
        return java.util.Optional.empty();
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

    /**
     * Whether this is a series datatype.
     *
     * <p>The last column of `boot/types.reb` is the authority, and it puts
     * `image` in `series` beside the strings, the blocks and the binary. An image
     * is a series whose element is four bytes, so every navigation action follows
     * from the membership rather than being written for it.
     */
    public boolean isSeries() {
        return isAnyString() || isAnyBlock()
                || this == BINARY || this == IMAGE || this == VECTOR;
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
