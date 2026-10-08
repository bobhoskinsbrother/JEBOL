package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class PokezNative extends DefaultNative {

    private static final String THE_ACTION_IT_WRAPS = "poke";

    @Override
    public String nativeName() {
        return "pokez";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("series", Typeset.SERIES.membersAnd(Datatype.BITSET, Datatype.TUPLE)),
                Parameter.required("index", Set.of(Datatype.INTEGER)),
                Parameter.required("value"));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value series = arguments.getFirst();
            if (series instanceof TupleValue unchangeable) {
                throw Raised.of(EvaluationFailure.CANNOT_USE,
                        SetWordValue.of(THE_ACTION_IT_WRAPS),
                        DatatypeValue.of(unchangeable.datatype()));
            }
            long index = ((IntegerValue) arguments.get(1)).magnitude();
            boolean countsFromOne = index >= 0 && !(series instanceof BitsetValue);
            return evaluator.applyFunction(theActionItWraps(context),
                    List.of(series, IntegerValue.of(countsFromOne ? index + 1 : index), arguments.get(2)));
        };
    }

    private Value theActionItWraps(Context context) {
        if (!context.knows(THE_ACTION_IT_WRAPS)) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, THE_ACTION_IT_WRAPS);
        }
        return context.slotFor(THE_ACTION_IT_WRAPS).value();
    }
}
