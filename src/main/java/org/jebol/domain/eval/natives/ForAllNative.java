package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.AnyWordValue;

import java.util.List;

public class ForAllNative extends SteppingThroughNative {

    private static final int ONE_AT_TIME = 1;

    @Override
    public String nativeName() {
        return "forall";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.quoteSoftly("word"), Parameter.of("body", BLOCK));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> walkBySteps(
                evaluator,
                (AnyWordValue) arguments.get(0),
                ONE_AT_TIME,
                (AnyBlockValue) arguments.get(1));
    }
}
