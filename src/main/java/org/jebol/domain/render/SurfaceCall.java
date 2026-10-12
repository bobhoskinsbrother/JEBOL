package org.jebol.domain.render;

import org.jebol.domain.value.ImageValue;

import java.util.List;
import java.util.Optional;

public sealed interface SurfaceCall {

    Placement where();

    record Filled(
            Placement where, List<PathStep> path, FillRule rule,
            Optional<Gradient> gradient, Colour colour, Transform transform,
            boolean antiAliased)
            implements SurfaceCall {

        public Filled {
            path = List.copyOf(path);
        }
    }

    record Stroked(
            Placement where, List<PathStep> path, PaintState painted, Transform transform)
            implements SurfaceCall {

        public Stroked {
            path = List.copyOf(path);
        }
    }

    record Pictured(
            Placement where, ImageValue image, double wide, double high, Transform transform)
            implements SurfaceCall {
    }

    record Written(Placement where, PlacedText text) implements SurfaceCall {
    }
}
