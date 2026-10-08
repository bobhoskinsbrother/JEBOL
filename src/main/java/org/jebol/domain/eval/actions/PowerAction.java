package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class PowerAction extends DefaultNative implements ActionValue {

    @Override
    public String nativeName() {
        return "power";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsOnlyNumbers("base", "exponent");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value base = arguments.get(0);
            Value exponent = arguments.get(1);
            if (base instanceof TupleValue) {
                return refuseTheDatatype(base);
            }
            if (!Comparison.isNumeric(base) || !Comparison.isNumeric(exponent)) {
                return refuseTheArgument(base, "number");
            }
            double raised = Math.pow(
                    Comparison.asDouble(base), Comparison.asDouble(exponent));
            return bothArePercents(base, exponent)
                    ? PercentValue.of(raised)
                    : DecimalValue.of(raised);
        };
    }

    private boolean bothArePercents(Value base, Value exponent) {
        return base instanceof PercentValue && exponent instanceof PercentValue;
    }
}
