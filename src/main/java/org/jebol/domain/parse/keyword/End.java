package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.Value;

import java.util.List;

final class End extends SameForABlockAndAString {

    End() {
        super("end", 0, false, WhenNothingFollowsIt.DOES_NOT_MATCH);
    }

    @Override
    int apply(ParseWalk walk, List<Value> rules, int at) {
        return walk.atEnd() ? 1 : ParseWalk.NO_MATCH;
    }
}
