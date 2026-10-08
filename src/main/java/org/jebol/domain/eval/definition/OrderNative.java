package org.jebol.domain.eval.definition;

import org.jebol.domain.value.Parameter;

import java.util.List;

public abstract class OrderNative extends ComparisonNative {

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWhateverComesAlong("value1", "value2");
    }
}
