package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class ArctangentOfTwoSidesNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "atan2";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("y", Set.of(Datatype.DECIMAL)),
                Parameter.required("x", Set.of(Datatype.DECIMAL)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> DecimalValue.of(
                Math.atan2(Comparison.asDouble(arguments.get(0)),
                        Comparison.asDouble(arguments.get(1))));
    }
}
