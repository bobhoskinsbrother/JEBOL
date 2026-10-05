package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.ImageOperations;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Typeset;

import java.util.List;
import java.util.Set;

public class BlurNative extends DefaultNative {

    @Override
    public String name() {
        return "blur";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("image", Set.of(Datatype.IMAGE)),
                Parameter.required("radius", Typeset.NUMBER.members()));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            ImageOperations.blur((ImageValue) arguments.getFirst(),
                    (int) Math.round(Arithmetic.asMagnitude(arguments.get(1))));
            return arguments.getFirst();
        };
    }
}
