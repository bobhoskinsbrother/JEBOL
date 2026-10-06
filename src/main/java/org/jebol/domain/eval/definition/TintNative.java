package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Typeset;

import java.util.List;
import java.util.Set;

public class TintNative extends ColourNative {

    @Override
    public String name() {
        return "tint";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("target", aColourOrAnImage()),
                Parameter.required("rgb", Set.of(Datatype.TUPLE)),
                Parameter.required("amount", Typeset.NUMBER.members()));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            int[] mixture = threeParts((TupleValue) arguments.get(1));
            double amount = Comparison.asDouble(arguments.get(2));
            return overEveryColour(arguments.getFirst(),
                    colour -> recolouredTuple(colour, parts -> tinted(parts, mixture, amount)),
                    parts -> tinted(parts, mixture, amount));
        };
    }

    private int[] tinted(int[] target, int[] mixture, double amount) {
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
