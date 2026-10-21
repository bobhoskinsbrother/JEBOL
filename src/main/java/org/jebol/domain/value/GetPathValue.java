package org.jebol.domain.value;

public final class GetPathValue extends AnyPathValue {

    public static final AnyPathDatatype TYPE = new AnyPathDatatype("get-path") {

        @Override
        public AnyPathValue wrap(BlockStorage storage, int index) {
            return new GetPathValue(storage, index);
        }
    };

    GetPathValue(BlockStorage storage, int index) {
        super(storage, index);
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    String markBefore(boolean forReading) {
        return forReading ? ":" : "";
    }

    @Override
    GetPathValue makeSameKindOver(BlockStorage storage, int index) {
        return new GetPathValue(storage, index);
    }

    @Override
    public boolean looksUpItsDeclaration() {
        return true;
    }
}
