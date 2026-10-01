package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.UnsetValue;

import java.util.List;
import java.util.Set;

public class AnyNative extends DefaultNative {

    @Override
    public String name() {
        return "any";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("block", Set.of(Datatype.BLOCK)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            BlockValue at = (BlockValue) arguments.getFirst();
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
