package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;

final class Insert extends DeclaredKeyword {

    private static final String WHOLE = "only";

    Insert() {
        super("insert", 0, true, WhenNothingFollowsIt.RAISES_PARSE_END);
    }

    @Override
    public int applyToBlock(ParseWalk walk, List<Value> rules, int at) {
        int valueAt = at + 1;
        boolean wholeBlock = saysToPutTheBlockInWhole(rules, valueAt);
        if (wholeBlock) {
            valueAt++;
        }
        Value added = walk.theValueToInsert(rules.get(valueAt));
        List<Value> putting = !wholeBlock && added instanceof BlockValue spread
                && spread.datatype() == Datatype.BLOCK
                ? spread.remaining()
                : List.of(added);
        walk.putItemsIntoTheBlockAt(walk.position(), putting);
        walk.moveTo(walk.position() + putting.size());
        return valueAt + 1 - at;
    }

    @Override
    public int applyToString(ParseWalk walk, List<Value> rules, int at) {
        Value added = rules.get(at + 1);
        if (added instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            added = walk.evaluateParen(paren);
        }
        int laidIn = walk.putValueIntoTheTextAt(walk.position(), added);
        walk.moveTo(walk.position() + laidIn);
        return 2;
    }

    private static boolean saysToPutTheBlockInWhole(List<Value> rules, int valueAt) {
        return rules.get(valueAt) instanceof WordValue modifier
                && modifier.datatype() == Datatype.WORD
                && modifier.canonical().equals(WHOLE)
                && valueAt + 1 < rules.size();
    }
}
