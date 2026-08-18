package org.jebol.domain.render;

/**
 * The area an instruction is allowed to paint in, measured from the surface.
 *
 * <p>Absolute, and carried on every instruction rather than pushed and popped
 * around them. A stack would have to survive being written down and sent
 * somewhere, and a renderer that lost its place in one would paint the rest of
 * the picture wrong with nothing to say why.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
public record ClipRectangle(int across, int down, int wide, int high) {

    /** The whole of a surface of this size, which is what a root starts with. */
    public static ClipRectangle wholeSurface(int wide, int high) {
        return new ClipRectangle(0, 0, wide, high);
    }

    /** Nothing at all, which is what an overlap with no area comes to. */
    public static ClipRectangle nothing() {
        return new ClipRectangle(0, 0, 0, 0);
    }

    public boolean isEmpty() {
        return wide <= 0 || high <= 0;
    }

    /**
     * The part of this that is also inside the other.
     *
     * <p>What narrowing down a tree comes to: a child is clipped to its
     * parent, its parent to its own parent, and so on to the root. Doing it
     * as an overlap rather than as a nested stack is what lets the answer
     * travel on its own.
     */
    public ClipRectangle overlapWith(ClipRectangle other) {
        int left = Math.max(across, other.across);
        int top = Math.max(down, other.down);
        int right = Math.min(across + wide, other.across + other.wide);
        int bottom = Math.min(down + high, other.down + other.high);
        if (right <= left || bottom <= top) {
            return nothing();
        }
        return new ClipRectangle(left, top, right - left, bottom - top);
    }
}
