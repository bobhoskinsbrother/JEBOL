package org.jebol.domain.eval;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.Value;

import java.util.List;

/**
 * What a gob does when an action is performed on it, which is what
 * {@code REBTYPE(Gob)} answers in {@code t-gob.c}.
 *
 * <p>A gob's series is its pane, and a pane holds gobs and nothing else.
 * APPEND puts a child at the end of the pane and answers the gob; INSERT puts
 * one where the gob is held and answers the position after it.
 *
 * <p>None of /PART, /ONLY or /DUP is served, and saying so is the first thing
 * either arm does.
 */
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

    /** A pane drops the whole run of children at once. */
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

    /**
     * One gob, or every gob a block holds, put into the pane from a position.
     *
     * <p>Each one lands after the last, and never past the end of a pane that
     * detaching a child may have shortened underneath.
     */
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
            case GobValue only -> List.<Value>of(only);
            case BlockValue block when block.datatype() == Datatype.BLOCK ->
                    block.remaining();
            default -> throw Raised.of(EvaluationFailure.EXPECT_VAL,
                    "a pane holds gobs, not " + value.datatype().literalSpelling());
        };
    }
}
