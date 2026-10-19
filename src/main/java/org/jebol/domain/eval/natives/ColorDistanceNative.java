package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.TupleValue;

import java.util.List;
import java.util.Set;

public class ColorDistanceNative extends ColourNative {

    @Override
    public String nativeName() {
        return "color-distance";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("a", Set.of(TupleValue.TYPE)),
                Parameter.of("b", Set.of(TupleValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> DecimalValue.of(measureThePerceptionDistance(
                readThreeParts((TupleValue) arguments.get(0)),
                readThreeParts((TupleValue) arguments.get(1))));
    }

    private double measureThePerceptionDistance(int[] one, int[] other) {
        long red = one[0] - other[0];
        long green = one[1] - other[1];
        long blue = one[2] - other[2];
        long meanRed = ((long) one[0] + other[0]) / 2;
        return Math.sqrt((((512 + meanRed) * red * red) >> 8)
                + 4 * green * green
                + (((767 - meanRed) * blue * blue) >> 8));
    }
}
