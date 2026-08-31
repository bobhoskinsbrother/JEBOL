package org.jebol.domain.value;

import java.util.EnumSet;
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
public record TypesetValue(Optional<Typeset> named, Set<Datatype> members) implements Value {

    public TypesetValue {
        members = members.isEmpty() ? Set.of() : EnumSet.copyOf(members);
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

    /**
     * Two sets are equal when they hold the same datatypes, named or not:
     * {@code (to typeset! [integer!]) = integer-only-set} must not depend
     * on how either side was built.
     */
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

    /**
     * The members, never the name.
     *
     * <p>{@code Mold_Typeset} walks the bits and writes what it finds, and it
     * has no way to know a set was asked for by name -- a typeset is its
     * members and nothing else. So {@code mold number!} is
     * {@code make typeset! [integer! decimal! percent!]} and not the word that
     * fetched it, which is what answering the name gave.
     */
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
