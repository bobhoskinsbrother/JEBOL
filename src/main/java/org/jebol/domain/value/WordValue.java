package org.jebol.domain.value;

public final class WordValue extends AnyWordValue {

    public static final AnyWordDatatype TYPE = new AnyWordDatatype("word") {

        @Override
        public AnyWordValue spelt(String spelling, Context binding) {
            return new WordValue(spelling, binding);
        }
    };

    WordValue(String spelling, Context binding) {
        super(spelling, binding);
    }

    public static WordValue of(String spelling) {
        return new WordValue(spelling, Context.unbound());
    }

    @Override
    public Datatype datatype() {
        return TYPE;
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
