package org.jebol.domain.value;

public final class HashValue extends AnyBlockValue {

    HashValue(BlockStorage storage, int index) {
        super(storage, index);
    }

    @Override
    public Datatype datatype() {
        return Datatype.HASH;
    }

    @Override
    HashValue sameKindOver(BlockStorage storage, int index) {
        return new HashValue(storage, index);
    }
}
