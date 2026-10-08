package org.jebol.domain.value;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jebol.domain.value.Datatype.*;

public enum Typeset {
    ANY_TYPE("any-type", EnumSet.complementOf(EnumSet.of(END))),
    NUMBER("number", EnumSet.of(INTEGER, DECIMAL, PERCENT)),
    SCALAR("scalar", datatypesWhere(Datatype::isScalar)),
    SERIES("series", datatypesWhere(Datatype::isSeries)),
    ANY_OBJECT("any-object", EnumSet.of(OBJECT, MODULE, ERROR, TASK, PORT)),
    COPYABLE("copyable", EnumSet.of(
            BINARY, STRING, FILE, EMAIL, REF, URL, TAG, BITSET, IMAGE, VECTOR, BLOCK,  PAREN, PATH, SET_PATH, GET_PATH,
            LIT_PATH, HASH, MAP, NATIVE, ACTION, REBCODE, COMMAND, OP, CLOSURE, FUNCTION, OBJECT, ERROR, PORT)),
    IMMEDIATE("immediate", EnumSet.of(
            NONE, LOGIC, INTEGER, DECIMAL, PERCENT, MONEY, CHAR, PAIR, TUPLE, TIME, DATE, DATATYPE, TYPESET, WORD,
            SET_WORD, GET_WORD, LIT_WORD, REFINEMENT, ISSUE, EVENT)),
    INTERNAL("internal", EnumSet.of(END, UNSET, FRAME, HANDLE)),
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

    public Set<Datatype> membersAnd(Datatype... alsoTaken) {
        Set<Datatype> taken = EnumSet.copyOf(members);
        taken.addAll(Set.of(alsoTaken));
        return Set.copyOf(taken);
    }

    public static Optional<Typeset> named(String spelling) {
        String wanted = spelling.toLowerCase(Locale.ROOT);
        return Stream.of(values()).filter(typeset -> typeset.spelling.equals(wanted)).findFirst();
    }
}
