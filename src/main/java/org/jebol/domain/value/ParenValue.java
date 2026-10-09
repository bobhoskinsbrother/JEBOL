package org.jebol.domain.value;

import java.util.List;

public final class ParenValue extends AnyBlockValue {

    public static final AnyBlockDatatype TYPE = new AnyBlockDatatype("paren") {

        @Override
        public AnyBlockValue holding(BlockStorage storage, int index) {
            return new ParenValue(storage, index);
        }

        @Override
        boolean listsATypesetsMembers() {
            return true;
        }
    };

    ParenValue(BlockStorage storage, int index) {
        super(storage, index);
    }

    public static ParenValue of(List<Value> items) {
        return new ParenValue(new BlockStorage(items), 1);
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    String opensWith() {
        return "(";
    }

    @Override
    String closesWith() {
        return ")";
    }

    @Override
    ParenValue sameKindOver(BlockStorage storage, int index) {
        return new ParenValue(storage, index);
    }
}
