package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.parse.Returned;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Value;

import java.util.List;

final class Return extends SameForABlockAndAString {

    Return() {
        super("return", 0, true, WhenNothingFollowsIt.DOES_NOT_MATCH);
    }

    @Override
    int apply(ParseWalk walk, List<Value> rules, int at) {
        if (rules.get(at + 1) instanceof BlockValue paren
                && paren.datatype() == Datatype.PAREN) {
            throw new Returned(walk.evaluateParen(paren));
        }
        int before = walk.position();
        if (walk.matchOne(rules, at + 1) == ParseWalk.NO_MATCH) {
            walk.moveTo(before);
            return ParseWalk.NO_MATCH;
        }
        throw new Returned(walk.sliceBetween(before, walk.position()));
    }
}
