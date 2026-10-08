package org.jebol.domain.value;

public final class TagValue extends AnyStringValue {

    TagValue(StringStorage storage, int index) {
        super(storage, index);
    }

    public static TagValue of(String text) {
        return new TagValue(StringStorage.of(text), 1);
    }

    @Override
    public Datatype datatype() {
        return Datatype.TAG;
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
