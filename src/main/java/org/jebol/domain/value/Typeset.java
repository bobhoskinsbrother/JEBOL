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
