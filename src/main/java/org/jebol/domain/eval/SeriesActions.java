package org.jebol.domain.eval;

import org.jebol.domain.value.SeriesValue;
import org.jebol.domain.value.Value;

import java.util.List;

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

    /**
     * The elements from a position onwards, each as the value a script sees:
     * a character for a string, an integer for a binary, a tuple of four
     * channels for an image.
     */
    abstract List<Value> elementsOf(SeriesValue from);

    /**
     * A new series of this one's own kind holding these values, which is what
     * TAKE and COPY answer with. A string built from characters, a binary
     * from octets, a vector narrowed back to its own width.
     */
    abstract Value ofTheSameKindHolding(List<Value> items);

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

    /** Takes a run out at a position, one element at a time. */
    void takeOutFrom(int oneBasedIndex, int howMany) {
        for (int gone = 0; gone < howMany; gone++) {
            takeOneOutAt(oneBasedIndex);
        }
    }

    /**
     * TAKE: lifts a run out and answers it as a series of this kind.
     *
     * <p>A negative count reaches backwards from the position, and neither
     * direction reaches past an end -- asking for more than is there takes
     * what is there.
     */
    public Value takenSeveral(long wanted) {
        int from = Math.min(held().index(), held().storageLength() + 1);
        int howMany;
        if (wanted >= 0) {
            howMany = (int) Math.min(wanted, held().lengthFromHere());
        } else {
            howMany = (int) Math.min(-wanted, from - 1L);
            from -= howMany;
        }
        List<Value> taken = List.copyOf(
                elementsOf(held().head()).subList(from - 1, from - 1 + howMany));
        takeOutFrom(from, howMany);
        return ofTheSameKindHolding(taken);
    }

    /** TAKE with no count: the one element here, or none when there is none. */
    public Value takenOne() {
        if (held().lengthFromHere() == 0) {
            return org.jebol.domain.value.NoneValue.none();
        }
        Value taken = elementsOf(held()).getFirst();
        takeOutFrom(held().index(), 1);
        return taken;
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
