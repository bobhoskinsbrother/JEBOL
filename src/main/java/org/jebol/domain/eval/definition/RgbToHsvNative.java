package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.TupleValue;

import java.util.List;
import java.util.Set;

public class RgbToHsvNative extends ColourNative {

    private static final double A_SIXTH_OF_THE_WHEEL = 42.5;

    private static final double GREEN_STARTS_AT = 85.0;

    private static final double BLUE_STARTS_AT = 170.0;

    @Override
    public String nativeName() {
        return "rgb-to-hsv";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("rgb", Set.of(Datatype.TUPLE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                recolouredTuple((TupleValue) arguments.getFirst(), this::rgbToHsv);
    }

    private int[] rgbToHsv(int[] rgb) {
        int red = rgb[0];
        int green = rgb[1];
        int blue = rgb[2];
        int largest = Math.max(red, Math.max(green, blue));
        int smallest = Math.min(red, Math.min(green, blue));
        if (largest == 0 || largest == smallest) {
            return new int[] {0, 0, largest};
        }
        double spread = largest - smallest;
        int saturation = (int) (255.0 * spread / largest);
        double hue;
        if (largest == red) {
            hue = A_SIXTH_OF_THE_WHEEL * (green - blue) / spread;
        } else if (largest == green) {
            hue = GREEN_STARTS_AT + A_SIXTH_OF_THE_WHEEL * (blue - red) / spread;
        } else {
            hue = BLUE_STARTS_AT + A_SIXTH_OF_THE_WHEEL * (red - green) / spread;
        }
        return new int[] {aNegativeHueWrapsRatherThanClamping(hue), saturation, largest};
    }

    private int aNegativeHueWrapsRatherThanClamping(double hue) {
        return (int) hue & 0xFF;
    }
}
