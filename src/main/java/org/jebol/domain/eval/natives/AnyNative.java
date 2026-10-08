package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class AnyNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "any";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("block", Set.of(BlockValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            AnyBlockValue at = (AnyBlockValue) arguments.getFirst();
            while (!at.atTail()) {
                Evaluator.Step step = evaluator.evaluateNextOrRaise(at, context);
                at = at.atIndex(step.nextIndex());
                if (!(step.value() instanceof UnsetValue) && step.value().isTruthy()) {
                    return step.value();
                }
            }
            return NoneValue.none();
        };
    }
}
