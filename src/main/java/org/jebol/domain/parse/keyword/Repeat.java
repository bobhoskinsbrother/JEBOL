package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.Value;

import java.util.List;

final class Repeat extends SameForABlockAndAString {

    private final int leastNeeded;

    Repeat(String spelling, int leastNeeded) {
        super(spelling, 0, true, WhenNothingFollowsIt.DOES_NOT_MATCH);
        this.leastNeeded = leastNeeded;
    }

    @Override
    int apply(ParseWalk walk, List<Value> rules, int at) {
        return walk.repeat(rules, at, leastNeeded);
    }
}
