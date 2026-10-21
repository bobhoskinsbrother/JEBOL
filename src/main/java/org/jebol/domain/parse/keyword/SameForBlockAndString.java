package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.Value;

import java.util.List;

abstract class SameForBlockAndString extends DeclaredKeyword {

    SameForBlockAndString(String spelling, int slotsBeforeRule, boolean ownsRuleAfterIt, WhenNothingFollowsIt whenNothingFollowsIt) {
        super(spelling, slotsBeforeRule, ownsRuleAfterIt, whenNothingFollowsIt);
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
