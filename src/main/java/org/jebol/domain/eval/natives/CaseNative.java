package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class CaseNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "case";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("choices", Set.of(BlockValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("all");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            AnyBlockValue at = (AnyBlockValue) arguments.getFirst();
            boolean runsThemAll = refinements.contains("all");
            while (!at.atTail()) {
                Evaluator.Step condition = evaluator.evaluateNextOrRaise(at, context);
                AnyBlockValue afterCondition = at.atIndex(condition.nextIndex());
                if (!condition.value().isTruthy()) {
                    at = pastTheBranch(afterCondition);
                    continue;
                }
                if (afterCondition.atTail()) {
                    return LogicValue.of(true);
                }
                Evaluator.Step branch = evaluator.evaluateNextOrRaise(afterCondition, context);
                Value taken = branch.value() instanceof AnyBlockValue block
                        ? evaluator.evaluateOrRaise(block, context)
                        : branch.value();
                at = afterCondition.atIndex(branch.nextIndex());
                if (!runsThemAll || at.atTail()) {
                    return taken;
                }
            }
            return NoneValue.none();
        };
    }

    private AnyBlockValue pastTheBranch(AnyBlockValue afterCondition) {
        return afterCondition.atTail()
                ? afterCondition
                : afterCondition.atIndex(afterCondition.index() + 1);
    }
}
