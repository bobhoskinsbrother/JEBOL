package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class CaseNative extends DefaultNative {

    @Override
    public String name() {
        return "case";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("choices", Set.of(Datatype.BLOCK)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("all");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            BlockValue at = (BlockValue) arguments.getFirst();
            boolean runsThemAll = refinements.contains("all");
            while (!at.atTail()) {
                Evaluator.Step condition = evaluator.evaluateNextOrRaise(at, context);
                BlockValue afterCondition = at.atIndex(condition.nextIndex());
                if (!condition.value().isTruthy()) {
                    at = pastTheBranch(afterCondition);
                    continue;
                }
                if (afterCondition.atTail()) {
                    return LogicValue.of(true);
                }
                Evaluator.Step branch = evaluator.evaluateNextOrRaise(afterCondition, context);
                Value taken = branch.value() instanceof BlockValue block
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

    private BlockValue pastTheBranch(BlockValue afterCondition) {
        return afterCondition.atTail()
                ? afterCondition
                : afterCondition.atIndex(afterCondition.index() + 1);
    }
}
