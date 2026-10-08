package org.jebol.domain.value;

public final class UrlValue extends AnyStringValue {

    private static final char ENDS_THE_SCHEME = ':';

    UrlValue(StringStorage storage, int index) {
        super(storage, index);
    }

    public static UrlValue of(String address) {
        return new UrlValue(StringStorage.of(address), 1);
    }

    @Override
    public Datatype datatype() {
        return Datatype.URL;
    }

    @Override
    UrlValue sameKindOver(StringStorage storage, int index) {
        return new UrlValue(storage, index);
    }

    @Override
    public String mold() {
        return moldedUnlessItWouldNotReadBackWhenMarkedBy(ENDS_THE_SCHEME);
    }

    @Override
    public boolean isALocation() {
        return true;
    }
}
