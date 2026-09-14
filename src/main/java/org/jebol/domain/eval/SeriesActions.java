package org.jebol.domain.eval;

import org.jebol.domain.value.SeriesValue;
import org.jebol.domain.value.Value;

import java.util.List;

abstract class SeriesActions implements Actions {

    abstract SeriesValue held();

    abstract void takeOneOutAt(int oneBasedIndex);

    abstract List<Value> elementsOf(SeriesValue from);

    abstract Value ofTheSameKindHolding(List<Value> items);

    @Override
    public Value subject() {
        return (Value) held();
    }

    @Override
    public int length() {
        return held().lengthFromHere();
    }

    public Value removed(long howMany) {
        SeriesValue removingFrom = theRunReachingBackIfNegative(howMany);
        for (long dropped = 0; dropped < Math.abs(howMany)
                && !removingFrom.atTail(); dropped++) {
            takeOneOutAt(removingFrom.index());
        }
        return (Value) removingFrom;
    }

    void takeOutFrom(int oneBasedIndex, int howMany) {
        for (int gone = 0; gone < howMany; gone++) {
            takeOneOutAt(oneBasedIndex);
        }
    }

    public Value takenSeveral(long wanted) {
        int from = Math.min(held().index(), held().storageLength() + 1);
        int howMany;
        if (wanted >= 0) {
            howMany = (int) Math.min(wanted, held().lengthFromHere());
        } else {
            howMany = (int) Math.min(-wanted, from - 1L);
            from -= howMany;
        }
        List<Value> taken = List.copyOf(
                elementsOf(held().head()).subList(from - 1, from - 1 + howMany));
        takeOutFrom(from, howMany);
        return ofTheSameKindHolding(taken);
    }

    public Value takenOne() {
        if (held().lengthFromHere() == 0) {
            return org.jebol.domain.value.NoneValue.none();
        }
        Value taken = elementsOf(held()).getFirst();
        takeOutFrom(held().index(), 1);
        return taken;
    }

    Value clearedOneAtATime() {
        while (held().storageLength() >= held().index()) {
            takeOneOutAt(held().index());
        }
        return (Value) held();
    }

    private SeriesValue theRunReachingBackIfNegative(long wanted) {
        if (wanted >= 0) {
            return held();
        }
        int reaching = (int) Math.min(-wanted, held().index() - 1L);
        return held().atIndex(held().index() - reaching);
    }
}
