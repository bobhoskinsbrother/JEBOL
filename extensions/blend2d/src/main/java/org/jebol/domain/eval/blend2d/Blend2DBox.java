package org.jebol.domain.eval.blend2d;

import java.util.Optional;

record Blend2DBox(double x0, double y0, double x1, double y1) {

    static Blend2DBox fromRect(double x, double y, double wide, double high) {
        return new Blend2DBox(x, y, x + wide, y + high);
    }

    Optional<Blend2DBox> intersectWith(Blend2DBox other) {
        Blend2DBox overlap = new Blend2DBox(Math.max(x0, other.x0), Math.max(y0, other.y0),
                Math.min(x1, other.x1), Math.min(y1, other.y1));
        return overlap.x0 < overlap.x1 && overlap.y0 < overlap.y1 ? Optional.of(overlap) : Optional.empty();
    }

    Blend2DBox scaledBy(double factor) {
        return new Blend2DBox(x0 * factor, y0 * factor, x1 * factor, y1 * factor);
    }

    int[] truncate() {
        return new int[] {(int) x0, (int) y0, (int) x1, (int) y1};
    }
}
