package org.jebol.domain.eval.bit.bitType;

import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.eval.arithmetic.BitwiseOperation;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;

import java.util.LinkedHashSet;
import java.util.Set;

public class Typesets implements BitType {

    @Override
    public boolean shouldHandle(Value left, Value right) {
        return left instanceof TypesetValue;
    }

    @Override
    public Value combine(Value left, Value right, BitwiseOperation operation) {
        TypesetValue ours = (TypesetValue) left;
        return TypesetValue.of(
                membersKeptFrom(ours.members(), whatItCanTake(right), operation));
    }

    private Set<Datatype> whatItCanTake(Value right) {
        return switch (right) {
            case TypesetValue members -> members.members();
            case DatatypeValue(Datatype represents) -> Set.of(represents);
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG, right);
        };
    }

    private Set<Datatype> membersKeptFrom(
            Set<Datatype> ours, Set<Datatype> theirs, BitwiseOperation operation) {

        Set<Datatype> kept = new LinkedHashSet<>();
        for (Datatype candidate : everyDatatypeNamedBy(ours, theirs)) {
            if (isKept(ours.contains(candidate), theirs.contains(candidate), operation)) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private Set<Datatype> everyDatatypeNamedBy(Set<Datatype> ours, Set<Datatype> theirs) {
        Set<Datatype> named = new LinkedHashSet<>(ours);
        named.addAll(theirs);
        return named;
    }

    private boolean isKept(boolean inOurs, boolean inTheirs, BitwiseOperation operation) {
        return combinedBits(inOurs ? 1 : 0, inTheirs ? 1 : 0, operation) != 0;
    }

}
