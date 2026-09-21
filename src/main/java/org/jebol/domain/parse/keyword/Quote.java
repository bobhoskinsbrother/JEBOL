package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Value;

import java.util.List;

final class Quote extends DeclaredKeyword {

    Quote() {
        super("quote", 1, false, WhenNothingFollowsIt.RAISES_PARSE_END);
    }

    @Override
    public int applyToBlock(ParseWalk walk, List<Value> rules, int at) {
        Value wanted = rules.get(at + 1);
        if (wanted instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            wanted = walk.evaluateParen(paren);
        }
        return walk.matchesLiteral(wanted) ? 2 : ParseWalk.NO_MATCH;
    }

    @Override
    public int applyToString(ParseWalk walk, List<Value> rules, int at) {
        return ParseWalk.NO_MATCH;
    }
}
