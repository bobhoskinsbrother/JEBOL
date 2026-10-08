package org.jebol.domain.value;

public final class WordValue extends AnyWordValue {

    WordValue(String spelling, Context binding) {
        super(spelling, binding);
    }

    public static WordValue of(String spelling) {
        return new WordValue(spelling, Context.unbound());
    }

    @Override
    public Datatype datatype() {
        return Datatype.WORD;
    }

    @Override
    public WordValue boundTo(Context context) {
        return new WordValue(spelling(), context);
    }

    @Override
    public String mold() {
        return spelling();
    }

    @Override
    public boolean looksUpItsDeclaration() {
        return true;
    }
}
