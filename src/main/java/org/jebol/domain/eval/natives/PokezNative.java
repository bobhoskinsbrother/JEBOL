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
                Parameter.of("series", TypesetValue.SERIES.membersAnd(BitsetValue.TYPE, TupleValue.TYPE)),
                Parameter.of("index", Set.of(IntegerValue.TYPE)),
                Parameter.of("value"));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value series = arguments.getFirst();
            if (series instanceof TupleValue unchangeable) {
                throw Raised.of(EvaluationFailure.CANNOT_USE,
                        SetWordValue.of(THE_ACTION_IT_WRAPS),
                        unchangeable.datatype());
            }
            long index = ((IntegerValue) arguments.get(1)).magnitude();
            boolean countsFromOne = index >= 0 && !(series instanceof BitsetValue);
            return evaluator.applyFunction(findTheActionItWraps(context),
                    List.of(series, IntegerValue.of(countsFromOne ? index + 1 : index), arguments.get(2)));
        };
    }

    private Value findTheActionItWraps(Context context) {
        if (!context.contains(THE_ACTION_IT_WRAPS)) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, THE_ACTION_IT_WRAPS);
        }
        return context.findSlotFor(THE_ACTION_IT_WRAPS).value();
    }
}
