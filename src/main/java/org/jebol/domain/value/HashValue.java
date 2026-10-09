package org.jebol.domain.value;

public final class HashValue extends AnyBlockValue {

    public static final AnyBlockDatatype TYPE = new AnyBlockDatatype("hash") {

        @Override
        public AnyBlockValue holding(BlockStorage storage, int index) {
            return new HashValue(storage, index);
        }

        @Override
        boolean wrapsWhatItConverts() {
            return false;
        }
    };

    HashValue(BlockStorage storage, int index) {
        super(storage, index);
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    String opensWith() {
        return "make hash! [";
    }

    @Override
    HashValue sameKindOver(BlockStorage storage, int index) {
        return new HashValue(storage, index);
    }
}
