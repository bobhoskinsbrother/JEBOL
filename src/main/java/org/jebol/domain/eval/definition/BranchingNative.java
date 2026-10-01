package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Value;

import java.util.Set;

public abstract class BranchingNative extends DefaultNative {

    @Override
    public Set<String> refinements() {
        return Set.of("only");
    }

    protected Value branchTaken(
            Value branch, Evaluator evaluator, Context context, Set<String> refinements) {

        return branch instanceof BlockValue block
                && block.datatype() == Datatype.BLOCK
                && !refinements.contains("only")
                ? evaluator.evaluateOrRaise(block, context)
                : branch;
    }
}
