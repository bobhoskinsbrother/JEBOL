package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

import java.util.List;

public class AsColorNative extends DefaultNative {

    private static final double A_HALF_THAT_ROUNDS_UP = 0.5;

    private static final int THE_BRIGHTEST_CHANNEL = 255;

    @Override
    public String name() {
        return "as-color";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsOnlyNumbers("r", "g", "b");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> TupleValue.of(
                aChannelRoundingNotTruncating(arguments.get(0)),
                aChannelRoundingNotTruncating(arguments.get(1)),
                aChannelRoundingNotTruncating(arguments.get(2)));
    }

    private int aChannelRoundingNotTruncating(Value given) {
        double number = switch (given) {
            case IntegerValue whole -> whole.magnitude();
            case DecimalValue fraction -> fraction.datatype() == Datatype.PERCENT
                    ? fraction.quantity() * THE_BRIGHTEST_CHANNEL + A_HALF_THAT_ROUNDS_UP
                    : fraction.quantity() + A_HALF_THAT_ROUNDS_UP;
            default -> 0;
        };
        return Math.clamp((int) number, 0, THE_BRIGHTEST_CHANNEL);
    }
}
