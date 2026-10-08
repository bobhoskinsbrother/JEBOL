package org.jebol.domain.value;

public final class RefValue extends AnyStringValue {

    private static final String LEXER_DELIMITERS = "()[]{}\"/;";

    private static final char OPENS_AN_EMAIL_INSTEAD = '@';

    RefValue(StringStorage storage, int index) {
        super(storage, index);
    }

    public static RefValue of(String text) {
        return new RefValue(StringStorage.of(text), 1);
    }

    @Override
    public Datatype datatype() {
        return Datatype.REF;
    }

    @Override
    RefValue sameKindOver(StringStorage storage, int index) {
        return new RefValue(storage, index);
    }

    @Override
    public String mold() {
        return !Molder.WRITING_EVERYTHING_OUT.get() && theLexerWouldReadItBack()
                ? "@" + text()
                : constructed();
    }

    private boolean theLexerWouldReadItBack() {
        return text().codePoints().noneMatch(codepoint ->
                codepoint == OPENS_AN_EMAIL_INSTEAD
                        || !Character.isLetterOrDigit(codepoint)
                                && (codepoint < 21
                                        || Character.isWhitespace(codepoint)
                                        || codepoint < 0x80 && LEXER_DELIMITERS
                                                .indexOf(codepoint) >= 0));
    }
}
