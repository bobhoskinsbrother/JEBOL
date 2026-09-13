package org.jebol.domain.render;

import org.jebol.domain.value.PairValue;

/**
 * Which ends of an open path carry an arrowhead.
 *
 * <p>The dialect writes it as a pair of flags, one for each end of the line.
 * A head is built into the path itself rather than asked of a renderer, so
 * this is read while the path is being made and does not reach one.
 *
 * <p>Specified in {@code spec/draw.allium}.
 */
public record ArrowEnds(boolean atTheStart, boolean atTheEnd) {

    public static final ArrowEnds NEITHER = new ArrowEnds(false, false);

    public static ArrowEnds fromFlags(PairValue flags) {
        return new ArrowEnds(flags.x() != 0, flags.y() != 0);
    }

    public boolean anyAtAll() {
        return atTheStart || atTheEnd;
    }
}
