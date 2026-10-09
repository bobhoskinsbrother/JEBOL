package org.jebol.domain.value;

import org.jebol.domain.value.sets.MembersKept;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class TypesetValue implements Value {

    public static final Datatype TYPE = new Datatype("typeset") {

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            return switch (from) {
                case TypesetValue already -> already;
                case BlockValue block -> TypesetValue.of(TypesetActions.datatypesNamedIn(block));
                default -> throw refusing(from);
            };
        }
    };

    public static final TypesetValue ANY_TYPE = new TypesetValue("any-type",
            datatype -> datatype != EndValue.TYPE);

    public static final TypesetValue NUMBER = new TypesetValue("number",
            datatype -> datatype instanceof NumberDatatype);

    public static final TypesetValue SCALAR = new TypesetValue("scalar",
            datatype -> datatype instanceof ScalarDatatype);

    public static final TypesetValue SERIES = new TypesetValue("series",
            datatype -> datatype instanceof SeriesDatatype);

    public static final TypesetValue ANY_STRING = new TypesetValue("any-string",
            datatype -> datatype instanceof AnyStringValue.AnyStringDatatype);

    public static final TypesetValue ANY_BLOCK = new TypesetValue("any-block",
            datatype -> datatype instanceof AnyBlockValue.AnyBlockDatatype);

    public static final TypesetValue ANY_PATH = new TypesetValue("any-path",
            datatype -> datatype instanceof AnyPathValue.AnyPathDatatype);

    public static final TypesetValue ANY_WORD = new TypesetValue("any-word",
            datatype -> datatype instanceof AnyWordValue.AnyWordDatatype);

    public static final TypesetValue ANY_FUNCTION = new TypesetValue("any-function",
            datatype -> datatype instanceof AnyFunctionDatatype);

    public static final TypesetValue ANY_OBJECT = new TypesetValue("any-object",
            datatype -> datatype instanceof AnyObjectDatatype);

    public static final TypesetValue IMMEDIATE = new TypesetValue("immediate",
            datatype -> TypesetValue.SCALAR.holds(datatype)
                    || TypesetValue.ANY_WORD.holds(datatype)
                    || datatype == NoneValue.TYPE
                    || datatype == LogicValue.TYPE
                    || datatype == Datatype.TYPE
                    || datatype == TypesetValue.TYPE
                    || datatype == EventValue.TYPE);

    public static final TypesetValue COPYABLE = new TypesetValue("copyable",
            datatype -> TypesetValue.SERIES.holds(datatype)
                    || TypesetValue.ANY_FUNCTION.holds(datatype)
                    || datatype == PortValue.TYPE
                    || datatype == MapValue.TYPE
                    || datatype == ObjectValue.TYPE
                    || datatype == BitsetValue.TYPE
                    || datatype == ErrorValue.TYPE);

    public static final TypesetValue INTERNAL = new TypesetValue("internal",
            datatype -> datatype == EndValue.TYPE
                    || datatype == UnsetValue.TYPE
                    || datatype == FrameValue.TYPE
                    || datatype == HandleValue.TYPE);

    private final Optional<String> spelling;
    private final Predicate<Datatype> membership;

    private TypesetValue(String spelling, Predicate<Datatype> membership) {
        this.spelling = Optional.of(spelling);
        this.membership = membership;
    }

    private TypesetValue(Set<Datatype> members) {
        this.spelling = Optional.empty();
        this.membership = Set.copyOf(members)::contains;
    }

    public static TypesetValue of(Set<Datatype> members) {
        return new TypesetValue(members);
    }

    public Optional<String> spelling() {
        return spelling;
    }

    public boolean holds(Datatype datatype) {
        return membership.test(datatype);
    }

    public Set<Datatype> members() {
        return Catalogue.DATATYPES.where(membership);
    }

    public Set<Datatype> membersAnd(Datatype... alsoTaken) {
        Set<Datatype> taken = new LinkedHashSet<>(members());
        taken.addAll(List.of(alsoTaken));
        return taken;
    }

    @Override
    public boolean atTail() {
        return members().isEmpty();
    }

    @Override
    public Value asASetWith(Value other, MembersKept keeping, boolean mindingCase) {
        if (!(other instanceof TypesetValue theirs)) {
            throw Raised.cannotUse(this, "a set operation");
        }
        return new TypesetActions(this).combinedWith(theirs, keeping.how());
    }

    @Override
    public Value bitwise(Value right, BitwiseOperation operation) {
        return TypesetValue.of(membersKeptAgainst(someDatatypesFrom(right), operation));
    }

    private Set<Datatype> someDatatypesFrom(Value right) {
        return switch (right) {
            case TypesetValue set -> set.members();
            case Datatype one -> Set.of(one);
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG, right);
        };
    }

    private Set<Datatype> membersKeptAgainst(
            Set<Datatype> theirs, BitwiseOperation operation) {

        Set<Datatype> ours = members();
        Set<Datatype> named = new LinkedHashSet<>(ours);
        named.addAll(theirs);
        Set<Datatype> kept = new LinkedHashSet<>();
        for (Datatype candidate : named) {
            if (isKept(ours.contains(candidate), theirs.contains(candidate), operation)) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private boolean isKept(boolean inOurs, boolean inTheirs, BitwiseOperation operation) {
        return operation.onWholeElements(inOurs ? 1 : 0, inTheirs ? 1 : 0) != 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TypesetValue set && members().equals(set.members());
    }

    @Override
    public int hashCode() {
        return members().hashCode();
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    public String toString() {
        return "make typeset! [" + spelledOut() + "]";
    }

    private String spelledOut() {
        return members().stream()
                .map(Datatype::literalSpelling)
                .collect(Collectors.joining(" "));
    }
}
