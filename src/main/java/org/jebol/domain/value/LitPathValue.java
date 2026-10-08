package org.jebol.domain.value;

public final class LitPathValue extends AnyPathValue {

    LitPathValue(BlockStorage storage, int index) {
        super(storage, index);
    }

    @Override
    public Datatype datatype() {
        return Datatype.LIT_PATH;
    }

    @Override
    LitPathValue sameKindOver(BlockStorage storage, int index) {
        return new LitPathValue(storage, index);
    }
}
