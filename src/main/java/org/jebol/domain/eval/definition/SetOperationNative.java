package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Combining;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;
import org.jebol.domain.value.sets.SetOperation;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class SetOperationNative extends DefaultNative {

    private static final int EVERY_MEMBER_ON_ITS_OWN = 1;

    private static final Set<Datatype> A_SET = Set.of(
            Datatype.BITSET, Datatype.TYPESET, Datatype.STRING, Datatype.MAP, Datatype.BLOCK);

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("first", A_SET),
                Parameter.required("second", A_SET),
                Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("case", "skip");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> Combining.sets(
                arguments.getFirst(), arguments.get(1),
                SetOperation.named(nativeName()).orElseThrow(),
                refinements.contains("case"),
                recordWidthOf(argumentOf("skip", 0, arguments, refinements)));
    }

    private int recordWidthOf(Optional<Value> width) {
        if (width.isEmpty() || !(width.get() instanceof IntegerValue wanted)) {
            return EVERY_MEMBER_ON_ITS_OWN;
        }
        if (wanted.magnitude() < 1) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, Molder.mold(wanted));
        }
        return (int) wanted.magnitude();
    }
}
