package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class AllNative extends DefaultNative {

    @Override
    public String name() {
        return "all";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("block", Set.of(Datatype.BLOCK)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            BlockValue at = (BlockValue) arguments.getFirst();
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
