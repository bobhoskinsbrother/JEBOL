package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.Value;

import java.util.List;

abstract class SameForABlockAndAString extends DeclaredKeyword {

    SameForABlockAndAString(String spelling, int slotsBeforeTheRule, boolean ownsTheRuleAfterIt, WhenNothingFollowsIt whenNothingFollowsIt) {
        super(spelling, slotsBeforeTheRule, ownsTheRuleAfterIt, whenNothingFollowsIt);
    }

    abstract int apply(ParseWalk walk, List<Value> rules, int at);

    @Override
    public final int applyToBlock(ParseWalk walk, List<Value> rules, int at) {
        return apply(walk, rules, at);
    }

    @Override
    public final int applyToString(ParseWalk walk, List<Value> rules, int at) {
        return apply(walk, rules, at);
    }
}
