package org.jebol.domain.value;

import org.jebol.domain.value.sets.SetOperation;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class TypesetActions {

    private final TypesetValue members;

    public TypesetActions(TypesetValue members) {
        this.members = members;
    }

    public TypesetValue complemented() {
        return TypesetValue.of(Catalogue.DATATYPES.where(each -> !members.holds(each)));
    }

    public boolean holds(Value asked) {
        return asked instanceof Datatype datatype && members.holds(datatype);
    }

    public TypesetValue combinedWith(TypesetValue theirs, SetOperation how) {
        return TypesetValue.of(Catalogue.DATATYPES.where(
                each -> how.holdsWhen(members.holds(each), theirs.holds(each))));
    }

    public static Set<Datatype> datatypesNamedIn(AnyBlockValue spec) {
        Set<Datatype> found = new LinkedHashSet<>();
        for (Value item : spec.remaining()) {
            if (!addTheTypesNamedBy(item, found)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(item));
            }
        }
        return found;
    }

    public static boolean addTheTypesNamedBy(Value item, Set<Datatype> found) {
        if (item instanceof Datatype datatype) {
            found.add(datatype);
            return true;
        }
        if (item instanceof TypesetValue typeset) {
            found.addAll(typeset.members());
            return true;
        }
        if (!(item instanceof AnyWordValue word)) {
            return false;
        }
        String spelling = word.spelling();
        Optional<Datatype> one = Catalogue.DATATYPES.named(spelling);
        one.ifPresent(found::add);
        Optional<Typeset> family = Typeset.named(spelling.endsWith("!")
                ? spelling.substring(0, spelling.length() - 1)
                : spelling);
        family.ifPresent(whole -> found.addAll(whole.members()));
        return one.isPresent() || family.isPresent();
    }
}
