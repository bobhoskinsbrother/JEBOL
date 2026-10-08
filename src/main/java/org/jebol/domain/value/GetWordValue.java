package org.jebol.domain.value;

public final class GetWordValue extends AnyWordValue {

    GetWordValue(String spelling, Context binding) {
        super(spelling, binding);
    }

    public static GetWordValue of(String spelling) {
        return new GetWordValue(spelling, Context.unbound());
    }

    @Override
    public Datatype datatype() {
        return Datatype.GET_WORD;
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
    public boolean fetchesItsValue() {
        return true;
    }

    @Override
    public ParameterKind kindOfParameterItDeclares() {
        return ParameterKind.HARD_QUOTED;
    }
}
