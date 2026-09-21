package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;

final class Change extends DeclaredKeyword {

    private static final String WHOLE = "only";

    Change() {
        super("change", 1, true, WhenNothingFollowsIt.DOES_NOT_MATCH);
    }

    @Override
    public int applyToBlock(ParseWalk walk, List<Value> rules, int at) {
        refuseTheModifierBeforeTheRule(rules.get(at + 1));
        Integer markOffset = walk.sameStorageOffset(rules.get(at + 1));
        if (markOffset != null) {
            if (at + 2 >= rules.size()) {
                throw Raised.of(EvaluationFailure.PARSE_END,
                        "change needs a value to put where the match was");
            }
            return putTheBlockIn(walk, rules, at, at + 2,
                    Math.min(walk.position(), markOffset),
                    Math.abs(walk.position() - markOffset));
        }
        int replacementAt = at + 1 + walk.ruleSpan(rules, at + 1);
        if (replacementAt >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    "change needs a value to put where the match was");
        }
        int before = walk.position();
        if (walk.matchOne(rules, at + 1) == ParseWalk.NO_MATCH) {
            walk.moveTo(before);
            return ParseWalk.NO_MATCH;
        }
        return putTheBlockIn(walk, rules, at, replacementAt,
                before, walk.position() - before);
    }

    @Override
    public int applyToString(ParseWalk walk, List<Value> rules, int at) {
        Integer markOffset = walk.sameStorageOffset(rules.get(at + 1));
        if (markOffset != null) {
            if (at + 2 >= rules.size()) {
                return ParseWalk.NO_MATCH;
            }
            putTheTextIn(walk, rules.get(at + 2),
                    Math.min(walk.position(), markOffset),
                    Math.abs(walk.position() - markOffset));
            return 3;
        }
        int span = walk.ruleSpan(rules, at + 1);
        int replacementAt = at + 1 + span;
        if (replacementAt >= rules.size()) {
            return ParseWalk.NO_MATCH;
        }
        int before = walk.position();
        if (walk.matchOne(rules, at + 1) == ParseWalk.NO_MATCH) {
            walk.moveTo(before);
            return ParseWalk.NO_MATCH;
        }
        putTheTextIn(walk, rules.get(replacementAt),
                before, walk.position() - before);
        return 1 + span + 1;
    }

    private static int putTheBlockIn(ParseWalk walk, List<Value> rules, int at,
            int replacementSlot, int begin, int howMany) {

        boolean wholeBlock = saysToPutTheBlockInWhole(rules, replacementSlot);
        int lastRuleAt = wholeBlock ? replacementSlot + 1 : replacementSlot;
        Value replacement = walk.theValueToInsert(rules.get(lastRuleAt));
        List<Value> putting = !wholeBlock && replacement instanceof BlockValue spread
                && spread.datatype() == Datatype.BLOCK
                ? spread.remaining()
                : List.of(replacement);
        walk.removeBetween(begin, howMany);
        walk.putItemsIntoTheBlockAt(begin, putting);
        walk.moveTo(begin + putting.size());
        return lastRuleAt + 1 - at;
    }

    private static void putTheTextIn(
            ParseWalk walk, Value replacement, int begin, int howMany) {

        walk.removeBetween(begin, howMany);
        int laidIn = walk.putValueIntoTheTextAt(
                begin, walk.replacementFor(replacement));
        walk.moveTo(begin + laidIn);
    }

    private static boolean saysToPutTheBlockInWhole(List<Value> rules, int slot) {
        return rules.get(slot) instanceof WordValue modifier
                && modifier.datatype() == Datatype.WORD
                && modifier.canonical().equals(WHOLE)
                && slot + 1 < rules.size();
    }

    private static void refuseTheModifierBeforeTheRule(Value rule) {
        if (rule instanceof WordValue misplaced
                && misplaced.datatype() == Datatype.WORD
                && misplaced.canonical().equals(WHOLE)) {
            throw Raised.of(EvaluationFailure.PARSE_RULE,
                    "only says how to put the replacement in, so it goes "
                            + "before the replacement and not before the rule");
        }
    }
}
