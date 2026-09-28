package org.jebol.domain.value;

import org.jebol.domain.value.sets.MembersKept;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A set of datatypes: {@code number!}, {@code series!} and their
 * siblings, and any set a script builds for itself.
 *
 * <p>Holds the members rather than the name, because a typeset need not
 * have one. {@code to typeset! [integer! string!]} is a perfectly good
 * typeset that R3's own base-defs.reb builds one of per generated
 * function, and it answers to no name at all. The named ones keep theirs
 * so that MOLD can print {@code series!} rather than the twenty datatypes
 * it stands for.
 */
public record TypesetValue(Optional<Typeset> family, Set<Datatype> members) implements Value {

    public TypesetValue {
        members = members.isEmpty() ? Set.of() : EnumSet.copyOf(members);
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
            case DatatypeValue(Datatype represents) -> Set.of(represents);
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG, right);
        };
    }

    private Set<Datatype> membersKeptAgainst(
            Set<Datatype> theirs, BitwiseOperation operation) {

        Set<Datatype> named = new LinkedHashSet<>(members);
        named.addAll(theirs);
        Set<Datatype> kept = new LinkedHashSet<>();
        for (Datatype candidate : named) {
            if (isKept(members.contains(candidate), theirs.contains(candidate), operation)) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private boolean isKept(boolean inOurs, boolean inTheirs, BitwiseOperation operation) {
        return operation.onWholeElements(inOurs ? 1 : 0, inTheirs ? 1 : 0) != 0;
    }

    public static TypesetValue of(Typeset represents) {
        return new TypesetValue(Optional.of(represents), represents.members());
    }

    /** A set with no name, built from whichever datatypes were asked for. */
    public static TypesetValue of(Set<Datatype> members) {
        return new TypesetValue(Optional.empty(), members);
    }

    /** Whether a value of this datatype belongs to the set. */
    public boolean holds(Datatype datatype) {
        return members.contains(datatype);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TypesetValue set && members.equals(set.members);
    }

    @Override
    public int hashCode() {
        return members.hashCode();
    }

    @Override
    public Datatype datatype() {
        return Datatype.TYPESET;
    }

    @Override
    public String toString() {
        return "make typeset! [" + spelledOut() + "]";
    }

    private String spelledOut() {
        StringBuilder written = new StringBuilder();
        for (Datatype datatype : Datatype.values()) {
            if (!members.contains(datatype)) {
                continue;
            }
            if (!written.isEmpty()) {
                written.append(' ');
            }
            written.append(datatype.literalSpelling());
        }
        return written.toString();
    }
}
