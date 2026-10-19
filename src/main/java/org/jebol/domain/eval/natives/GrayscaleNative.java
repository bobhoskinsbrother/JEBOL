package org.jebol.domain.eval.natives;

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
        return List.of(Parameter.of("target", acceptAColourOrAnImage()));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> recolourEveryColour(arguments.getFirst(),
                colour -> IntegerValue.of(measureTheGrey(readThreeParts(colour))),
                parts -> setAllThreeTo(measureTheGrey(parts)));
    }

    private int measureTheGrey(int[] parts) {
        return (parts[0] + parts[1] + parts[2]) / 3;
    }
}
