package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;

final class Capture extends DeclaredKeyword {

    private final boolean wholeSlice;

    Capture(String spelling, boolean wholeSlice) {
        super(spelling, 1, true, WhenNothingFollowsIt.RAISES_PARSE_VARIABLE);
        this.wholeSlice = wholeSlice;
    }

    @Override
    public int applyToBlock(ParseWalk walk, List<Value> rules, int at) {
        WordValue word = walk.theWordToWriteInto(rules, at);
        if (at + 2 >= rules.size()) {
            return ParseWalk.NO_MATCH;
        }
        int startedAt = walk.position();
        int consumed = walk.matchOne(rules, at + 2);
        if (consumed == ParseWalk.NO_MATCH) {
            return ParseWalk.NO_MATCH;
        }
        walk.assign(word, wholeSlice
                ? walk.sliceBetween(startedAt, walk.position())
                : walk.firstItemBetween(startedAt, walk.position()));
        return 2 + consumed;
    }

    @Override
    public int applyToString(ParseWalk walk, List<Value> rules, int at) {
        WordValue target = walk.theWordToWriteInto(rules, at);
        if (at + 2 >= rules.size()) {
            return ParseWalk.NO_MATCH;
        }
        int ruleAt = theRuleAfterAnyMarks(walk, rules, at + 2);
        int before = walk.position();
        if (walk.matchOne(rules, ruleAt) == ParseWalk.NO_MATCH) {
            walk.moveTo(before);
            return ParseWalk.NO_MATCH;
        }
        walk.assign(target, wholeSlice
                ? walk.textSliceBetween(before, walk.position())
                : walk.firstCharacterMatchedFrom(before));
        return (ruleAt - at) + walk.ruleSpan(rules, ruleAt);
    }

    private static int theRuleAfterAnyMarks(
            ParseWalk walk, List<Value> rules, int from) {

        int ruleAt = from;
        while (rules.get(ruleAt) instanceof WordValue mark
                && mark.datatype() == Datatype.SET_WORD) {
            walk.assign(mark, walk.inputPositionedHere());
            ruleAt++;
            if (ruleAt >= rules.size()) {
                throw Raised.of(EvaluationFailure.PARSE_END,
                        "a capture has no rule after its marks to apply to");
            }
        }
        if (rules.get(ruleAt) instanceof WordValue asRule
                && asRule.datatype() == Datatype.GET_WORD) {
            throw Raised.of(EvaluationFailure.PARSE_RULE, asRule);
        }
        return ruleAt;
    }
}
