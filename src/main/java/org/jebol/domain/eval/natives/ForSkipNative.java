package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.AnyWordValue;

import java.util.List;
import java.util.Set;

public class ForSkipNative extends SteppingThroughNative {

    @Override
    public String nativeName() {
        return "forskip";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.softQuoted("word"),
                Parameter.required("size", Set.of(Datatype.INTEGER, Datatype.DECIMAL)),
                Parameter.required("body", A_BLOCK));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> walkBySteps(
                evaluator,
                (AnyWordValue) arguments.get(0),
                (int) Comparison.asDouble(arguments.get(1)),
                (AnyBlockValue) arguments.get(2));
    }
}
