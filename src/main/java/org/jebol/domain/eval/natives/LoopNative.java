package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;

import java.util.List;

public class LoopNative extends LoopingNative {

    @Override
    public String nativeName() {
        return "loop";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("count", Typeset.NUMBER.members()),
                Parameter.required("body", A_BLOCK));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            long passes = ((IntegerValue) arguments.get(0)).magnitude();
            AnyBlockValue body = (AnyBlockValue) arguments.get(1);
            return answerOfTheLoop(() -> {
                Value last = NoneValue.none();
                for (long pass = 0; pass < passes; pass++) {
                    last = oneRoundCatchingContinue(evaluator, body, evaluator.systemContext());
                }
                return last;
            });
        };
    }
}
