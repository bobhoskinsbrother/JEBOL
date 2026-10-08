package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class ForeverNative extends LoopingNative {

    @Override
    public String nativeName() {
        return "forever";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("body", A_BLOCK));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            AnyBlockValue body = (AnyBlockValue) arguments.getFirst();
            return answerOfTheLoop(() -> {
                while (true) {
                    oneRoundCatchingContinue(evaluator, body, context);
                }
            });
        };
    }
}
