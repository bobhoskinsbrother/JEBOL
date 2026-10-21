package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class LastNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "last";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("value",
                TypesetValue.SERIES.membersAnd(TupleValue.TYPE, GobValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case TupleValue parts -> IntegerValue.of(parts.readOctetAt(parts.segmentCount()));
            case RebolSeries series -> series.pick(series.lengthFromHere());
            case Value anythingElse -> refuseDatatype(anythingElse);
        };
    }
}
