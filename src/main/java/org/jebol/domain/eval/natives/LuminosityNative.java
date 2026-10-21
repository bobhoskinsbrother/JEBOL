package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class LuminosityNative extends ColourNative {

    @Override
    public String nativeName() {
        return "luminosity";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("target", acceptAColourOrAnImage()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("luma");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            boolean luma = refinements.contains("luma");
            return recolourEveryColour(arguments.getFirst(),
                    colour -> IntegerValue.of(measureLuminosityTruncated(readThreeParts(colour), luma)),
                    parts -> setAllThreeTo(measureLuminosityTruncated(parts, luma)));
        };
    }

    private int measureLuminosityTruncated(int[] parts, boolean luma) {
        return luma
                ? (int) ((0.299 * parts[0]) + (0.587 * parts[1]) + (0.114 * parts[2]))
                : (int) ((0.2126 * parts[0]) + (0.7152 * parts[1]) + (0.0722 * parts[2]));
    }
}
