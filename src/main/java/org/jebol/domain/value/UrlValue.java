package org.jebol.domain.value;

import java.util.List;
import java.util.stream.Collectors;

public final class UrlValue extends AnyStringValue {

    private static final char ENDS_THE_SCHEME = ':';

    public static final AnyStringDatatype TYPE = new AnyStringDatatype("url") {

        @Override
        public AnyStringValue holding(StringStorage storage, int index) {
            return new UrlValue(storage, index);
        }

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            return from instanceof AnyBlockValue parts
                    ? urlBuiltFrom(parts)
                    : super.built(asking, from, maker);
        }

        private Value urlBuiltFrom(AnyBlockValue parts) {
            List<Value> written = parts.remaining();
            if (written.isEmpty()) {
                throw refusing(parts);
            }
            String scheme = Molder.form(written.getFirst());
            String rest = written.subList(1, written.size()).stream()
                    .map(Molder::form)
                    .collect(Collectors.joining("/"));
            return UrlValue.of(scheme + "://" + rest);
        }
    };

    UrlValue(StringStorage storage, int index) {
        super(storage, index);
    }

    public static UrlValue of(String address) {
        return new UrlValue(StringStorage.of(address), 1);
    }

    @Override
    public Datatype datatype() {
        return TYPE;
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
