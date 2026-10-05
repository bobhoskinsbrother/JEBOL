package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.ImageOperations;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class PremultiplyNative extends DefaultNative {

    @Override
    public String name() {
        return "premultiply";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("image", Set.of(Datatype.IMAGE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            ImageOperations.premultiply((ImageValue) arguments.getFirst());
            return arguments.getFirst();
        };
    }
}
