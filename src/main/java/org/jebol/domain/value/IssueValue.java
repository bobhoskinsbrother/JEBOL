package org.jebol.domain.value;

public final class IssueValue extends AnyWordValue {

    IssueValue(String spelling, Context binding) {
        super(spelling, binding);
    }

    public static IssueValue of(String spelling) {
        return new IssueValue(spelling, Context.unbound());
    }

    @Override
    public Datatype datatype() {
        return Datatype.ISSUE;
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
