package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;

import java.util.List;

public class LastNative extends DefaultNative {

    @Override
    public String name() {
        return "last";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("value",
                Typeset.SERIES.membersAnd(Datatype.TUPLE, Datatype.GOB)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case TupleValue parts -> IntegerValue.of(parts.octetAt(parts.segmentCount()));
            case RebolSeries series -> series.picked(series.lengthFromHere());
            case Value anythingElse -> refuseTheDatatype(anythingElse);
        };
    }
}
