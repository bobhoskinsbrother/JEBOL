package org.jebol.domain.value;

public final class LitWordValue extends AnyWordValue {

    LitWordValue(String spelling, Context binding) {
        super(spelling, binding);
    }

    public static LitWordValue of(String spelling) {
        return new LitWordValue(spelling, Context.unbound());
    }

    @Override
    public Datatype datatype() {
        return Datatype.LIT_WORD;
    }

    @Override
    public LitWordValue boundTo(Context context) {
        return new LitWordValue(spelling(), context);
    }

    @Override
    public String mold() {
        return "'" + spelling();
    }

    @Override
    public ParameterKind kindOfParameterItDeclares() {
        return ParameterKind.SOFT_QUOTED;
    }}
