package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record Asked(
        Value subject,
        Value given,
        Value duplicated,
        Value limitAsked,
        Optional<Long> howMuchOfIt,
        int howManyOctets,
        long howManyTimes,
        boolean wholeRatherThanSpliced,
        Set<String> refinementsAsked,
        Evaluator evaluator,
        Context context) {

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

    public List<Value> theWantedItemsOf(BlockValue added) {
        return howMuchOf(added, limitAsked)
                .map(count -> {
                    List<Value> whole = added.head().remaining();
                    int here = added.index() - 1;
                    int from = count >= 0 ? here : (int) Math.max(0, here + count);
                    int to = count >= 0
                            ? (int) Math.min(whole.size(), here + count)
                            : here;
                    int start = Math.min(from, whole.size());
                    return whole.subList(start,
                            Math.max(Math.min(to, whole.size()), start));
                })
                .orElseGet(added::remaining);
    }


    public void refuseRefinementsThisDatatypeDoesNotServe(String nativeName) {
        for (String unfinished : List.of("part", "only", "dup")) {
            if (refinementsAsked.contains(unfinished)) {
                throw Raised.of(EvaluationFailure.FEATURE_NA,
                        nativeName + "/" + unfinished + " on a gob is not implemented");
            }
        }
    }

    public static Asked reading(
            Value subject, Value given, Set<String> refinementsAsked,
            Optional<Value> times, Optional<Value> part,
            Evaluator evaluator, Context context) {

        Value limit = part.orElseGet(NoneValue::none);
        return new Asked(
                subject,
                given,
                times.<Value>map(given::repeatedInABlock).orElse(given),
                limit,
                howMuchOf(given, limit),
                howManyOctetsOf(given, limit),
                refinementsAsked.contains("dup")
                        && times.orElseGet(NoneValue::none) instanceof IntegerValue(long magnitude)
                        ? Math.max(0, magnitude)
                        : 1,
                refinementsAsked.contains("only"),
                refinementsAsked,
                evaluator,
                context);
    }

    private static int howManyOctetsOf(Value given, Value limit) {
        if (limit instanceof IntegerValue(long magnitude)) {
            return (int) magnitude;
        }
        if (limit instanceof RebolSeries upTo
                && given instanceof RebolSeries from
                && from.sharesStorageWith(upTo)) {
            return Math.abs(upTo.index() - from.index());
        }
        return SeriesContents.EVERY_ONE;
    }

    private static Optional<Long> howMuchOf(Value given, Value limit) {
        if (limit instanceof IntegerValue(long magnitude)) {
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
            throw Raised.of(EvaluationFailure.INVALID_PART, limit);
        }
        if (limit instanceof RebolSeries upTo) {
            if (!(given instanceof RebolSeries from)
                    || from.datatype() != upTo.datatype()
                    || !from.sharesStorageWith(upTo)) {
                throw Raised.of(EvaluationFailure.INVALID_PART, limit);
            }
            return Optional.of((long) (upTo.index() - from.index()));
        }
        return Optional.empty();
    }
}
