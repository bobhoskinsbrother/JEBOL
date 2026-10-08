package org.jebol.domain.value;

public final class SetWordValue extends AnyWordValue {

    public static final AnyWordDatatype TYPE = new AnyWordDatatype("set-word") {

        @Override
        public AnyWordValue spelt(String spelling, Context binding) {
            return new SetWordValue(spelling, binding);
        }
    };

    SetWordValue(String spelling, Context binding) {
        super(spelling, binding);
    }

    public static SetWordValue of(String spelling) {
        return new SetWordValue(spelling, Context.unbound());
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    public SetWordValue boundTo(Context context) {
        return new SetWordValue(spelling(), context);
    }

    @Override
    public String mold() {
        return spelling() + ":";
    }
}
