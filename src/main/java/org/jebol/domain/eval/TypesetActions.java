package org.jebol.domain.eval;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * What a set of datatypes does when an action is performed on it, which is
 * what {@code REBTYPE(Typeset)} answers in {@code t-typeset.c}.
 *
 * <p>A typeset reads three spellings of the same idea and says so here rather
 * than at each call: a datatype value, another typeset, or a word -- and a
 * word may name one datatype ({@code integer!}) or a whole family
 * ({@code number!}), with or without its mark.
 */
public final class TypesetActions {

    private final TypesetValue members;

    public TypesetActions(TypesetValue members) {
        this.members = members;
    }

    /** COMPLEMENT: every datatype this one does not hold. */
    public TypesetValue complemented() {
        Set<Datatype> rest = EnumSet.allOf(Datatype.class);
        rest.removeAll(members.members());
        return TypesetValue.of(Set.copyOf(rest));
    }

    /** FIND, which asks whether one datatype is among the members. */
    public boolean holds(Value asked) {
        return asked instanceof DatatypeValue wanted
                && members.holds(wanted.represents());
    }

    /**
     * INTERSECT, UNION, EXCLUDE and DIFFERENCE, walked datatype by datatype
     * because a typeset is a membership question rather than a series and
     * there is nothing to keep in order.
     */
    public TypesetValue combinedWith(TypesetValue theirs, Combining.Sets how) {
        Set<Datatype> mine = members.members();
        Set<Datatype> yours = theirs.members();
        Set<Datatype> kept = EnumSet.noneOf(Datatype.class);
        for (Datatype each : Datatype.values()) {
            boolean inMine = mine.contains(each);
            boolean inYours = yours.contains(each);
            boolean wanted = switch (how) {
                case UNION -> inMine || inYours;
                case INTERSECT -> inMine && inYours;
                case DIFFERENCE -> inMine ^ inYours;
                case EXCLUDE -> inMine && !inYours;
            };
            if (wanted) {
                kept.add(each);
            }
        }
        return TypesetValue.of(Set.copyOf(kept));
    }

    /** MAKE and TO from a block, which names its members one spelling at a time. */
    public static Set<Datatype> datatypesNamedIn(BlockValue spec) {
        Set<Datatype> found = EnumSet.noneOf(Datatype.class);
        for (Value item : spec.remaining()) {
            if (!addTheTypesNamedBy(item, found)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(item));
            }
        }
        return found;
    }

    /**
     * Whichever datatypes one item names, added to what is already found.
     *
     * <p>Answers false rather than raising when the item names none, because
     * the two callers differ: building a typeset refuses the item, and reading
     * a function spec walks past it.
     */
    public static boolean addTheTypesNamedBy(Value item, Set<Datatype> found) {
        if (item instanceof DatatypeValue datatype) {
            found.add(datatype.represents());
            return true;
        }
        if (item instanceof TypesetValue typeset) {
            found.addAll(typeset.members());
            return true;
        }
        if (!(item instanceof WordValue word)) {
            return false;
        }
        String spelling = word.spelling();
        Optional<Datatype> one = Datatype.named(spelling);
        one.ifPresent(found::add);
        Optional<Typeset> family = Typeset.named(spelling.endsWith("!")
                ? spelling.substring(0, spelling.length() - 1)
                : spelling);
        family.ifPresent(whole -> found.addAll(whole.members()));
        return one.isPresent() || family.isPresent();
    }
}
