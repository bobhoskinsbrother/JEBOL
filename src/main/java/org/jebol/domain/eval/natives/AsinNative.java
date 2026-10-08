package org.jebol.domain.eval.natives;

import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class AsinNative extends OneNumberNative {

    @Override
    public String nativeName() {
        return "asin";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("value", Set.of(DecimalValue.TYPE)));
    }

    @Override
    protected double answerFor(double quantity) {
        return Math.asin(quantity);
    }
}
