package org.jebol.domain.value;

public final class SetWordValue extends AnyWordValue {

    SetWordValue(String spelling, Context binding) {
        super(spelling, binding);
    }

    public static SetWordValue of(String spelling) {
        return new SetWordValue(spelling, Context.unbound());
    }

    @Override
    public Datatype datatype() {
        return Datatype.SET_WORD;
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
