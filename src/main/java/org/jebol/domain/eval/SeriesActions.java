package org.jebol.domain.eval;

import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;

import java.util.List;

abstract class SeriesActions implements Actions {

    abstract RebolSeries held();

    abstract void takeOneOutAt(int oneBasedIndex);

    abstract Value ofTheSameKindHolding(List<Value> items);

    @Override
    public Value subject() {
        return held();
    }

    @Override
    public int length() {
        return held().lengthFromHere();
    }

    @Override
    public Value removed(long howMany) {
        RebolSeries removingFrom = theRunReachingBackIfNegative(howMany);
        for (long dropped = 0; dropped < Math.abs(howMany)
                && !removingFrom.atTail(); dropped++) {
            takeOneOutAt(removingFrom.index());
        }
        return removingFrom;
    }

    @Override
    public void takeOutFrom(int oneBasedIndex, int howMany) {
        for (int gone = 0; gone < howMany; gone++) {
            takeOneOutAt(oneBasedIndex);
        }
    }

    @Override
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
                held().head().items().subList(from - 1, from - 1 + howMany));
        takeOutFrom(from, howMany);
        return ofTheSameKindHolding(taken);
    }

    @Override
    public Value takenOne() {
        if (held().lengthFromHere() == 0) {
            return NoneValue.none();
        }
        Value taken = held().items().getFirst();
        takeOutFrom(held().index(), 1);
        return taken;
    }

    int pokedStoragePosition(Value position) {
        long at = position.asPosition();
        if (at < 1 || at > held().lengthFromHere()) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "poke at " + at + " on a series of " + held().lengthFromHere());
        }
        return held().index() + (int) at - 1;
    }

    Value clearedOneAtATime() {
        while (held().storageLength() >= held().index()) {
            takeOneOutAt(held().index());
        }
        return held();
    }

    private RebolSeries theRunReachingBackIfNegative(long wanted) {
        if (wanted >= 0) {
            return held();
        }
        int reaching = (int) Math.min(-wanted, held().index() - 1L);
        return held().atIndex(held().index() - reaching);
    }
}
