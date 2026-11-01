package org.jebol.domain.eval.blend2d;

public sealed interface Blend2DPaint permits Blend2DGradient, Blend2DPattern {

    int premultipliedAt(double x, double y);
}
