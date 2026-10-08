package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;

public class UntilNative extends LoopingNative {

    @Override
    public String nativeName() {
        return "until";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("body", A_BLOCK));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            BlockValue body = (BlockValue) arguments.getFirst();
            return answerOfTheLoop(() -> {
                Value last;
                do {
                    last = oneRoundCatchingContinue(evaluator, body, evaluator.systemContext());
                } while (!theTruthInWhatALoopTests(last));
                return last;
            });
        };
    }
}
