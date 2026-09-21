package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.Value;

import java.util.List;

final class Then extends SameForABlockAndAString {

    Then() {
        super("then", 0, true, WhenNothingFollowsIt.DOES_NOT_MATCH);
    }

    @Override
    int apply(ParseWalk walk, List<Value> rules, int at) {
        return walk.giveUpTheNextAlternative(rules, at);
    }
}
