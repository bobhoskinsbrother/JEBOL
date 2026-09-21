package org.jebol.domain.parse.keyword;

abstract class DeclaredKeyword implements ParseKeyword {

    private final String spelling;
    private final int slotsBeforeTheRule;
    private final boolean ownsTheRuleAfterIt;
    private final WhenNothingFollowsIt whenNothingFollowsIt;

    DeclaredKeyword(String spelling, int slotsBeforeTheRule, boolean ownsTheRuleAfterIt, WhenNothingFollowsIt whenNothingFollowsIt) {
        this.spelling = spelling;
        this.slotsBeforeTheRule = slotsBeforeTheRule;
        this.ownsTheRuleAfterIt = ownsTheRuleAfterIt;
        this.whenNothingFollowsIt = whenNothingFollowsIt;
    }

    @Override
    public final String spelling() {
        return spelling;
    }

    @Override
    public final int slotsBeforeTheRule() {
        return slotsBeforeTheRule;
    }

    @Override
    public final boolean ownsTheRuleAfterIt() {
        return ownsTheRuleAfterIt;
    }

    @Override
    public final WhenNothingFollowsIt whenNothingFollowsIt() {
        return whenNothingFollowsIt;
    }
}
