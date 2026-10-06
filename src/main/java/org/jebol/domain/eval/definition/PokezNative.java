package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Set;

public class PokezNative extends DefaultNative {

    private static final String THE_ACTION_IT_WRAPS = "poke";

    @Override
    public String name() {
        return "pokez";
    }

    @Override
    public List<Parameter> parameters() {
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
                        WordValue.of(THE_ACTION_IT_WRAPS, Datatype.SET_WORD),
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
