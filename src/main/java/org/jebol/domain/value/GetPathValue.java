package org.jebol.domain.value;

public final class GetPathValue extends AnyPathValue {

    GetPathValue(BlockStorage storage, int index) {
        super(storage, index);
    }

    @Override
    public Datatype datatype() {
        return Datatype.GET_PATH;
    }

    @Override
    GetPathValue sameKindOver(BlockStorage storage, int index) {
        return new GetPathValue(storage, index);
    }

    @Override
    public boolean looksUpItsDeclaration() {
        return true;
    }
}
