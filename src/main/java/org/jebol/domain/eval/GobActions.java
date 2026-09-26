package org.jebol.domain.eval;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.SeriesValue;
import org.jebol.domain.value.Value;

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
    void takeOneOutAt(int oneBasedIndex) {
        gob.storage().removeChildren(oneBasedIndex, 1);
    }

    @Override
    List<Value> elementsOf(SeriesValue from) {
        GobValue pane = (GobValue) from;
        return pane.storage().pane().subList(
                Math.min(pane.index() - 1, pane.storage().length()),
                pane.storage().length());
    }

    @Override
    Value ofTheSameKindHolding(List<Value> items) {
        return BlockValue.block(items);
    }

    @Override
    public Value cleared() {
        gob.storage().removeChildren(gob.index(),
                gob.storage().length() - gob.index() + 1);
        return gob;
    }

    @Override
    public Value append(Asked asked) {
        asked.refuseRefinementsThisDatatypeDoesNotServe("append");
        givenTheChildrenOf(asked.given(), gob.storage().length() + 1);
        return gob;
    }

    @Override
    public Value insert(Asked asked) {
        asked.refuseRefinementsThisDatatypeDoesNotServe("insert");
        givenTheChildrenOf(asked.given(), gob.positionWithinThePane());
        return gob;
    }

    Value givenTheChildrenOf(Value value, int at) {
        int goesAt = at;
        for (Value child : theChildrenOffered(value)) {
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

    private static List<Value> theChildrenOffered(Value value) {
        return switch (value) {
            case GobValue only -> List.of(only);
            case BlockValue block when block.datatype() == Datatype.BLOCK ->
                    block.remaining();
            default -> throw Raised.of(EvaluationFailure.EXPECT_VAL,
                    "a pane holds gobs, not " + value.datatype().literalSpelling());
        };
    }
}
