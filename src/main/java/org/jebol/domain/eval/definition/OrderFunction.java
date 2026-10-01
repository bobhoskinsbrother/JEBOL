package org.jebol.domain.eval.definition;

import org.jebol.domain.value.Parameter;

import java.util.List;

public abstract class OrderFunction extends ComparisonFunction {

    @Override
    public List<Parameter> parameters() {
        return acceptsWhateverComesAlong("value1", "value2");
    }
}
