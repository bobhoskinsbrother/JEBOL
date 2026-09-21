package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.Value;

import java.util.List;

final class MindCase extends SameForABlockAndAString {

    private final boolean minding;

    MindCase(String spelling, boolean minding) {
        super(spelling, 0, false, WhenNothingFollowsIt.DOES_NOT_MATCH);
        this.minding = minding;
    }

    @Override
    int apply(ParseWalk walk, List<Value> rules, int at) {
        return walk.mindCaseFromHereOn(minding);
    }
}
