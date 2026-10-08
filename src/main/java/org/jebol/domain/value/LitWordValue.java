package org.jebol.domain.value;

public final class LitWordValue extends AnyWordValue {

    public static final AnyWordDatatype TYPE = new AnyWordDatatype("lit-word") {

        @Override
        public AnyWordValue spelt(String spelling, Context binding) {
            return new LitWordValue(spelling, binding);
        }
    };

    LitWordValue(String spelling, Context binding) {
        super(spelling, binding);
    }

    public static LitWordValue of(String spelling) {
        return new LitWordValue(spelling, Context.unbound());
    }

    @Override
    public Datatype datatype() {
        return TYPE;
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
