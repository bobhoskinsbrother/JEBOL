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
    public Value poked(Value position, Value written) {
        VectorPath.write(vector, position, written);
        return written;
    }

    @Override
    void takeOneOutAt(int oneBasedIndex) {
        vector.storage().removeAt(oneBasedIndex);
    }

    @Override
    Value ofTheSameKindHolding(List<Value> items) {
        VectorStorage made = new VectorStorage(vector.kind(), 0);
        items.forEach(number ->
                made.append(VectorPath.storedFormOf(vector.kind(), number)));
        return new VectorValue(made, 1);
    }

    @Override
    public Value cleared() {
        vector.storage().clearFrom(vector.index());
        return vector;
    }

    @Override
    public Value append(Asked asked) {
        for (Value number : numbersAddedBy(asked)) {
            vector.storage().append(VectorPath.storedFormOf(vector.kind(), number));
        }
        return vector.head();
    }

    @Override
    public Value insert(Asked asked) {
        VectorValue held = (VectorValue) vector.clampedToTail();
        List<Value> numbers = numbersAddedBy(asked);
        for (int at = numbers.size(); at > 0; at--) {
            held.storage().insertAt(held.index(),
                    VectorPath.storedFormOf(held.kind(), numbers.get(at - 1)));
        }
        return held.atIndex(held.index() + numbers.size());
    }

    private List<Value> numbersAddedBy(Asked asked) {
        List<Value> once = asked.refinementsAsked().contains("part")
                ? vector.kind().numbersOfferedBy(asked.given(), asked.howManyOctetsWanted())
                : vector.kind().numbersContributedBy(asked.given());
        List<Value> added = new ArrayList<>();
        for (long round = 0; round < asked.howManyTimes(); round++) {
            added.addAll(once);
        }
        return added;
    }
}
