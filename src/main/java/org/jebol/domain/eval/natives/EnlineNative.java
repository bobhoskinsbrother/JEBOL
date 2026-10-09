package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class EnlineNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "enline";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("series", TypesetValue.ANY_STRING.membersAnd(BlockValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case AnyStringValue text -> text.withOneLineFeedPerEnding();
            case Value lines -> throw Raised.of(EvaluationFailure.NOT_DONE);
        };
    }
}
