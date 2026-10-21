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

    abstract Value ofSameKindHolding(List<Value> items);

    @Override
    public Value subject() {
        return held();
    }

    @Override
    public int measureLength() {
        return held().lengthFromHere();
    }

    @Override
    public Value remove(long howMany) {
        RebolSeries removingFrom = held().reachBackIfNegative(howMany);
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
    public Value takeSeveral(long wanted) {
        int from = Math.min(held().index(), held().storageLength() + 1);
        int howMany;
        if (wanted >= 0) {
            howMany = (int) Math.min(wanted, held().lengthFromHere());
        } else {
            howMany = (int) Math.min(-wanted, from - 1L);
            from -= howMany;
        }
        List<Value> taken = List.copyOf(
                held().head().listItems().subList(from - 1, from - 1 + howMany));
        takeOutFrom(from, howMany);
        return ofSameKindHolding(taken);
    }

    @Override
    public Value takeOne() {
        if (held().lengthFromHere() == 0) {
            return NoneValue.none();
        }
        Value taken = held().listItems().getFirst();
        takeOutFrom(held().index(), 1);
        return taken;
    }

    int findPokedStoragePosition(Value position) {
        long at = position.asPosition();
        if (at < 1 || at > held().lengthFromHere()) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, position);
        }
        return held().index() + (int) at - 1;
    }

    Value clearOneAtTime() {
        while (held().storageLength() >= held().index()) {
            takeOneOutAt(held().index());
        }
        return held();
    }
}
