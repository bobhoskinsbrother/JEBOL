package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;

final class Change extends DeclaredKeyword {

    private static final String WHOLE = "only";

    Change() {
        super("change", 1, true, WhenNothingFollowsIt.DOES_NOT_MATCH);
    }

    @Override
    public boolean triesRuleFirst() {
        return true;
    }

    @Override
    public int applyToBlock(ParseWalk walk, List<Value> rules, int at) {
        refuseModifierBeforeRule(rules.get(at + 1));
        Integer markOffset = walk.sameStorageOffset(rules.get(at + 1));
        if (markOffset != null) {
            return putBlockIn(walk, rules, at, at + 2,
                    Math.min(walk.position(), markOffset),
                    Math.abs(walk.position() - markOffset));
        }
        int replacementAt = at + 1 + walk.ruleSpan(rules, at + 1);
        int before = walk.position();
        if (walk.matchOne(rules, at + 1) == ParseWalk.NO_MATCH) {
            walk.moveTo(before);
            return ParseWalk.NO_MATCH;
        }
        return putBlockIn(walk, rules, at, replacementAt,
                before, walk.position() - before);
    }

    @Override
    public int applyToString(ParseWalk walk, List<Value> rules, int at) {
        Integer markOffset = walk.sameStorageOffset(rules.get(at + 1));
        if (markOffset != null) {
            return putTextIn(walk, rules, at, at + 2,
                    Math.min(walk.position(), markOffset),
                    Math.abs(walk.position() - markOffset));
        }
        int replacementAt = at + 1 + walk.ruleSpan(rules, at + 1);
        int before = walk.position();
        if (walk.matchOne(rules, at + 1) == ParseWalk.NO_MATCH) {
            walk.moveTo(before);
            return ParseWalk.NO_MATCH;
        }
        return putTextIn(walk, rules, at, replacementAt,
                before, walk.position() - before);
    }

    private static int putBlockIn(ParseWalk walk, List<Value> rules, int at,
                                  int replacementSlot, int begin, int howMany) {

        ValueToPutIn written = ValueToPutIn.readFrom(rules, replacementSlot);
        Value replacement = walk.chooseValueToInsert(rules.get(written.slot()));
        List<Value> putting = !written.whole() && replacement instanceof BlockValue spread
                ? spread.remaining()
                : List.of(replacement);
        walk.removeBetween(begin, howMany);
        walk.putItemsIntoBlockAt(begin, putting);
        walk.moveTo(begin + putting.size());
        return written.slot() + 1 - at;
    }

    private static int putTextIn(ParseWalk walk, List<Value> rules, int at,
                                 int replacementSlot, int begin, int howMany) {

        ValueToPutIn written = ValueToPutIn.readFrom(rules, replacementSlot);
        Value replacement = walk.replacementFor(rules.get(written.slot()));
        walk.removeBetween(begin, howMany);
        int laidIn = walk.putValueIntoTextAt(begin, replacement);
        walk.moveTo(begin + laidIn);
        return written.slot() + 1 - at;
    }

    private static void refuseModifierBeforeRule(Value rule) {
        if (rule instanceof WordValue misplaced
                && misplaced.canonical().equals(WHOLE)) {
            throw Raised.of(EvaluationFailure.PARSE_RULE, misplaced);
        }
    }
}
