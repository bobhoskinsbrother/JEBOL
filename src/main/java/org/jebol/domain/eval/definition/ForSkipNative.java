package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Set;

public class ForSkipNative extends SteppingThroughNative {

    @Override
    public String name() {
        return "forskip";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.softQuoted("word"),
                Parameter.required("size", Set.of(Datatype.INTEGER, Datatype.DECIMAL)),
                Parameter.required("body", A_BLOCK));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> walkBySteps(
                evaluator,
                (WordValue) arguments.get(0),
                (int) Comparison.asDouble(arguments.get(1)),
                (BlockValue) arguments.get(2));
    }
}
