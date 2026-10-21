package org.jebol.domain.parse.keyword;

abstract class DeclaredKeyword implements ParseKeyword {

    private final String spelling;
    private final int slotsBeforeRule;
    private final boolean ownsRuleAfterIt;
    private final WhenNothingFollowsIt whenNothingFollowsIt;

    DeclaredKeyword(String spelling, int slotsBeforeRule, boolean ownsRuleAfterIt, WhenNothingFollowsIt whenNothingFollowsIt) {
        this.spelling = spelling;
        this.slotsBeforeRule = slotsBeforeRule;
        this.ownsRuleAfterIt = ownsRuleAfterIt;
        this.whenNothingFollowsIt = whenNothingFollowsIt;
    }

    @Override
    public final String spelling() {
        return spelling;
    }

    @Override
    public final int slotsBeforeRule() {
        return slotsBeforeRule;
    }

    @Override
    public final boolean ownsRuleAfterIt() {
        return ownsRuleAfterIt;
    }

    @Override
    public final WhenNothingFollowsIt describeWhenNothingFollows() {
        return whenNothingFollowsIt;
    }
}
