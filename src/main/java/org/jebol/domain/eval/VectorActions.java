package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;

public final class VectorActions extends SeriesActions {

    private final VectorValue vector;

    public VectorActions(VectorValue vector) {
        this.vector = vector;
    }

    @Override
    VectorValue held() {
        return vector;
    }

    @Override
    public Value poke(Value position, Value written) {
        VectorPath.write(vector, position, written);
        return written;
    }

    @Override
    void takeOneOutAt(int oneBasedIndex) {
        vector.storage().removeAt(oneBasedIndex);
    }

    @Override
    Value ofSameKindHolding(List<Value> items) {
        VectorStorage made = new VectorStorage(vector.kind(), 0);
        items.forEach(number ->
                made.append(VectorPath.encodeForStorage(vector.kind(), number)));
        return new VectorValue(made, 1);
    }

    @Override
    public Value clear() {
        vector.storage().clearFrom(vector.index());
        return vector;
    }

    @Override
    public Value append(Asked asked) {
        for (Value number : collectTheNumbersAddedBy(asked)) {
            vector.storage().append(VectorPath.encodeForStorage(vector.kind(), number));
        }
        return vector.head();
    }

    @Override
    public Value insert(Asked asked) {
        VectorValue held = (VectorValue) vector.clampToTail();
        List<Value> numbers = collectTheNumbersAddedBy(asked);
        for (int at = numbers.size(); at > 0; at--) {
            held.storage().insertAt(held.index(),
                    VectorPath.encodeForStorage(held.kind(), numbers.get(at - 1)));
        }
        return held.atIndex(held.index() + numbers.size());
    }

    private List<Value> collectTheNumbersAddedBy(Asked asked) {
        List<Value> once = asked.refinementsAsked().contains("part")
                ? vector.kind().collectTheNumbersOfferedBy(asked.given(), asked.countOctetsWanted())
                : vector.kind().collectNumbersContributedBy(asked.given());
        List<Value> added = new ArrayList<>();
        for (long round = 0; round < asked.howManyTimes(); round++) {
            added.addAll(once);
        }
        return added;
    }
}
