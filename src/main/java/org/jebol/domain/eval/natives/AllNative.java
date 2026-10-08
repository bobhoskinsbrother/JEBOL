package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class AllNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "all";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("block", Set.of(BlockValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            AnyBlockValue at = (AnyBlockValue) arguments.getFirst();
            Value last = UnsetValue.unset();
            while (!at.atTail()) {
                Evaluator.Step step = evaluator.evaluateNextOrRaise(at, context);
                at = at.atIndex(step.nextIndex());
                if (step.value() instanceof UnsetValue) {
                    continue;
                }
                if (!step.value().isTruthy()) {
                    return NoneValue.none();
                }
                last = step.value();
            }
            return last;
        };
    }
}
