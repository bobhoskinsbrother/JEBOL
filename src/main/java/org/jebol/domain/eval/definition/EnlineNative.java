package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;

import java.util.List;

public class EnlineNative extends DefaultNative {

    @Override
    public String name() {
        return "enline";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("series", Typeset.ANY_STRING.membersAnd(Datatype.BLOCK)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case StringValue text -> text.withOneLineFeedPerEnding();
            case Value lines -> throw Raised.of(EvaluationFailure.NOT_DONE);
        };
    }
}
