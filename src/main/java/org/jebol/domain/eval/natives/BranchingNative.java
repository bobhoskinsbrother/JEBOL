package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.value.*;

import java.util.Set;

public abstract class BranchingNative extends DefaultNative {

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("only");
    }

    protected Value branchTaken(
            Value branch, Evaluator evaluator, Context context, Set<String> refinements) {

        return branch instanceof BlockValue block
                && !refinements.contains("only")
                ? evaluator.evaluateOrRaise(block, context)
                : branch;
    }
}
