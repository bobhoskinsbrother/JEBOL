package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.ParenValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;

final class If extends SameForBlockAndString {

    If() {
        super("if", 0, true, WhenNothingFollowsIt.RAISES_PARSE_END_AT_END);
    }

    @Override
    int apply(ParseWalk walk, List<Value> rules, int at) {
        if (!(rules.get(at + 1) instanceof ParenValue paren)) {
            throw Raised.of(EvaluationFailure.PARSE_RULE, rules.get(at + 1));
        }
        return walk.evaluateParen(paren).isTruthy() ? 2 : ParseWalk.NO_MATCH;
    }
}
