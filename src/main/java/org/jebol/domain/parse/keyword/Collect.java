package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseTargets;
import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.ArrayList;
import java.util.List;

final class Collect extends SameForABlockAndAString {

    Collect() {
        super("collect", 0, true, WhenNothingFollowsIt.RAISES_PARSE_END);
    }

    @Override
    int apply(ParseWalk walk, List<Value> rules, int at) {
        WhereItGoes goes = whereTheGatheringGoes(rules, at);
        if (goes.ruleAt() >= rules.size()) {
            return ParseWalk.NO_MATCH;
        }
        BlockValue destination = null;
        if (goes.assignedTo() != null) {
            destination = BlockValue.block(new ArrayList<>());
            walk.assign(goes.assignedTo(), destination);
        }
        walk.startCollecting();
        int consumed = walk.matchOne(rules, goes.ruleAt());
        List<Value> mine = walk.stopCollecting();

        if (goes.appendedTo() != null) {
            walk.deliverTheCollectedTo(goes.appendedTo(), mine, true);
        } else if (goes.insertedInto() != null) {
            walk.deliverTheCollectedTo(goes.insertedInto(), mine, false);
        } else if (destination != null) {
            for (Value item : mine) {
                destination.storage().insertAt(destination.storageLength() + 1, item);
            }
        } else {
            walk.deliverTheCollected(mine);
        }
        return consumed == ParseWalk.NO_MATCH
                ? ParseWalk.NO_MATCH
                : (goes.ruleAt() - at) + walk.ruleSpan(rules, goes.ruleAt());
    }

    private record WhereItGoes(WordValue assignedTo, WordValue insertedInto,
            WordValue appendedTo, int ruleAt) {
    }

    private static WhereItGoes whereTheGatheringGoes(List<Value> rules, int at) {
        if (!(rules.get(at + 1) instanceof WordValue keyword)
                || keyword.datatype() != Datatype.WORD) {
            return new WhereItGoes(null, null, null, at + 1);
        }
        Value name = at + 2 < rules.size() ? rules.get(at + 2) : null;
        return switch (keyword.canonical()) {
            case "set" -> new WhereItGoes(
                    ParseTargets.refuseAnythingButAWordOrASetWord(name),
                    null, null, at + 3);
            case "into" -> new WhereItGoes(null,
                    ParseTargets.refuseAnythingButAWordOrAGetWord(name),
                    null, at + 3);
            case "after" -> new WhereItGoes(null, null,
                    ParseTargets.refuseAnythingButAWordOrAGetWord(name), at + 3);
            default -> new WhereItGoes(null, null, null, at + 1);
        };
    }
}
