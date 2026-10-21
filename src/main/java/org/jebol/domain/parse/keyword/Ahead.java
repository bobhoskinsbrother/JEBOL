package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.Value;

import java.util.List;

final class Ahead extends SameForBlockAndString {

    Ahead(String spelling) {
        super(spelling, 0, true, WhenNothingFollowsIt.DOES_NOT_MATCH);
    }

    @Override
    int apply(ParseWalk walk, List<Value> rules, int at) {
        return walk.matchWithoutConsuming(rules, at);
    }
}
