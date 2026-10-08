package org.jebol.domain.value;

public final class GetPathValue extends AnyPathValue {

    public static final AnyPathDatatype TYPE = new AnyPathDatatype("get-path") {

        @Override
        public AnyPathValue holding(BlockStorage storage, int index) {
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
    String markedBefore(boolean forReading) {
        return forReading ? ":" : "";
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
