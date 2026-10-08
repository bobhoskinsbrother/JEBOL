package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class GrayscaleNative extends ColourNative {

    @Override
    public String nativeName() {
        return "grayscale";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("target", aColourOrAnImage()));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> overEveryColour(arguments.getFirst(),
                colour -> IntegerValue.of(grey(threeParts(colour))),
                parts -> allThreeAt(grey(parts)));
    }

    private int grey(int[] parts) {
        return (parts[0] + parts[1] + parts[2]) / 3;
    }
}
