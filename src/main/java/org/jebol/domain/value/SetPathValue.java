package org.jebol.domain.value;

public final class SetPathValue extends AnyPathValue {

    public static final AnyPathDatatype TYPE = new AnyPathDatatype("set-path") {

        @Override
        public AnyPathValue holding(BlockStorage storage, int index) {
            return new SetPathValue(storage, index);
        }
    };

    SetPathValue(BlockStorage storage, int index) {
        super(storage, index);
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    String markedAfter(boolean forReading) {
        return forReading ? ":" : "";
    }

    @Override
    SetPathValue sameKindOver(BlockStorage storage, int index) {
        return new SetPathValue(storage, index);
    }
}
