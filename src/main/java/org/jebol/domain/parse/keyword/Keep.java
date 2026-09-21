package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;

final class Keep extends DeclaredKeyword {

    private static final String ONE_AT_A_TIME = "pick";
    private static final String THE_WHOLE_SLICE = "copy";

    Keep() {
        super("keep", 0, true, WhenNothingFollowsIt.RAISES_PARSE_END);
    }

    @Override
    public int applyToBlock(ParseWalk walk, List<Value> rules, int at) {
        refuseAKeepWithNoCollectAroundIt(walk);
        Value kept = rules.get(at + 1);
        if (kept instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            walk.keep(walk.evaluateParen(paren));
            return 2;
        }
        if (saysOneAtATime(kept)) {
            return spreadWhatTheRuleMatched(walk, rules, at);
        }
        if (isTheWord(kept, THE_WHOLE_SLICE)) {
            return keepTheCapture(walk, rules, at + 1);
        }
        return keepWhatTheRuleMatched(walk, rules, at);
    }

    @Override
    public int applyToString(ParseWalk walk, List<Value> rules, int at) {
        refuseAKeepWithNoCollectAroundIt(walk);
        Value kept = rules.get(at + 1);
        if (kept instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            walk.keep(walk.evaluateParen(paren));
            return 2;
        }
        if (saysOneAtATime(kept)) {
            return spreadWhatTheRuleMatched(walk, rules, at);
        }
        boolean wholeSlice = isTheWord(kept, THE_WHOLE_SLICE);
        int before = walk.position();
        if (walk.matchOne(rules, at + 1) == ParseWalk.NO_MATCH) {
            walk.moveTo(before);
            return ParseWalk.NO_MATCH;
        }
        if (walk.position() > before) {
            walk.keep(wholeSlice
                    ? walk.textSliceBetween(before, walk.position())
                    : walk.whatMatchedBetween(before, walk.position()).orElseThrow());
        }
        return 1 + walk.ruleSpan(rules, at + 1);
    }

    private static int keepWhatTheRuleMatched(
            ParseWalk walk, List<Value> rules, int at) {

        int before = walk.position();
        if (walk.matchOne(rules, at + 1) == ParseWalk.NO_MATCH) {
            walk.moveTo(before);
            return ParseWalk.NO_MATCH;
        }
        walk.whatMatchedBetween(before, walk.position()).ifPresent(walk::keep);
        return 1 + walk.ruleSpan(rules, at + 1);
    }

    private static int spreadWhatTheRuleMatched(
            ParseWalk walk, List<Value> rules, int at) {

        if (at + 2 < rules.size()
                && rules.get(at + 2) instanceof BlockValue expression
                && expression.datatype() == Datatype.PAREN) {
            walk.keep(walk.evaluateParen(expression));
            return 3;
        }
        int ruleAt = at + 2;
        int before = walk.position();
        if (ruleAt >= rules.size()
                || walk.matchOne(rules, ruleAt) == ParseWalk.NO_MATCH) {
            walk.moveTo(before);
            return ParseWalk.NO_MATCH;
        }
        walk.keepEachBetween(before, walk.position());
        return 2 + walk.ruleSpan(rules, ruleAt);
    }

    private static int keepTheCapture(ParseWalk walk, List<Value> rules, int at) {
        if (at + 2 >= rules.size()) {
            return ParseWalk.NO_MATCH;
        }
        int before = walk.position();
        if (walk.matchOne(rules, at + 2) == ParseWalk.NO_MATCH) {
            walk.moveTo(before);
            return ParseWalk.NO_MATCH;
        }
        Value captured = walk.sliceBetween(before, walk.position());
        if (rules.get(at + 1) instanceof WordValue name) {
            walk.assign(name, captured);
        }
        walk.keep(captured);
        return 3 + walk.ruleSpan(rules, at + 2);
    }

    private static void refuseAKeepWithNoCollectAroundIt(ParseWalk walk) {
        if (walk.noCollectionIsOpen()) {
            throw Raised.of(EvaluationFailure.PARSE_NO_COLLECT,
                    "keep has no collect around it");
        }
    }

    private static boolean saysOneAtATime(Value kept) {
        return isTheWord(kept, ONE_AT_A_TIME);
    }

    private static boolean isTheWord(Value kept, String spelling) {
        return kept instanceof WordValue word
                && word.datatype() == Datatype.WORD
                && word.canonical().equals(spelling);
    }
}
