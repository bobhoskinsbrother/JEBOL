package org.jebol.domain.value;

import java.util.Optional;

public abstract sealed class AnyWordValue implements Value
        permits WordValue, SetWordValue, GetWordValue, LitWordValue, RefinementValue, IssueValue {

    private static final String THE_NAME_AN_OBJECT_ANSWERS_TO_ITSELF_BY = "self";

    private final String spelling;
    private final String canonical;
    private final Context binding;

    AnyWordValue(String spelling, Context binding) {
        if (spelling == null || spelling.isEmpty()) {
            throw new IllegalArgumentException("a word needs a spelling");
        }
        if (binding == null) {
            throw new IllegalArgumentException(
                    "an unbound word carries Context.unbound(), never null");
        }
        this.spelling = spelling;
        this.canonical = Context.canonicalise(spelling);
        this.binding = binding;
    }

    @Override
    public abstract Datatype datatype();

    public abstract AnyWordValue boundTo(Context context);

    public abstract String mold();

    public String form() {
        return spelling;
    }

    public boolean fetchesItsValue() {
        return false;
    }

    public ParameterKind kindOfParameterItDeclares() {
        return ParameterKind.NORMAL;
    }

    public static AnyWordValue ofTheDatatype(String spelling, Datatype datatype) {
        return ofTheDatatype(spelling, Context.unbound(), datatype);
    }

    private static AnyWordValue ofTheDatatype(String spelling, Context binding, Datatype datatype) {
        return switch (datatype) {
            case WORD -> new WordValue(spelling, binding);
            case SET_WORD -> new SetWordValue(spelling, binding);
            case GET_WORD -> new GetWordValue(spelling, binding);
            case LIT_WORD -> new LitWordValue(spelling, binding);
            case REFINEMENT -> new RefinementValue(spelling, binding);
            case ISSUE -> new IssueValue(spelling, binding);
            default -> throw new IllegalArgumentException(
                    datatype.literalSpelling() + " is not an any-word! datatype");
        };
    }

    public String spelling() {
        return spelling;
    }

    public String canonical() {
        return canonical;
    }

    public Context binding() {
        return binding;
    }

    @Override
    public boolean equalTo(Value other, Sameness how) {
        return other instanceof AnyWordValue theirs && namesSameAs(theirs);
    }

    @Override
    public Optional<Value[]> broughtTogetherWith(Value other) {
        return other instanceof AnyWordValue ? both(this, other) : Optional.empty();
    }

    @Override
    public void refuseToBeWrittenWhenItNamesSelf() {
        if (canonical.equals(THE_NAME_AN_OBJECT_ANSWERS_TO_ITSELF_BY)) {
            throw Raised.of(EvaluationFailure.SELF_PROTECTED);
        }
    }

    public boolean isBound() {
        return !binding.isUnbound();
    }

    public ContextSlot boundSlot() {
        if (!isBound() || !binding.knows(canonical)) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, spelling);
        }
        return binding.slotFor(canonical);
    }

    public AnyWordValue as(Datatype otherDatatype) {
        return ofTheDatatype(spelling, binding, otherDatatype);
    }

    public WordValue asWord() {
        return new WordValue(spelling, binding);
    }

    public SetWordValue asSetWord() {
        return new SetWordValue(spelling, binding);
    }

    public boolean namesSameAs(AnyWordValue other) {
        return canonical.equals(other.canonical);
    }

    public boolean isSameAs(AnyWordValue other) {
        return equals(other)
                && (other.binding == binding || shareAFunctionsFrames(other));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AnyWordValue word
                && word.datatype() == datatype()
                && word.spelling.equals(spelling);
    }

    @Override
    public int hashCode() {
        return datatype().hashCode() * 31 + spelling.hashCode();
    }

    @Override
    public String toString() {
        return mold();
    }

    private boolean shareAFunctionsFrames(AnyWordValue other) {
        Value ours = binding.functionOwningThisFrame();
        return ours != null && ours == other.binding.functionOwningThisFrame();
    }
}
