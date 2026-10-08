package org.jebol.domain.value;

public final class GetWordValue extends AnyWordValue {

    public static final AnyWordDatatype TYPE = new AnyWordDatatype("get-word") {

        @Override
        public AnyWordValue spelt(String spelling, Context binding) {
            return new GetWordValue(spelling, binding);
        }
    };

    GetWordValue(String spelling, Context binding) {
        super(spelling, binding);
    }

    public static GetWordValue of(String spelling) {
        return new GetWordValue(spelling, Context.unbound());
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    public GetWordValue boundTo(Context context) {
        return new GetWordValue(spelling(), context);
    }

    @Override
    public String mold() {
        return ":" + spelling();
    }

    @Override
    public boolean looksUpItsDeclaration() {
        return true;
    }

    @Override
    public ParameterKind kindOfParameterItDeclares() {
        return ParameterKind.HARD_QUOTED;
    }
}
