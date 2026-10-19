package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.List;

public final class GobActions extends SeriesActions {

    private final GobValue gob;

    public GobActions(GobValue gob) {
        this.gob = gob;
    }

    @Override
    GobValue held() {
        return gob;
    }

    @Override
    public Value poke(Value position, Value written) {
        GobPath.pokeWhichInsertsRatherThanReplaces(gob, (int) position.asPosition(), written);
        return written;
    }

    @Override
    void takeOneOutAt(int oneBasedIndex) {
        gob.storage().removeChildren(oneBasedIndex, 1);
    }

    @Override
    Value ofTheSameKindHolding(List<Value> items) {
        return BlockValue.of(items);
    }

    @Override
    public Value clear() {
        gob.storage().removeChildren(gob.index(),
                gob.storage().length() - gob.index() + 1);
        return gob;
    }

    @Override
    public Value append(Asked asked) {
        asked.refuseRefinementsThisDatatypeDoesNotServe("append");
        takeTheChildrenOf(asked.given(), gob.storage().length() + 1);
        return gob;
    }

    @Override
    public Value insert(Asked asked) {
        asked.refuseRefinementsThisDatatypeDoesNotServe("insert");
        takeTheChildrenOf(asked.given(), gob.findThePositionWithinThePane());
        return gob;
    }

    Value takeTheChildrenOf(Value value, int at) {
        int goesAt = at;
        for (Value child : listTheChildrenOffered(value)) {
            if (!(child instanceof GobValue one)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG,
                        "a pane holds gobs, not "
                                + child.datatype().literalSpelling());
            }
            gob.storage().insertChild(goesAt, one);
            goesAt = Math.min(goesAt + 1, gob.storage().length() + 1);
        }
        return gob;
    }

    private static List<Value> listTheChildrenOffered(Value value) {
        return switch (value) {
            case GobValue only -> List.of(only);
            case BlockValue block ->
                    block.remaining();
            default -> throw Raised.of(EvaluationFailure.EXPECT_VAL,
                    "a pane holds gobs, not " + value.datatype().literalSpelling());
        };
    }
}
