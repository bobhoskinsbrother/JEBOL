package org.jebol.domain.value;

public final class TagValue extends AnyStringValue {

    public static final AnyStringDatatype TYPE = new AnyStringDatatype("tag") {

        @Override
        public AnyStringValue holding(StringStorage storage, int index) {
            return new TagValue(storage, index);
        }
    };

    TagValue(StringStorage storage, int index) {
        super(storage, index);
    }

    public static TagValue of(String text) {
        return new TagValue(StringStorage.of(text), 1);
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    TagValue sameKindOver(StringStorage storage, int index) {
        return new TagValue(storage, index);
    }

    @Override
    public String mold() {
        return form();
    }

    @Override
    public String form() {
        return "<" + text() + ">";
    }
}
