package org.jebol.domain.value;

import java.util.Optional;

public record WordValue(String spelling, String canonical, Context binding, Datatype datatype)
        implements Value {

    private static final String THE_NAME_AN_OBJECT_ANSWERS_TO_ITSELF_BY = "self";

    public WordValue {
        if (spelling == null || spelling.isEmpty()) {
            throw new IllegalArgumentException("a word needs a spelling");
        }
        if (binding == null) {
            throw new IllegalArgumentException(
                    "an unbound word carries Context.unbound(), never null");
        }
        if (!datatype.isAnyWord()) {
            throw new IllegalArgumentException(
                    datatype.literalSpelling() + " is not an any-word! datatype");
        }
        if (!canonical.equals(Context.canonicalise(spelling))) {
            throw new IllegalArgumentException(
                    "canonical \"" + canonical + "\" does not match spelling \""
                            + spelling + "\"");
        }
    }

    public static WordValue of(String spelling) {
        return of(spelling, Datatype.WORD);
    }

    public static WordValue of(String spelling, Datatype datatype) {
        return new WordValue(
                spelling, Context.canonicalise(spelling), Context.unbound(), datatype);
    }

    @Override
    public boolean equalTo(Value other, Sameness how) {
        return other instanceof WordValue theirs && namesSameAs(theirs);
    }

    @Override
    public Optional<Value[]> broughtTogetherWith(Value other) {
        return other.datatype().isAnyWord() ? both(this, other) : Optional.empty();
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

    public WordValue boundTo(Context context) {
        return new WordValue(spelling, canonical, context, datatype);
    }

    public ContextSlot boundSlot() {
        if (!isBound() || !binding.knows(canonical)) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, spelling);
        }
        return binding.slotFor(canonical);
    }

    public WordValue as(Datatype otherDatatype) {
        return new WordValue(spelling, canonical, binding, otherDatatype);
    }

    public boolean namesSameAs(WordValue other) {
        return canonical.equals(other.canonical);
    }

    public boolean isSameAs(WordValue other) {
        return equals(other)
                && (other.binding == binding || shareAFunctionsFrames(other));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WordValue word
                && word.datatype == datatype
                && word.spelling.equals(spelling);
    }

    @Override
    public int hashCode() {
        return datatype.hashCode() * 31 + spelling.hashCode();
    }

    @Override
    public String toString() {
        return switch (datatype) {
            case SET_WORD -> spelling + ":";
            case GET_WORD -> ":" + spelling;
            case LIT_WORD -> "'" + spelling;
            case REFINEMENT -> "/" + spelling;
            case ISSUE -> "#" + spelling;
            default -> spelling;
        };
    }

    private boolean shareAFunctionsFrames(WordValue other) {
        Value ours = binding.functionOwningThisFrame();
        return ours != null && ours == other.binding.functionOwningThisFrame();
    }
}
