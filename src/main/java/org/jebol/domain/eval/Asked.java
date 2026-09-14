package org.jebol.domain.eval;

import org.jebol.domain.value.BlockStorage;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.SeriesValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One action, asked of one value, with its refinements already read.
 *
 * <p>An arm should be about its own datatype and nothing else, so the
 * refinements are resolved here rather than in each of the ten places an
 * action lands. What reaches an arm is a plain question -- add this, this
 * many times, this much of it, whole or spliced -- and the arm answers it in
 * the terms of its own type.
 *
 * <p>{@code /part} keeps its awkwardness, because REBOL's does: it may be a
 * count, or a position in the very same storage the value came from, and the
 * second form is only meaningful against that value. Reading it once here is
 * the reason an arm never has to know that.
 */
public record Asked(
        Value subject,
        Value given,
        Value duplicated,
        Optional<Long> howMuchOfIt,
        int howManyOctets,
        long howManyTimes,
        boolean wholeRatherThanSpliced,
        Set<String> refinementsAsked,
        Evaluator evaluator,
        Context context) {

    /**
     * The same {@code /part}, read the way a binary reads it, which is not
     * the way {@link #howMuchOfIt} reads it and never has been.
     *
     * <p>Two differences, both of which a test pins. A position in the same
     * storage counts backwards as a distance here and as a signed offset
     * there, so appending {@code /part} a position behind the value adds
     * octets rather than none. And a fractional or paired limit is quietly
     * ignored here where the other reading raises {@code invalid-part}.
     *
     * <p>Keeping both is not tidiness deferred -- collapsing them would
     * change what REBOL answers.
     */
    public int howManyOctetsWanted() {
        return howManyOctets;
    }

    /** The first few of a list, or all of them when {@code /part} said nothing. */
    public List<Value> theFirstFewOf(List<Value> items) {
        return howMuchOfIt
                .map(count -> items.subList(0,
                        (int) Math.max(0, Math.min(count, items.size()))))
                .orElse(items);
    }

    /**
     * What an arm says when it serves an action but none of its refinements.
     *
     * <p>The wording names a gob whatever the datatype is, which is what it
     * has always said and what the tests pin. An image reaching here is told
     * it is a gob, and that is worth fixing under its own change rather than
     * quietly here.
     */
    public void refuseRefinementsThisDatatypeDoesNotServe(String nativeName) {
        for (String unfinished : List.of("part", "only", "dup")) {
            if (refinementsAsked.contains(unfinished)) {
                throw Raised.of(EvaluationFailure.FEATURE_NA,
                        nativeName + "/" + unfinished + " on a gob is not implemented");
            }
        }
    }

    /**
     * Reads the refinements once, so that every arm below is handed the same
     * already-answered question.
     *
     * <p>{@code theArgumentFor} is how the registry hands over a refinement's
     * argument; the plumbing that finds it stays where refinements are
     * declared rather than coming in here.
     */
    public static Asked reading(
            Value subject, Value given, Set<String> refinementsAsked,
            java.util.function.Function<String, Value> theArgumentFor,
            Evaluator evaluator, Context context) {

        Value times = theArgumentFor.apply("dup");
        Value limit = theArgumentFor.apply("part");
        return new Asked(
                subject,
                given,
                duplicated(given, times),
                howMuchOf(given, limit),
                howManyOctetsOf(given, limit),
                refinementsAsked.contains("dup") && times instanceof IntegerValue counted
                        ? Math.max(0, counted.magnitude())
                        : 1,
                refinementsAsked.contains("only"),
                refinementsAsked,
                evaluator,
                context);
    }

    private static int howManyOctetsOf(Value given, Value limit) {
        if (limit instanceof IntegerValue wanted) {
            return (int) wanted.magnitude();
        }
        if (limit instanceof SeriesValue upTo
                && given instanceof SeriesValue from
                && from.sharesStorageWith(upTo)) {
            return Math.abs(upTo.index() - from.index());
        }
        return SeriesContents.EVERY_ONE;
    }

    private static Value duplicated(Value given, Value times) {
        if (times == null) {
            return given;
        }
        BlockValue spread = given instanceof BlockValue block
                && block.datatype() == Datatype.BLOCK
                ? block
                : null;
        List<Value> pieces = spread == null ? List.of(given) : spread.remaining();
        BlockStorage repeated = new BlockStorage();
        for (long round = 0; round < wholeCountOf(times); round++) {
            repeated.spliceInAt(repeated.length() + 1, pieces,
                    spread == null ? null : spread.storage(),
                    spread == null ? 1 : spread.index());
        }
        return new BlockValue(repeated, 1, Datatype.BLOCK);
    }

    private static long wholeCountOf(Value times) {
        return switch (times) {
            case IntegerValue count -> count.magnitude();
            case DecimalValue fraction when fraction.datatype() != Datatype.PERCENT ->
                    (long) Comparison.asDouble(fraction);
            default -> throw Raised.of(EvaluationFailure.INVALID_TYPE,
                    Molder.mold(times) + " is not a count of repetitions");
        };
    }

    private static Optional<Long> howMuchOf(Value given, Value limit) {
        if (limit instanceof IntegerValue wanted) {
            long magnitude = wanted.magnitude();
            if (magnitude > Integer.MAX_VALUE || magnitude < Integer.MIN_VALUE) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE, Long.toString(magnitude));
            }
            return Optional.of(magnitude);
        }
        if (limit instanceof DecimalValue fraction
                && fraction.datatype() != Datatype.PERCENT) {
            return Optional.of((long) Comparison.asDouble(fraction));
        }
        if (limit instanceof DecimalValue || limit instanceof PairValue) {
            throw Raised.of(EvaluationFailure.INVALID_PART, Molder.mold(limit));
        }
        if (limit instanceof SeriesValue upTo) {
            if (!(given instanceof SeriesValue from)
                    || from.datatype() != upTo.datatype()
                    || !from.sharesStorageWith(upTo)) {
                throw Raised.of(EvaluationFailure.INVALID_PART, "part");
            }
            return Optional.of((long) (upTo.index() - from.index()));
        }
        return Optional.empty();
    }

    /** A stand-in when an arm asks about a limit with no value to measure against. */
    public static Value nothingToMeasureAgainst() {
        return NoneValue.none();
    }
}
