package org.jebol.domain.eval;

import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Value;

import java.util.List;

public final class BlockActions extends SeriesActions {

    private final AnyBlockValue block;

    public BlockActions(AnyBlockValue block) {
        this.block = block;
    }

    @Override
    AnyBlockValue held() {
        return block;
    }

    @Override
    public Value poked(Value position, Value written) {
        block.storage().set(pokedStoragePosition(position), written);
        return written;
    }

    @Override
    void takeOneOutAt(int oneBasedIndex) {
        block.storage().removeAt(oneBasedIndex);
    }

    @Override
    Value ofTheSameKindHolding(List<Value> items) {
        return block.holding(items);
    }

    @Override
    public Value cleared() {
        return clearedOneAtATime();
    }

    @Override
    public Value append(Asked asked) {
        if (asked.duplicated() instanceof AnyBlockValue added
                && splicesRatherThanGoesInWhole(added, asked)) {
            block.storage().spliceInAt(
                    block.storage().length() + 1,
                    asked.theFirstFewOf(added.remaining()),
                    added.storage(), added.index());
        } else {
            block.storage().append(asked.given());
        }
        return block.head();
    }

    @Override
    public Value insert(Asked asked) {
        AnyBlockValue held = (AnyBlockValue) block.clampedToTail();
        if (asked.duplicated() instanceof AnyBlockValue added
                && splicesRatherThanGoesInWhole(added, asked)) {
            List<Value> items = asked.theWantedItemsOf(added);
            held.storage().spliceInAt(held.index(), items,
                    added.storage(), added.index());
            return held.atIndex(held.index() + items.size());
        }
        held.storage().insertAt(held.index(), asked.given());
        return held.atIndex(held.index() + 1);
    }

    private static boolean splicesRatherThanGoesInWhole(AnyBlockValue added, Asked asked) {
        return added instanceof BlockValue && !asked.wholeRatherThanSpliced();
    }
}
