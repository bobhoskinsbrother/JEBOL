package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.TypesetValue;

import java.util.List;
import java.util.Set;

public class TintNative extends ColourNative {

    @Override
    public String nativeName() {
        return "tint";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("target", acceptAColourOrAnImage()),
                Parameter.of("rgb", Set.of(TupleValue.TYPE)),
                Parameter.of("amount", TypesetValue.NUMBER.members()));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            int[] mixture = readThreeParts((TupleValue) arguments.get(1));
            double amount = Comparison.asDouble(arguments.get(2));
            return recolourEveryColour(arguments.getFirst(),
                    colour -> recolourTheTuple(colour, parts -> tint(parts, mixture, amount)),
                    parts -> tint(parts, mixture, amount));
        };
    }

    private int[] tint(int[] target, int[] mixture, double amount) {
        double towards = Math.clamp(amount, 0.0, 1.0);
        double away = 1.0 - towards;
        int[] mixed = new int[3];
        for (int part = 0; part < 3; part++) {
            double from = target[part];
            double to = mixture[part];
            double moved = from >= to
                    ? to + ((from - to) * away)
                    : from + ((to - from) * towards);
            mixed[part] = Math.clamp((int) (0.5 + moved), 0, 255);
        }
        return mixed;
    }
}
