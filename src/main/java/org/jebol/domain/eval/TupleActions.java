package org.jebol.domain.eval;

import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

/**
 * What a tuple does when an action is performed on it, which is what
 * {@code REBTYPE(Tuple)} answers in {@code t-tuple.c}.
 *
 * <p>A tuple works octet by octet and every answer is clipped back into a
 * byte, because a tuple is a colour or a version rather than a row of
 * numbers. Two guards in the C are not obvious and are kept here: a zero
 * octet multiplied is left alone rather than made zero again, and a
 * multiplier above 255 saturates instead of overflowing.
 *
 * <p>A time beside a tuple is refused before anything else is asked, which is
 * the one pairing the C singles out.
 */
public final class TupleActions {

    private final Value left;

    public TupleActions(Value left) {
        this.left = left;
    }

    /**
     * COMPLEMENT, which subtracts each octet from 255 rather than turning its
     * bits over, so a colour complements to the colour a reader expects and
     * not to a number that happens to share its bits.
     */
    public Value complemented() {
        int[] octets = ((TupleValue) left).segments();
        for (int at = 0; at < octets.length; at++) {
            octets[at] = 255 - octets[at];
        }
        return TupleValue.of(octets);
    }

    /** ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER and MODULO, octet by octet. */
    Value combinedWith(Value right, Arithmetic.Operation operation) {
        return octetByOctet(left, right, (octet, against, fractional) ->
                octetCombined(octet, against, fractional, operation));
    }

    private static long octetCombined(
            long octet, double against, boolean fractional,
            Arithmetic.Operation operation) {

        return switch (operation) {
            case ADD -> octet + (long) against;
            case SUBTRACT -> octet - (long) against;
            case MULTIPLY -> {
                if (octet == 0) {
                    yield 0;
                }
                if (against > 255) {
                    yield 255;
                }
                yield fractional ? (long) (octet * against) : octet * (long) against;
            }
            case DIVIDE -> {
                if (against == 0) {
                    throw Raised.of(EvaluationFailure.ZERO_DIVIDE, "tuple");
                }
                yield fractional
                        ? (long) roundedHalfAwayFromZero(octet / against)
                        : octet / (long) against;
            }
            case REMAINDER, MODULO -> {
                if ((long) against == 0) {
                    throw Raised.of(EvaluationFailure.ZERO_DIVIDE, "tuple");
                }
                yield octet % (long) against;
            }
        };
    }

    private static double roundedHalfAwayFromZero(double amount) {
        return amount < 0 ? -Math.round(-amount) : Math.round(amount);
    }

    /** What one octet is worked out from, which the bitwise operators share. */
    @FunctionalInterface
    public interface OctetWork {
        long against(long octet, double amount, boolean fractional);
    }

    /**
     * Every octet of the wider of the two tuples, worked out and clipped.
     *
     * <p>A plain number on the right stands in for every octet, and whether it
     * was written as a fraction changes the answer: a tuple divided by 2 and
     * the same tuple divided by 2.0 round differently, which is why the work
     * is told rather than left to guess.
     */
    public static Value octetByOctet(Value left, Value right, OctetWork work) {
        refuseATimeBesideATuple(left, right);
        if (!(left instanceof TupleValue ours)) {
            throw Raised.cannotUse(left, "tuple arithmetic");
        }
        TupleValue theirs = right instanceof TupleValue tuple ? tuple : null;
        if (theirs == null && !Comparison.isNumeric(right)) {
            throw Raised.cannotUse(right, "tuple arithmetic");
        }
        int width = theirs == null
                ? ours.segmentCount()
                : Math.max(ours.segmentCount(), theirs.segmentCount());
        boolean fractional = right.datatype() == Datatype.DECIMAL
                || right.datatype() == Datatype.PERCENT;
        double amount = theirs == null ? Comparison.asDouble(right) : 0;

        int[] answer = new int[width];
        for (int at = 1; at <= width; at++) {
            long worked = work.against(ours.octetAt(at),
                    theirs == null ? amount : theirs.octetAt(at), fractional);
            answer[at - 1] = (int) Math.max(0, Math.min(255, worked));
        }
        return TupleValue.of(answer);
    }

    private static void refuseATimeBesideATuple(Value left, Value right) {
        if (left instanceof TimeValue || right instanceof TimeValue) {
            throw Raised.of(EvaluationFailure.NOT_RELATED,
                    DatatypeValue.of(Datatype.TIME),
                    DatatypeValue.of(Datatype.TUPLE));
        }
    }
}
