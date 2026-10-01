package org.jebol.domain.eval.definition;

import org.jebol.domain.value.Parameter;

import java.util.List;

public abstract class EqualityNative extends ComparisonNative {

    @Override
    public List<Parameter> parameters() {
        return acceptsAnyType("value1", "value2");
    }
}
