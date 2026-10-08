package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.List;

public final class BlockValue extends AnyBlockValue {

    BlockValue(BlockStorage storage, int index) {
        super(storage, index);
    }

    public static BlockValue block(Value... items) {
        return new BlockValue(BlockStorage.of(items), 1);
    }

    public static BlockValue block(List<Value> items) {
        return new BlockValue(new BlockStorage(items), 1);
    }

    public static BlockValue over(BlockStorage storage) {
        return new BlockValue(storage, 1);
    }

    @Override
    public Datatype datatype() {
        return Datatype.BLOCK;
    }

    @Override
    BlockValue sameKindOver(BlockStorage storage, int index) {
        return new BlockValue(storage, index);
    }

    @Override
    public BlockValue atIndex(int oneBasedIndex) {
        return sameKindOver(storage(), oneBasedIndex);
    }

    @Override
    public Value randomised(RandomDraw draw) {
        List<Value> items = new ArrayList<>(remaining());
        draw.shuffle(items);
        for (int at = 0; at < items.size(); at++) {
            storage().set(index() + at, items.get(at));
        }
        return this;
    }

    @Override
    public Value pickedAtRandom(RandomDraw draw) {
        List<Value> items = remaining();
        return items.isEmpty() ? NoneValue.none() : items.get(draw.below(items.size()));
    }

    @Override
    public BlockValue repeatedInABlock(Value times) {
        long count = times.asCountOfRepetitions();
        BlockStorage repeated = new BlockStorage();
        for (long round = 0; round < count; round++) {
            repeated.spliceInAt(repeated.length() + 1, remaining(), storage(), index());
        }
        return new BlockValue(repeated, 1);
    }
}
