package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class AsNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "as";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("type", aSeriesTypeAnd(Datatype.DATATYPE)),
                Parameter.required("spec", aSeriesTypeAnd()));
    }

    private Set<Datatype> aSeriesTypeAnd(Datatype... alsoTaken) {
        Set<Datatype> accepted = EnumSet.copyOf(Typeset.ANY_BLOCK.members());
        accepted.addAll(Typeset.ANY_STRING.members());
        accepted.addAll(List.of(alsoTaken));
        return Set.copyOf(accepted);
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Datatype wanted = arguments.getFirst() instanceof DatatypeValue(Datatype represents)
                    ? represents
                    : arguments.getFirst().datatype();
            return switch (arguments.get(1)) {
                case Value same when same.datatype() == wanted -> same;
                case BlockValue block when wanted.isAnyBlock() -> block.as(wanted);
                case AnyStringValue text when wanted.isAnyString() -> text.as(wanted);
                case Value other -> throw Raised.of(EvaluationFailure.NOT_SAME_CLASS,
                        DatatypeValue.of(other.datatype()), DatatypeValue.of(wanted));
            };
        };
    }
}
