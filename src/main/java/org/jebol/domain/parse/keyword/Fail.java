package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.Value;

import java.util.List;

final class Fail extends SameForABlockAndAString {

    Fail() {
        super("fail", 0, false, WhenNothingFollowsIt.DOES_NOT_MATCH);
    }

    @Override
    int apply(ParseWalk walk, List<Value> rules, int at) {
        return ParseWalk.NO_MATCH;
    }
}
