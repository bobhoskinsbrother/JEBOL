package org.jebol.domain.eval;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Value;

import java.util.List;

public final class BlockActions extends SeriesActions {

    private final BlockValue block;

    public BlockActions(BlockValue block) {
        this.block = block;
    }

    @Override
    BlockValue held() {
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
        return BlockValue.block(items).as(block.datatype());
    }

    @Override
    public Value cleared() {
        return clearedOneAtATime();
    }

    @Override
    public Value append(Asked asked) {
        if (asked.duplicated() instanceof BlockValue added
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
        BlockValue held = (BlockValue) RebolNativeWords.clampedToTail(block);
        if (asked.duplicated() instanceof BlockValue added
                && splicesRatherThanGoesInWhole(added, asked)) {
            List<Value> items = asked.theWantedItemsOf(added);
            held.storage().spliceInAt(held.index(), items,
                    added.storage(), added.index());
            return held.atIndex(held.index() + items.size());
        }
        held.storage().insertAt(held.index(), asked.given());
        return held.atIndex(held.index() + 1);
    }

    private static boolean splicesRatherThanGoesInWhole(BlockValue added, Asked asked) {
        return added.datatype() == Datatype.BLOCK && !asked.wholeRatherThanSpliced();
    }
}
