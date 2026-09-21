package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.Value;

import java.util.List;

final class Remove extends SameForABlockAndAString {

    Remove() {
        super("remove", 0, true, WhenNothingFollowsIt.DOES_NOT_MATCH);
    }

    @Override
    int apply(ParseWalk walk, List<Value> rules, int at) {
        Integer markOffset = walk.sameStorageOffset(rules.get(at + 1));
        return markOffset != null
                ? takeOutWhatReachesBackToTheMark(walk, markOffset)
                : takeOutWhatTheRuleMatches(walk, rules, at);
    }

    private static int takeOutWhatReachesBackToTheMark(ParseWalk walk, int markOffset) {
        int begin = Math.min(walk.position(), markOffset);
        walk.removeBetween(begin, Math.abs(walk.position() - markOffset));
        walk.moveTo(begin);
        return 2;
    }

    private static int takeOutWhatTheRuleMatches(
            ParseWalk walk, List<Value> rules, int at) {

        int span = walk.ruleSpan(rules, at + 1);
        int before = walk.position();
        if (walk.matchOne(rules, at + 1) == ParseWalk.NO_MATCH) {
            walk.moveTo(before);
            return ParseWalk.NO_MATCH;
        }
        walk.removeBetween(before, walk.position() - before);
        walk.moveTo(before);
        return 1 + span;
    }
}
