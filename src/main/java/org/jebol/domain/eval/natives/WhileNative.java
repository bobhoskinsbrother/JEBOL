package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.AnyBlockValue;
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
        return List.of(Parameter.of("condition", BLOCK),
                Parameter.of("body", BLOCK));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            AnyBlockValue condition = (AnyBlockValue) arguments.get(0);
            AnyBlockValue body = (AnyBlockValue) arguments.get(1);
            return runLoop(() -> {
                Value last = NoneValue.none();
                while (isTrueForLoop(evaluator.evaluateOrRaise(
                        condition, evaluator.systemContext()))) {
                    last = runOneRoundCatchingContinue(evaluator, body, evaluator.systemContext());
                }
                return last;
            });
        };
    }
}
