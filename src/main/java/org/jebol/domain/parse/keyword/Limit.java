package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.Value;

import java.util.List;

final class Limit extends SameForABlockAndAString {

    Limit() {
        super("limit", 0, false, WhenNothingFollowsIt.DOES_NOT_MATCH);
    }

    @Override
    int apply(ParseWalk walk, List<Value> rules, int at) {
        throw Raised.of(EvaluationFailure.NOT_DONE,
                "limit is a parse command reserved for future use");
    }
}
