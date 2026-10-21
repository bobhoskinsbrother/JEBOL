package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public abstract class CaseChangeNative extends DefaultNative {

    abstract int change(int codepoint);

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("string", TypesetValue.ANY_STRING.membersAnd(CharacterValue.TYPE)),
                Parameter.belongTo("part", "length",
                        TypesetValue.NUMBER.membersAnd(TypesetValue.ANY_STRING.members().toArray(Datatype[]::new))));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("part");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case CharacterValue letter -> CharacterValue.of(change(letter.codepoint()));
            case AnyStringValue text when refinements.contains("part") -> partChanged(text,
                    refinementArgument("part", 0, arguments, refinements).orElseThrow());
            case AnyStringValue text -> text.rewriteFromHere(this::changeText);
            case Value anythingElse -> refuseDatatype(anythingElse);
        };
    }

    private String changeText(String text) {
        StringBuilder changed = new StringBuilder();
        text.codePoints().map(this::change).forEach(changed::appendCodePoint);
        return changed.toString();
    }

    private Value partChanged(AnyStringValue text, Value limit) {
        long wanted = countHowManyAskedOf(text, limit);
        AnyStringValue changingFrom = (AnyStringValue) text.reachBackIfNegative(wanted);
        int changing = wanted < 0
                ? text.index() - changingFrom.index()
                : (int) Math.min(wanted, text.lengthFromHere());
        changingFrom.rewriteFront(changing, this::changeText);
        return text;
    }

    private long countHowManyAskedOf(AnyStringValue text, Value limit) {
        return switch (limit) {
            case IntegerValue whole -> whole.magnitude();
            case DecimalValue fraction ->
                    (long) fraction.quantity();
            case AnyStringValue upTo when upTo.sharesStorageWith(text) -> upTo.index() - text.index();
            default -> throw Raised.of(EvaluationFailure.INVALID_PART, limit);
        };
    }
}
