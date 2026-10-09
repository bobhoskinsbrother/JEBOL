package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.ImageOperations;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class BlurNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "blur";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("image", Set.of(ImageValue.TYPE)),
                Parameter.required("radius", TypesetValue.NUMBER.members()));
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
