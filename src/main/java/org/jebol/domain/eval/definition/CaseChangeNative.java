package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public abstract class CaseChangeNative extends DefaultNative {

    abstract int changed(int codepoint);

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("string", Typeset.ANY_STRING.membersAnd(Datatype.CHAR)),
                Parameter.belongingTo("part", "length",
                        Typeset.NUMBER.membersAnd(Typeset.ANY_STRING.members().toArray(Datatype[]::new))));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("part");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case CharacterValue letter -> CharacterValue.of(changed(letter.codepoint()));
            case StringValue text when refinements.contains("part") -> partChanged(text,
                    argumentOf("part", 0, arguments, refinements).orElseThrow());
            case StringValue text -> text.rewrittenFromHere(this::changedText);
            case Value anythingElse -> refuseTheDatatype(anythingElse);
        };
    }

    private String changedText(String text) {
        StringBuilder changed = new StringBuilder();
        text.codePoints().map(this::changed).forEach(changed::appendCodePoint);
        return changed.toString();
    }

    private Value partChanged(StringValue text, Value limit) {
        long wanted = howManyAskedOf(text, limit);
        StringValue changingFrom = (StringValue) text.reachingBackIfNegative(wanted);
        int changing = wanted < 0
                ? text.index() - changingFrom.index()
                : (int) Math.min(wanted, text.lengthFromHere());
        changingFrom.frontRewritten(changing, this::changedText);
        return text;
    }

    private long howManyAskedOf(StringValue text, Value limit) {
        return switch (limit) {
            case IntegerValue whole -> whole.magnitude();
            case DecimalValue fraction when fraction.datatype() == Datatype.DECIMAL ->
                    (long) fraction.quantity();
            case StringValue upTo when upTo.sharesStorageWith(text) -> upTo.index() - text.index();
            default -> throw Raised.of(EvaluationFailure.INVALID_PART, limit);
        };
    }
}
