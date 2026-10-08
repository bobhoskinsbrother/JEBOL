package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.ImageOperations;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class PremultiplyNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "premultiply";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("image", Set.of(ImageValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            ImageOperations.premultiply((ImageValue) arguments.getFirst());
            return arguments.getFirst();
        };
    }
}
