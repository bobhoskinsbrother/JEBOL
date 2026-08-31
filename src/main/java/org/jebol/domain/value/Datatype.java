package org.jebol.domain.value;

import java.util.Set;

/**
 * Every datatype JEBOL represents, as specified in {@code spec/values.allium}.
 *
 * <p>The spelling is the REBOL name without its trailing exclamation mark, so
 * {@code SET_WORD} reports itself as {@code set-word}. The typeset predicates
 * mirror the standard R3-Alpha typesets rather than being invented here.
 *
 * <p><b>The order is {@code types.reb}'s and is not free to change.</b> A
 * typeset molds its members by walking the table, so the order is visible in
 * every one that writes itself down: {@code mold any-string!} is
 * {@code [string! file! email! ref! url! tag!]} because that is the order the
 * table lists them in, and nothing else would read back the same. Forty-three
 * of the fifty-eight used to sit somewhere else, which was invisible until a
 * typeset was molded and compared.
 *
 * <p>{@code java-object!} is last because it is JEBOL's own and Rebol's table
 * has no row for it.
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
    BINARY("binary"),
    STRING("string"),
    FILE("file"),
    EMAIL("email"),
    REF("ref"),
    URL("url"),
    TAG("tag"),
    BITSET("bitset"),
    IMAGE("image"),
    VECTOR("vector"),
    BLOCK("block"),
    PAREN("paren"),
    PATH("path"),
    SET_PATH("set-path"),
    GET_PATH("get-path"),
    LIT_PATH("lit-path"),
    HASH("hash"),
    MAP("map"),
    DATATYPE("datatype"),
    TYPESET("typeset"),
    WORD("word"),
    SET_WORD("set-word"),
    GET_WORD("get-word"),
    LIT_WORD("lit-word"),
    REFINEMENT("refinement"),
    ISSUE("issue"),
    NATIVE("native"),
    ACTION("action"),
    REBCODE("rebcode"),
    COMMAND("command"),
    OP("op"),
    CLOSURE("closure"),
    FUNCTION("function"),
    FRAME("frame"),
    OBJECT("object"),
    MODULE("module"),
    ERROR("error"),
    TASK("task"),
    PORT("port"),
    GOB("gob"),
    EVENT("event"),
    HANDLE("handle"),
    STRUCT("struct"),
    LIBRARY("library"),
    UTYPE("utype"),
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
