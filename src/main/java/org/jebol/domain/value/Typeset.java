package org.jebol.domain.value;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public enum Typeset {
    ANY_TYPE("any-type", datatype -> datatype != EndValue.TYPE),
    NUMBER("number"),
    SCALAR("scalar"),
    SERIES("series"),
    ANY_OBJECT("any-object"),
    ANY_STRING("any-string"),
    ANY_BLOCK("any-block"),
    ANY_PATH("any-path"),
    ANY_WORD("any-word"),
    ANY_FUNCTION("any-function"),
    COPYABLE("copyable", datatype -> datatype.belongsTo(Typeset.SERIES)
            || datatype.belongsTo(Typeset.ANY_FUNCTION)
            || datatype == PortValue.TYPE
            || datatype == MapValue.TYPE
            || datatype == ObjectValue.TYPE
            || datatype == BitsetValue.TYPE
            || datatype == ErrorValue.TYPE),
    IMMEDIATE("immediate", datatype -> datatype.belongsTo(Typeset.SCALAR)
            || datatype.belongsTo(Typeset.ANY_WORD)
            || datatype == NoneValue.TYPE
            || datatype == LogicValue.TYPE
            || datatype == Datatype.TYPE
            || datatype == TypesetValue.TYPE
            || datatype == EventValue.TYPE),
    INTERNAL("internal", datatype -> datatype == EndValue.TYPE
            || datatype == UnsetValue.TYPE
            || datatype == FrameValue.TYPE
            || datatype == HandleValue.TYPE);

    private final String spelling;
    private final Predicate<Datatype> membership;

    Typeset(String spelling) {
        this.spelling = spelling;
        this.membership = datatype -> datatype.declares(this);
    }

    Typeset(String spelling, Predicate<Datatype> builtInBaseDefs) {
        this.spelling = spelling;
        this.membership = builtInBaseDefs;
    }

    public String spelling() {
        return spelling;
    }

    public String literalSpelling() {
        return spelling + "!";
    }

    public Set<Datatype> members() {
        return Catalogue.DATATYPES.where(membership);
    }

    public boolean holds(Datatype datatype) {
        return membership.test(datatype);
    }

    public Set<Datatype> membersAnd(Datatype... alsoTaken) {
        Set<Datatype> taken = new LinkedHashSet<>(members());
        taken.addAll(List.of(alsoTaken));
        return taken;
    }

    public static Optional<Typeset> named(String spelling) {
        String wanted = spelling.toLowerCase(Locale.ROOT);
        return Stream.of(values()).filter(typeset -> typeset.spelling.equals(wanted)).findFirst();
    }
}
