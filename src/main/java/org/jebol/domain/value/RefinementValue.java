package org.jebol.domain.value;

public final class RefinementValue extends AnyWordValue {

    public static final AnyWordDatatype TYPE = new AnyWordDatatype("refinement") {

        @Override
        public AnyWordValue spelt(String spelling, Context binding) {
            return new RefinementValue(spelling, binding);
        }
    };

    RefinementValue(String spelling, Context binding) {
        super(spelling, binding);
    }

    public static RefinementValue of(String spelling) {
        return new RefinementValue(spelling, Context.unbound());
    }

    @Override
    public Datatype datatype() {
        return TYPE;
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
