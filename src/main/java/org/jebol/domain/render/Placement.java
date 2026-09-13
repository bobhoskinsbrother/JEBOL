package org.jebol.domain.render;

/**
 * Where one thing goes, and how much of it shows through: the position measured
 * from the surface, the area it may paint in, and the opacity of every gob
 * between it and the root multiplied together.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
public record Placement(
        int across, int down, int wide, int high,
        ClipRectangle clip, int opacity, java.util.List<PathStep> clipShape) {

    /** Fully opaque. What a gob starts at and what most of them stay at. */
    public static final int OPAQUE = 255;

    public Placement {
        opacity = Math.clamp(opacity, 0, OPAQUE);
        clipShape = java.util.List.copyOf(clipShape);
    }

    /** Clipped to a rectangle and nothing finer, which is the ordinary case. */
    public Placement(
            int across, int down, int wide, int high, ClipRectangle clip, int opacity) {
        this(across, down, wide, high, clip, opacity, java.util.List.of());
    }

    /** The same place, narrowed to the inside of a shape as well. */
    public Placement insideTheShape(java.util.List<PathStep> shape) {
        return new Placement(across, down, wide, high, clip, opacity, shape);
    }

    /** Whether anything of this would show at all. */
    public boolean showsNothing() {
        return wide <= 0 || high <= 0 || opacity <= 0 || clip.isEmpty();
    }
}
