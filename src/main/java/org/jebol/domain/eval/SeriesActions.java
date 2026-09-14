package org.jebol.domain.eval;

import org.jebol.domain.value.SeriesValue;
import org.jebol.domain.value.Value;

/**
 * The arms every series answers the same way, which the C shares as
 * {@code Do_Series} across {@code t-block.c} and {@code t-string.c}.
 *
 * <p>A series action is almost always one walk with one thing done at each
 * step, and the walk is the same whatever the series holds -- what differs is
 * only how a single element is read out or taken out. So the walk lives here
 * once and each datatype supplies the step.
 *
 * <p>Nothing is served by default beyond {@link #length()}. A series that
 * does not answer an action must not start answering it merely by being a
 * series: an image has a position and a length but refuses CLEAR, and that
 * refusal is the value's to keep rather than something this class overrules.
 */
abstract class SeriesActions implements Actions {

    /** The series these arms answer for, at the position it is held. */
    abstract SeriesValue held();

    /** Takes one element out at a position, which is the step of every walk. */
    abstract void takeOneOutAt(int oneBasedIndex);

    @Override
    public Value subject() {
        return (Value) held();
    }

    @Override
    public int length() {
        return held().lengthFromHere();
    }

    /**
     * REMOVE: drop so many from here, stopping at the tail however many were
     * asked for.
     *
     * <p>A negative count reaches backwards instead, and takes the run ending
     * where the series is held rather than starting there -- so
     * {@code remove/part series -3} drops the three before the position and
     * answers that position, now three earlier.
     */
    public Value removed(long howMany) {
        SeriesValue removingFrom = theRunReachingBackIfNegative(howMany);
        for (long dropped = 0; dropped < Math.abs(howMany)
                && !removingFrom.atTail(); dropped++) {
            takeOneOutAt(removingFrom.index());
        }
        return (Value) removingFrom;
    }

    /**
     * Emptying one element at a time, which is what a series with no quicker
     * way of dropping a run does for CLEAR.
     */
    Value clearedOneAtATime() {
        while (held().storageLength() >= held().index()) {
            takeOneOutAt(held().index());
        }
        return (Value) held();
    }

    /**
     * Where a run of the asked-for size begins, which is here when the count
     * is positive and that many back when it is negative -- never before the
     * head, however far back it reaches for.
     */
    private SeriesValue theRunReachingBackIfNegative(long wanted) {
        if (wanted >= 0) {
            return held();
        }
        int reaching = (int) Math.min(-wanted, held().index() - 1L);
        return held().atIndex(held().index() - reaching);
    }
}
