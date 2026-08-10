package org.jebol.domain.value;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A named group of datatypes, written {@code number!} or {@code series!}.
 *
 * <p>Distinct from a {@link Datatype}: {@code integer!} names one thing a
 * value can be, {@code number!} names several. Function specs use both
 * interchangeably, which is why {@code func [n [number!]]} has to mean
 * something.
 */
public enum Typeset {
    ANY_TYPE("any-type", EnumSet.allOf(Datatype.class)),
    NUMBER("number", EnumSet.of(Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT)),
    SCALAR("scalar", datatypesWhere(Datatype::isScalar)),
    SERIES("series", datatypesWhere(Datatype::isSeries)),
    // The families R3 names that are not about series or words.
    // base-defs.reb declares its generated functions against ANY-OBJECT!,
    // so its absence stopped six functions being defined.
    ANY_OBJECT("any-object", EnumSet.of(
            Datatype.OBJECT, Datatype.MODULE, Datatype.ERROR,
            Datatype.TASK, Datatype.PORT)),

    // What COPY answers a copy of. Everything else is immediate: a value
    // with nothing inside it to share, so copying one would be the same
    // as not copying it.
    COPYABLE("copyable", EnumSet.of(
            Datatype.BINARY, Datatype.STRING, Datatype.FILE, Datatype.EMAIL,
            Datatype.REF, Datatype.URL, Datatype.TAG, Datatype.BITSET,
            Datatype.IMAGE, Datatype.VECTOR, Datatype.BLOCK, Datatype.PAREN,
            Datatype.PATH, Datatype.SET_PATH, Datatype.GET_PATH, Datatype.LIT_PATH,
            Datatype.HASH, Datatype.MAP, Datatype.NATIVE, Datatype.ACTION,
            Datatype.REBCODE, Datatype.COMMAND, Datatype.OP, Datatype.CLOSURE,
            Datatype.FUNCTION, Datatype.OBJECT, Datatype.ERROR, Datatype.PORT)),

    IMMEDIATE("immediate", EnumSet.of(
            Datatype.NONE, Datatype.LOGIC, Datatype.INTEGER, Datatype.DECIMAL,
            Datatype.PERCENT, Datatype.MONEY, Datatype.CHAR, Datatype.PAIR,
            Datatype.TUPLE, Datatype.TIME, Datatype.DATE, Datatype.DATATYPE,
            Datatype.TYPESET, Datatype.WORD, Datatype.SET_WORD, Datatype.GET_WORD,
            Datatype.LIT_WORD, Datatype.REFINEMENT, Datatype.ISSUE, Datatype.EVENT)),

    // The ones no script sees as a value of its own.
    INTERNAL("internal", EnumSet.of(
            Datatype.END, Datatype.UNSET, Datatype.FRAME, Datatype.HANDLE)),

    ANY_STRING("any-string", datatypesWhere(Datatype::isAnyString)),
    ANY_BLOCK("any-block", datatypesWhere(Datatype::isAnyBlock)),
    ANY_PATH("any-path", datatypesWhere(Datatype::isAnyPath)),
    ANY_WORD("any-word", datatypesWhere(Datatype::isAnyWord)),
    ANY_FUNCTION("any-function", datatypesWhere(Datatype::isAnyFunction));

    private final String spelling;
    private final Set<Datatype> members;

    Typeset(String spelling, Set<Datatype> members) {
        this.spelling = spelling;
        this.members = Set.copyOf(members);
    }

    private static Set<Datatype> datatypesWhere(java.util.function.Predicate<Datatype> test) {
        return Stream.of(Datatype.values()).filter(test).collect(Collectors.toSet());
    }

    public String spelling() {
        return spelling;
    }

    public String literalSpelling() {
        return spelling + "!";
    }

    public Set<Datatype> members() {
        return members;
    }

    /** The typeset with this name, if there is one. */
    public static Optional<Typeset> named(String spelling) {
        String wanted = spelling.toLowerCase(Locale.ROOT);
        return Stream.of(values())
                .filter(typeset -> typeset.spelling.equals(wanted))
                .findFirst();
    }
}
