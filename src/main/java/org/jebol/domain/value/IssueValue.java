package org.jebol.domain.value;

public final class IssueValue extends AnyWordValue {

    public static final AnyWordDatatype TYPE = new AnyWordDatatype("issue") {

        @Override
        public AnyWordValue spelt(String spelling, Context binding) {
            return new IssueValue(spelling, binding);
        }

        @Override
        String asTheScannerReadsIt(String spelling) {
            return "#" + spelling;
        }

        @Override
        Datatype theDatatypeTheScannerReadsItAs() {
            return this;
        }
    };

    IssueValue(String spelling, Context binding) {
        super(spelling, binding);
    }

    public static IssueValue of(String spelling) {
        return new IssueValue(spelling, Context.unbound());
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    public IssueValue boundTo(Context context) {
        return new IssueValue(spelling(), context);
    }

    @Override
    public String mold() {
        return "#" + spelling();
    }
}
