package org.jebol.domain.value;

public final class RefinementValue extends AnyWordValue {

    RefinementValue(String spelling, Context binding) {
        super(spelling, binding);
    }

    public static RefinementValue of(String spelling) {
        return new RefinementValue(spelling, Context.unbound());
    }

    @Override
    public Datatype datatype() {
        return Datatype.REFINEMENT;
    }

    @Override
    public RefinementValue boundTo(Context context) {
        return new RefinementValue(spelling(), context);
    }

    @Override
    public String mold() {
        return "/" + spelling();
    }
}
