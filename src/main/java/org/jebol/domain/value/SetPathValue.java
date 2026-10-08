package org.jebol.domain.value;

public final class SetPathValue extends AnyPathValue {

    SetPathValue(BlockStorage storage, int index) {
        super(storage, index);
    }

    @Override
    public Datatype datatype() {
        return Datatype.SET_PATH;
    }

    @Override
    SetPathValue sameKindOver(BlockStorage storage, int index) {
        return new SetPathValue(storage, index);
    }
}
