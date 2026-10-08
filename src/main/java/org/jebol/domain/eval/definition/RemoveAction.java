package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Actions;
import org.jebol.domain.eval.BitsetActions;
import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RemoveAction extends DefaultNative {

    private static final long ONE_ITEM = 1;

    @Override
    public String nativeName() {
        return "remove";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        Set<Datatype> range = new HashSet<>(aPartLimit());
        range.add(Datatype.CHAR);
        return List.of(Parameter.required("series"),
                Parameter.belongingTo("part", "count", Set.copyOf(range)),
                Parameter.belongingTo("key", "which", Set.of()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("part", "key");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            if (arguments.getFirst() instanceof NoneValue nothing) {
                return nothing;
            }
            return evaluator.theRebolActorsAnswer(nativeName(), arguments, refinements)
                    .orElseGet(() -> removedFrom(arguments.getFirst(), arguments, refinements));
        };
    }

    private Value removedFrom(Value subject, List<Value> arguments, Set<String> refinements) {
        Optional<Value> key = argumentOf("key", 0, arguments, refinements);
        return switch (subject) {
            case PortValue queue when queue.eventQueue().isPresent() ->
                    throw Raised.of(EvaluationFailure.NO_PORT_ACTION,
                            WordValue.of(nativeName()).as(Datatype.SET_WORD));
            case MapValue map -> {
                key.ifPresent(map::remove);
                yield map;
            }
            case BitsetValue members -> {
                members.requireChangeable();
                yield new BitsetActions(members).removed(refinements,
                        refinement -> argumentOf(refinement, 0, arguments, refinements)
                                .orElseGet(NoneValue::none));
            }
            case RebolSeries series -> removedFromTheSeries(series, key,
                    argumentOf("part", 0, arguments, refinements));
            case Value anythingElse -> refuseTheArgument(anythingElse, "series");
        };
    }

    private Value removedFromTheSeries(
            RebolSeries series, Optional<Value> key, Optional<Value> part) {
        if (key.isPresent() && series instanceof BlockValue pairs) {
            pairs.removeTheFirstPairWhoseKey(
                    item -> Comparison.identicallyEqual(item, key.get()));
            return series;
        }
        if (key.isPresent()) {
            throw Raised.of(EvaluationFailure.FEATURE_NA,
                    "/key removes from a map or a bitset, not a series");
        }
        long howMany = part.map(count -> howManyCounted(series, count)).orElse(ONE_ITEM);
        return Actions.of(series).orElseThrow().removed(howMany);
    }

    private long howManyCounted(RebolSeries series, Value count) {
        return switch (count) {
            case IntegerValue(long magnitude)
                    when magnitude > Integer.MAX_VALUE || magnitude < Integer.MIN_VALUE ->
                    throw Raised.of(EvaluationFailure.OUT_OF_RANGE, Long.toString(magnitude));
            case IntegerValue(long magnitude) -> magnitude;
            case DecimalValue fraction when fraction.datatype() != Datatype.PERCENT ->
                    (long) Comparison.asDouble(fraction);
            case DecimalValue ignored -> throw Raised.of(EvaluationFailure.INVALID_PART, count);
            case PairValue ignored -> throw Raised.of(EvaluationFailure.INVALID_PART, count);
            case RebolSeries upTo when series.datatype() == upTo.datatype()
                    && series.sharesStorageWith(upTo) -> upTo.index() - series.index();
            case RebolSeries ignored -> throw Raised.of(EvaluationFailure.INVALID_PART, count);
            default -> ONE_ITEM;
        };
    }
}
