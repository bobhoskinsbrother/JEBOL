package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.TupleValue;

import java.util.List;
import java.util.Set;

public class HsvToRgbNative extends ColourNative {

    private static final double A_SIXTH_OF_THE_WHEEL = 255.0 / 6;

    @Override
    public String nativeName() {
        return "hsv-to-rgb";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("hsv", Set.of(TupleValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                recolouredTuple((TupleValue) arguments.getFirst(), this::hsvToRgb);
    }

    private int[] hsvToRgb(int[] hsv) {
        int saturation = hsv[1];
        if (saturation == 0) {
            return allThreeAt(hsv[2]);
        }
        double sixths = hsv[0] / A_SIXTH_OF_THE_WHEEL;
        int sector = (int) sixths;
        double into = sixths - sector;
        double whole = hsv[2] / 255.0;
        double spread = saturation / 255.0;
        int brightest = (int) (255.0 * whole);
        int dimmest = (int) (255.0 * whole * (1.0 - spread));
        int falling = (int) (255.0 * whole * (1.0 - spread * into));
        int rising = (int) (255.0 * whole * (1.0 - spread * (1.0 - into)));
        return switch (sector) {
            case 0 -> new int[] {brightest, rising, dimmest};
            case 1 -> new int[] {falling, brightest, dimmest};
            case 2 -> new int[] {dimmest, brightest, rising};
            case 3 -> new int[] {dimmest, falling, brightest};
            case 4 -> new int[] {rising, dimmest, brightest};
            default -> new int[] {brightest, dimmest, falling};
        };
    }
}
