package org.jebol.domain.eval.natives;

import org.jebol.domain.value.Parameter;

import java.util.List;

public abstract class EqualityNative extends ComparisonNative {

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnyType("value1", "value2");
    }
}
