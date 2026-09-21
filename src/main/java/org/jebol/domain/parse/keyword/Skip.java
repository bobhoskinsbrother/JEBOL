package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.Value;

import java.util.List;

final class Skip extends SameForABlockAndAString {

    Skip() {
        super("skip", 0, false, WhenNothingFollowsIt.DOES_NOT_MATCH);
    }

    @Override
    int apply(ParseWalk walk, List<Value> rules, int at) {
        return walk.advanceOne() ? 1 : ParseWalk.NO_MATCH;
    }
}
