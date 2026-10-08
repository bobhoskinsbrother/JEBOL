package org.jebol.domain.eval.natives;

import org.jebol.domain.value.EventValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class DoCallbackNative extends ExtensionPointNative {

    @Override
    public String nativeName() {
        return "do-callback";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("event", Set.of(EventValue.TYPE)));
    }
}
