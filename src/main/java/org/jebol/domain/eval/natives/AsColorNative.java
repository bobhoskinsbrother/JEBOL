package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class AsColorNative extends ArithmeticNative {

    private static final double ROUNDING_HALF = 0.5;

    private static final int BRIGHTEST_CHANNEL = 255;

    @Override
    public String nativeName() {
        return "as-color";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsOnlyNumbers("r", "g", "b");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> TupleValue.of(
                roundChannel(arguments.get(0)),
                roundChannel(arguments.get(1)),
                roundChannel(arguments.get(2)));
    }

    private int roundChannel(Value given) {
        double number = switch (given) {
            case IntegerValue whole -> whole.magnitude();
            case PercentValue portion -> portion.quantity() * BRIGHTEST_CHANNEL + ROUNDING_HALF;
            case DecimalValue fraction -> fraction.quantity() + ROUNDING_HALF;
            default -> 0;
        };
        return Math.clamp((int) number, 0, BRIGHTEST_CHANNEL);
    }
}
