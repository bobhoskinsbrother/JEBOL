package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Arithmetic;

public class TangentNative extends TrigonometryNative {

    @Override
    public String nativeName() {
        return "tangent";
    }

    @Override
    protected double ratioOf(double radians) {
        if (Arithmetic.nearlyTheSame(Math.abs(radians), Math.PI / 2.0)) {
            return radians < 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        return Math.tan(radians);
    }
}
