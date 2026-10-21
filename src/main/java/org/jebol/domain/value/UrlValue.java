package org.jebol.domain.value;

import java.util.List;
import java.util.stream.Collectors;

public final class UrlValue extends AnyStringValue {

    private static final char ENDS_THE_SCHEME = ':';

    public static final AnyStringDatatype TYPE = new AnyStringDatatype("url") {

        @Override
        public AnyStringValue wrap(StringStorage storage, int index) {
            return new UrlValue(storage, index);
        }

        @Override
        protected Value build(Conversion asking, Value from, Maker maker) {
            return from instanceof AnyBlockValue parts
                    ? buildAUrlFrom(parts)
                    : super.build(asking, from, maker);
        }

        private Value buildAUrlFrom(AnyBlockValue parts) {
            List<Value> written = parts.remaining();
            if (written.isEmpty()) {
                throw Raised.of(EvaluationFailure.BAD_MAKE_ARG,
                        EmailValue.TYPE, parts);
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
    UrlValue makeTheSameKindOver(StringStorage storage, int index) {
        return new UrlValue(storage, index);
    }

    @Override
    public String mold() {
        return moldUnlessItFailsToReadBack(ENDS_THE_SCHEME);
    }

    @Override
    public boolean isALocation() {
        return true;
    }
}
