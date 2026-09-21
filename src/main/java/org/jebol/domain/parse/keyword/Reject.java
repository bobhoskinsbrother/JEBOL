package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.BlockEnded;
import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.Value;

import java.util.List;

final class Reject extends SameForABlockAndAString {

    Reject() {
        super("reject", 0, false, WhenNothingFollowsIt.DOES_NOT_MATCH);
    }

    @Override
    int apply(ParseWalk walk, List<Value> rules, int at) {
        throw BlockEnded.asAFailure();
    }
}
