package org.jebol.domain.render;

import org.jebol.domain.value.ImageValue;

import java.util.List;
import java.util.Optional;

public sealed interface DrawingCommand {

    Placement where();

    record Fill(
            Placement where, List<PathStep> path, FillRule rule,
            Optional<Gradient> gradient, Colour colour, Transform transform,
            boolean antiAliased)
            implements DrawingCommand {

        public Fill {
            path = List.copyOf(path);
        }
    }

    record Stroke(
            Placement where, List<PathStep> path, PaintState painted, Transform transform)
            implements DrawingCommand {

        public Stroke {
            path = List.copyOf(path);
        }
    }

    record Picture(
            Placement where, ImageValue image, double wide, double high, Transform transform)
            implements DrawingCommand {
    }

    record Text(Placement where, PlacedText text, Transform transform) implements DrawingCommand {
    }
}
