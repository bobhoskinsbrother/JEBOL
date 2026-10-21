package org.jebol.domain.value;

public final class RefValue extends AnyStringValue {

    public static final AnyStringDatatype TYPE = new AnyStringDatatype("ref") {

        @Override
        public AnyStringValue wrap(StringStorage storage, int index) {
            return new RefValue(storage, index);
        }
    };

    private static final String LEXER_DELIMITERS = "()[]{}\"/;";

    private static final char OPENS_EMAIL_INSTEAD = '@';

    RefValue(StringStorage storage, int index) {
        super(storage, index);
    }

    public static RefValue of(String text) {
        return new RefValue(StringStorage.of(text), 1);
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    RefValue makeSameKindOver(StringStorage storage, int index) {
        return new RefValue(storage, index);
    }

    @Override
    public String mold() {
        return !Molder.WRITING_EVERYTHING_OUT.get() && canBeReadBack()
                ? "@" + text()
                : constructed();
    }

    private boolean canBeReadBack() {
        return text().codePoints().noneMatch(codepoint ->
                codepoint == OPENS_EMAIL_INSTEAD
                        || !Character.isLetterOrDigit(codepoint)
                                && (codepoint < 21
                                        || Character.isWhitespace(codepoint)
                                        || codepoint < 0x80 && LEXER_DELIMITERS
                                                .indexOf(codepoint) >= 0));
    }
}
