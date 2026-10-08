package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class FractionNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "fraction";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("number", Set.of(Datatype.DECIMAL)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            double whole = Comparison.asDouble(arguments.getFirst());
            return DecimalValue.of(whole - (long) whole);
        };
    }
}
