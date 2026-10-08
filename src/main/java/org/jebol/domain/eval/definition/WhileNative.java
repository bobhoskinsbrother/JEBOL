package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;

public class WhileNative extends LoopingNative {

    @Override
    public String nativeName() {
        return "while";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("condition", A_BLOCK),
                Parameter.required("body", A_BLOCK));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            BlockValue condition = (BlockValue) arguments.get(0);
            BlockValue body = (BlockValue) arguments.get(1);
            return answerOfTheLoop(() -> {
                Value last = NoneValue.none();
                while (theTruthInWhatALoopTests(evaluator.evaluateOrRaise(
                        condition, evaluator.systemContext()))) {
                    last = oneRoundCatchingContinue(evaluator, body, evaluator.systemContext());
                }
                return last;
            });
        };
    }
}
