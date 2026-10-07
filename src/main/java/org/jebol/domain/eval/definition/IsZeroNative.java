package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.BitsetActions;
import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

import java.util.Arrays;
import java.util.List;

public class IsZeroNative extends DefaultNative {

    @Override
    public String name() {
        return "zero?";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsAnyType("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                LogicValue.of(isTheZeroOfItsDatatype(arguments.getFirst()));
    }

    private boolean isTheZeroOfItsDatatype(Value value) {
        return switch (value) {
            case BitsetValue members -> new BitsetActions(members).isTheEmptySet();
            case PairValue(double x, double y) -> x == 0 && y == 0;
            case TupleValue segments -> Arrays.stream(segments.segments())
                    .allMatch(part -> part == 0);
            case CharacterValue(int codepoint) -> codepoint == 0;
            case TimeValue(long nanoseconds) -> nanoseconds == 0;
            case MoneyValue amount -> amount.asDeci().isZero();
            default -> Comparison.isNumeric(value) && Comparison.asDouble(value) == 0.0;
        };
    }
}
