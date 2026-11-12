package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Value;

import java.util.List;

final class Insert extends DeclaredKeyword {

    Insert() {
        super("insert", 0, true, WhenNothingFollowsIt.RAISES_PARSE_END_AT_END);
    }

    @Override
    public int applyToBlock(ParseWalk walk, List<Value> rules, int at) {
        ValueToPutIn written = ValueToPutIn.readFrom(rules, at + 1);
        Value added = walk.chooseValueToInsert(rules.get(written.slot()));
        List<Value> putting = !written.whole() && added instanceof BlockValue spread
                ? spread.remaining()
                : List.of(added);
        walk.putItemsIntoBlockAt(walk.position(), putting);
        walk.moveTo(walk.position() + putting.size());
        return written.slot() + 1 - at;
    }

    @Override
    public int applyToString(ParseWalk walk, List<Value> rules, int at) {
        ValueToPutIn written = ValueToPutIn.readFrom(rules, at + 1);
        Value added = walk.replacementFor(rules.get(written.slot()));
        int laidIn = walk.putValueIntoTextAt(walk.position(), added);
        walk.moveTo(walk.position() + laidIn);
        return written.slot() + 1 - at;
    }
}
