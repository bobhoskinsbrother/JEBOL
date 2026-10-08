package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.AnyWordValue;

import java.util.List;

public class ForAllNative extends SteppingThroughNative {

    private static final int ONE_AT_A_TIME = 1;

    @Override
    public String nativeName() {
        return "forall";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.softQuoted("word"), Parameter.required("body", A_BLOCK));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> walkBySteps(
                evaluator,
                (AnyWordValue) arguments.get(0),
                ONE_AT_A_TIME,
                (BlockValue) arguments.get(1));
    }
}
