package org.jebol.domain.value;

public final class StringValue extends AnyStringValue {

    public static final AnyStringDatatype TYPE = new AnyStringDatatype("string") {

        @Override
        public AnyStringValue holding(StringStorage storage, int index) {
            return new StringValue(storage, index);
        }
    };

    StringValue(StringStorage storage, int index) {
        super(storage, index);
    }

    public static StringValue of(String text) {
        return new StringValue(StringStorage.of(text), 1);
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    StringValue sameKindOver(StringStorage storage, int index) {
        return new StringValue(storage, index);
    }

    @Override
    public String mold() {
        return Molder.moldedText(text());
    }
}
