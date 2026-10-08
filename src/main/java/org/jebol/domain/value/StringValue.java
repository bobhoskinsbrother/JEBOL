package org.jebol.domain.value;

public final class StringValue extends AnyStringValue {

    StringValue(StringStorage storage, int index) {
        super(storage, index);
    }

    public static StringValue of(String text) {
        return new StringValue(StringStorage.of(text), 1);
    }

    @Override
    public Datatype datatype() {
        return Datatype.STRING;
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
