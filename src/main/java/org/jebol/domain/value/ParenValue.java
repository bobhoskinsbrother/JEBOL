package org.jebol.domain.value;

import java.util.List;

public final class ParenValue extends AnyBlockValue {

    ParenValue(BlockStorage storage, int index) {
        super(storage, index);
    }

    public static ParenValue of(List<Value> items) {
        return new ParenValue(new BlockStorage(items), 1);
    }

    @Override
    public Datatype datatype() {
        return Datatype.PAREN;
    }

    @Override
    ParenValue sameKindOver(BlockStorage storage, int index) {
        return new ParenValue(storage, index);
    }
}
