package org.jebol.domain.eval.definition;

import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class AtanFunction extends OneNumberFunction {

    @Override
    public String name() {
        return "atan";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("value", Set.of(Datatype.DECIMAL)));
    }

    @Override
    protected double answerFor(double quantity) {
        return Math.atan(quantity);
    }
}
