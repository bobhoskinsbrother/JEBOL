package org.jebol.domain.render;

/**
 * The area an instruction is allowed to paint in, measured from the surface:
 * absolute, and carried on every instruction rather than pushed and popped.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
public record ClipRectangle(int across, int down, int wide, int high) {

    /** The whole of a surface of this size, which is what a root starts with. */
    public static ClipRectangle coverWholeSurface(int wide, int high) {
        return new ClipRectangle(0, 0, wide, high);
    }

    /** Nothing at all, which is what an overlap with no area comes to. */
    public static ClipRectangle coverNothing() {
        return new ClipRectangle(0, 0, 0, 0);
    }

    public ClipRectangle mapThrough(Transform outer) {
        double[] topLeft = outer.mapPoint(across, down);
        double[] bottomRight = outer.mapPoint(across + wide, down + high);
        int left = (int) Math.round(Math.min(topLeft[0], bottomRight[0]));
        int top = (int) Math.round(Math.min(topLeft[1], bottomRight[1]));
        int right = (int) Math.round(Math.max(topLeft[0], bottomRight[0]));
        int bottom = (int) Math.round(Math.max(topLeft[1], bottomRight[1]));
        return new ClipRectangle(left, top, right - left, bottom - top);
    }

    public boolean isEmpty() {
        return wide <= 0 || high <= 0;
    }

    /** The part of this that is also inside the other. */
    public ClipRectangle overlapWith(ClipRectangle other) {
        int left = Math.max(across, other.across);
        int top = Math.max(down, other.down);
        int right = Math.min(across + wide, other.across + other.wide);
        int bottom = Math.min(down + high, other.down + other.high);
        if (right <= left || bottom <= top) {
            return coverNothing();
        }
        return new ClipRectangle(left, top, right - left, bottom - top);
    }
}
