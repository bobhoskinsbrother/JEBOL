package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.List;

public final class BlockValue extends AnyBlockValue {

    public static final AnyBlockDatatype TYPE = new AnyBlockDatatype("block") {

        @Override
        public AnyBlockValue wrap(BlockStorage storage, int index) {
            return new BlockValue(storage, index);
        }

        @Override
        boolean listsATypesetsMembers() {
            return true;
        }
    };

    BlockValue(BlockStorage storage, int index) {
        super(storage, index);
    }

    public static BlockValue of(Value... items) {
        return new BlockValue(BlockStorage.of(items), 1);
    }

    public static BlockValue of(List<Value> items) {
        return new BlockValue(new BlockStorage(items), 1);
    }

    public static BlockValue wrapTheStorage(BlockStorage storage) {
        return new BlockValue(storage, 1);
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    BlockValue makeSameKindOver(BlockStorage storage, int index) {
        return new BlockValue(storage, index);
    }

    @Override
    public BlockValue atIndex(int oneBasedIndex) {
        return makeSameKindOver(storage(), oneBasedIndex);
    }

    @Override
    public Value randomise(RandomDraw draw) {
        List<Value> items = new ArrayList<>(remaining());
        draw.shuffle(items);
        for (int at = 0; at < items.size(); at++) {
            storage().set(index() + at, items.get(at));
        }
        return this;
    }

    @Override
    public Value pickAtRandom(RandomDraw draw) {
        List<Value> items = remaining();
        return items.isEmpty() ? NoneValue.none() : items.get(draw.below(items.size()));
    }

    @Override
    public BlockValue repeatInABlock(Value times) {
        long count = times.asCountOfRepetitions();
        BlockStorage repeated = new BlockStorage();
        for (long round = 0; round < count; round++) {
            repeated.spliceInAt(repeated.length() + 1, remaining(), storage(), index());
        }
        return new BlockValue(repeated, 1);
    }
}
