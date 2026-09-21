package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Value;

import java.util.List;

final class If extends SameForABlockAndAString {

    If() {
        super("if", 0, true, WhenNothingFollowsIt.RAISES_PARSE_END);
    }

    @Override
    int apply(ParseWalk walk, List<Value> rules, int at) {
        if (!(rules.get(at + 1) instanceof BlockValue paren)
                || paren.datatype() != Datatype.PAREN) {
            return ParseWalk.NO_MATCH;
        }
        return walk.evaluateParen(paren).isTruthy() ? 2 : ParseWalk.NO_MATCH;
    }
}
