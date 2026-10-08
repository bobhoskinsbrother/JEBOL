package org.jebol.domain.value;

public final class LitPathValue extends AnyPathValue {

    public static final AnyPathDatatype TYPE = new AnyPathDatatype("lit-path") {

        @Override
        public AnyPathValue holding(BlockStorage storage, int index) {
            return new LitPathValue(storage, index);
        }
    };

    LitPathValue(BlockStorage storage, int index) {
        super(storage, index);
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    String markedBefore(boolean forReading) {
        return forReading ? "'" : "";
    }

    @Override
    LitPathValue sameKindOver(BlockStorage storage, int index) {
        return new LitPathValue(storage, index);
    }
}
