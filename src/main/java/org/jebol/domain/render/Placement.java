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
        ClipRectangle clip, int opacity) {

    /** Fully opaque. What a gob starts at and what most of them stay at. */
    public static final int OPAQUE = 255;

    public Placement {
        opacity = Math.clamp(opacity, 0, OPAQUE);
    }

    /** Whether anything of this would show at all. */
    public boolean showsNothing() {
        return wide <= 0 || high <= 0 || opacity <= 0 || clip.isEmpty();
    }
}
