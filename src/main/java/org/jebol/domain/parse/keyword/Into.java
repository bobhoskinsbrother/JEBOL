package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.SeriesValue;
import org.jebol.domain.value.Value;

import java.util.List;

final class Into extends DeclaredKeyword {

    Into() {
        super("into", 0, true, WhenNothingFollowsIt.RAISES_PARSE_END);
    }

    @Override
    public int applyToBlock(ParseWalk walk, List<Value> rules, int at) {
        if (!(walk.whatTheWordHolds(rules.get(at + 1)) instanceof BlockValue innerRule)) {
            throw Raised.of(EvaluationFailure.PARSE_RULE,
                    "into needs a block of rules to apply");
        }
        if (walk.atEnd() || !(walk.current() instanceof SeriesValue nested)
                || !walk.walkingOver(nested).matchesTheWholeOf(innerRule)) {
            return ParseWalk.NO_MATCH;
        }
        walk.moveTo(walk.position() + 1);
        return 2;
    }

    @Override
    public int applyToString(ParseWalk walk, List<Value> rules, int at) {
        return ParseWalk.NO_MATCH;
    }
}
