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

public final class TypesetActions {

    private final TypesetValue members;

    public TypesetActions(TypesetValue members) {
        this.members = members;
    }

    public TypesetValue complemented() {
        Set<Datatype> rest = EnumSet.allOf(Datatype.class);
        rest.removeAll(members.members());
        return TypesetValue.of(Set.copyOf(rest));
    }

    public boolean holds(Value asked) {
        return asked instanceof DatatypeValue wanted
                && members.holds(wanted.represents());
    }

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

    public static Set<Datatype> datatypesNamedIn(BlockValue spec) {
        Set<Datatype> found = EnumSet.noneOf(Datatype.class);
        for (Value item : spec.remaining()) {
            if (!addTheTypesNamedBy(item, found)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(item));
            }
        }
        return found;
    }

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
