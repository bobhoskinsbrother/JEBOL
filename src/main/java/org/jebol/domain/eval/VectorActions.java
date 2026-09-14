package org.jebol.domain.eval;

import org.jebol.domain.value.SeriesValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorStorage;
import org.jebol.domain.value.VectorValue;

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
    void takeOneOutAt(int oneBasedIndex) {
        vector.storage().removeAt(oneBasedIndex);
    }

    @Override
    List<Value> elementsOf(SeriesValue from) {
        return ((VectorValue) from).remaining();
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
        VectorValue held = (VectorValue) Natives.clampedToTail(vector);
        List<Value> numbers = numbersAddedBy(asked);
        for (int at = numbers.size(); at > 0; at--) {
            held.storage().insertAt(held.index(),
                    VectorPath.storedFormOf(held.kind(), numbers.get(at - 1)));
        }
        return held.atIndex(held.index() + numbers.size());
    }

    private List<Value> numbersAddedBy(Asked asked) {
        List<Value> once = asked.refinementsAsked().contains("part")
                ? Natives.numbersOfferedTo(vector.kind(), asked.given(),
                        asked.howManyOctetsWanted())
                : Natives.numbersContributedTo(vector.kind(), asked.given());
        List<Value> added = new ArrayList<>();
        for (long round = 0; round < asked.howManyTimes(); round++) {
            added.addAll(once);
        }
        return added;
    }
}
