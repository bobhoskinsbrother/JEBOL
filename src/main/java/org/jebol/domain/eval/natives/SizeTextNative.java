package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.render.TextLines;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class SizeTextNative extends TextMeasuringNative {

    public SizeTextNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "size-text";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("gob", Set.of(GobValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            TextLines lines = theTextOf((GobValue) arguments.getFirst(), evaluator).lines();
            return inWholePixels(lines.widest(), lines.tallness());
        };
    }
}
