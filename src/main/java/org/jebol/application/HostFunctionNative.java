package org.jebol.application;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;

import java.util.ArrayList;
import java.util.List;

public final class HostFunctionNative extends DefaultNative {

    private final String nativeName;
    private final int arity;
    private final RefinedCallable behaviour;

    public HostFunctionNative(String nativeName, int arity, RefinedCallable behaviour) {
        this.nativeName = nativeName;
        this.arity = arity;
        this.behaviour = behaviour;
    }

    @Override
    public String nativeName() {
        return nativeName;
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        List<Parameter> parameters = new ArrayList<>(arity);
        for (int position = 1; position <= arity; position++) {
            parameters.add(Parameter.required("argument" + position));
        }
        return parameters;
    }

    @Override
    public RefinedCallable behaviour() {
        return behaviour;
    }
}
