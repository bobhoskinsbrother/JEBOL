package org.jebol.domain.value;

import java.util.List;
import java.util.stream.Collectors;

public final class EmailValue extends AnyStringValue {

    private static final char SEPARATES_THE_USER_FROM_THE_HOST = '@';

    public static final AnyStringDatatype TYPE = new AnyStringDatatype("email") {

        @Override
        public AnyStringValue holding(StringStorage storage, int index) {
            return new EmailValue(storage, index);
        }

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            return from instanceof AnyBlockValue parts
                    ? addressBuiltFrom(parts)
                    : super.built(asking, from, maker);
        }

        private Value addressBuiltFrom(AnyBlockValue parts) {
            List<Value> written = parts.remaining();
            if (written.isEmpty()) {
                throw refusing(parts);
            }
            String user = Molder.form(written.getFirst());
            if (written.size() == 1) {
                return EmailValue.of(user);
            }
            String host = written.subList(1, written.size()).stream()
                    .map(Molder::form)
                    .collect(Collectors.joining("."));
            return EmailValue.of(user + SEPARATES_THE_USER_FROM_THE_HOST + host);
        }
    };

    EmailValue(StringStorage storage, int index) {
        super(storage, index);
    }

    public static EmailValue of(String address) {
        return new EmailValue(StringStorage.of(address), 1);
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    EmailValue sameKindOver(StringStorage storage, int index) {
        return new EmailValue(storage, index);
    }

    @Override
    public String mold() {
        return moldedUnlessItWouldNotReadBackWhenMarkedBy(SEPARATES_THE_USER_FROM_THE_HOST);
    }

    public Value user() {
        String whole = head().text();
        int at = whole.indexOf(SEPARATES_THE_USER_FROM_THE_HOST);
        return StringValue.of(at < 0 ? whole : whole.substring(0, at));
    }

    public Value host() {
        String whole = head().text();
        int at = whole.indexOf(SEPARATES_THE_USER_FROM_THE_HOST);
        return at < 0 ? NoneValue.none() : StringValue.of(whole.substring(at + 1));
    }

    public void userRewrittenAs(String replacement) {
        String whole = text();
        int at = whole.indexOf(SEPARATES_THE_USER_FROM_THE_HOST);
        replaceEverythingWith(replacement + (at < 0 ? "" : whole.substring(at)));
    }

    public void hostRewrittenAs(String replacement) {
        String whole = text();
        int at = whole.indexOf(SEPARATES_THE_USER_FROM_THE_HOST);
        replaceEverythingWith(at < 0
                ? whole + SEPARATES_THE_USER_FROM_THE_HOST + replacement
                : whole.substring(0, at + 1) + replacement);
    }

    private void replaceEverythingWith(String rebuilt) {
        StringStorage storage = storage();
        while (storage.length() > 0) {
            storage.removeAt(1);
        }
        rebuilt.codePoints().forEach(storage::append);
    }
}
