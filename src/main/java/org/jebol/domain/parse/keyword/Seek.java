package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseTargets;
import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyDecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.GetWordValue;
import org.jebol.domain.value.SetWordValue;
import org.jebol.domain.value.WordValue;

import java.util.List;

final class Seek extends DeclaredKeyword {

    private final boolean past;

    Seek(String spelling, boolean past) {
        super(spelling, 0, true, WhenNothingFollowsIt.RAISES_PARSE_END);
        this.past = past;
    }

    @Override
    public int applyToBlock(ParseWalk walk, List<Value> rules, int at) {
        Value wanted = rules.get(at + 1);
        if (namesTail(wanted)) {
            walk.moveTo(walk.inputLength());
            return 2;
        }
        Value looked = walk.resolveWord(wanted);
        while (walk.position() <= walk.inputLength()) {
            int before = walk.position();
            if (walk.matchValue(looked)) {
                walk.moveTo(past ? walk.position() : before);
                return 2;
            }
            walk.moveTo(before);
            if (walk.atEnd()) {
                return ParseWalk.NO_MATCH;
            }
            walk.moveTo(before + 1);
        }
        return ParseWalk.NO_MATCH;
    }

    @Override
    public int applyToString(ParseWalk walk, List<Value> rules, int at) {
        Value wanted = rules.get(at + 1);
        if (namesTail(wanted)) {
            walk.moveTo(walk.inputLength());
            return 2;
        }
        if (wanted instanceof IntegerValue where) {
            return moveToPlaceItNames(walk, where);
        }
        requirePlaceOrSearchTerm(wanted);
        Value looked = walk.resolveWord(wanted);
        return looked instanceof AnyBlockValue || looked instanceof BitsetValue
                ? findFirstPlaceRuleMatches(walk, looked)
                : findFirstPlaceTextAppears(walk, looked);
    }

    private static boolean namesTail(Value wanted) {
        return wanted instanceof AnyWordValue word && word.canonical().equals("end");
    }

    private int moveToPlaceItNames(ParseWalk walk, IntegerValue where) {
        long asked = where.magnitude() - (past ? 0 : 1);
        if (asked < 0 || asked > walk.inputLength()) {
            return ParseWalk.NO_MATCH;
        }
        walk.moveTo((int) asked);
        return 2;
    }

    private static void requirePlaceOrSearchTerm(Value wanted) {
        boolean refused = wanted instanceof AnyDecimalValue
                || wanted instanceof GetWordValue
                || wanted instanceof SetWordValue
                || (wanted instanceof WordValue keyword
                        && ParseTargets.WORDS_DIALECT_RESERVES
                                .contains(keyword.canonical()));
        if (refused) {
            throw Raised.of(EvaluationFailure.PARSE_RULE,
                    "to and thru take a place or something to look for, not "
                            + Molder.mold(wanted));
        }
    }

    private int findFirstPlaceRuleMatches(ParseWalk walk, Value looked) {
        for (int from = walk.position(); from <= walk.inputLength(); from++) {
            walk.moveTo(from);
            if (walk.matchValue(looked)) {
                if (!past) {
                    walk.moveTo(from);
                }
                return 2;
            }
        }
        return ParseWalk.NO_MATCH;
    }

    private int findFirstPlaceTextAppears(ParseWalk walk, Value looked) {
        int[] needle = walk.textFrom(looked).codePoints().toArray();
        int found = walk.findFirstMatchFrom(needle, walk.position());
        if (found < 0) {
            return ParseWalk.NO_MATCH;
        }
        walk.moveTo(past ? found + needle.length : found);
        return 2;
    }
}
