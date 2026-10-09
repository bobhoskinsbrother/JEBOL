package org.jebol.domain.render;

public record TextExtent(double wide, double ascent, double descent) {

    public double high() {
        return ascent + descent;
    }
}
