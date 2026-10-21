package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Actions;
import org.jebol.domain.eval.BitsetActions;
import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RemoveAction extends DefaultNative implements ActionValue {

    private static final long ONE_ITEM = 1;

    @Override
    public String nativeName() {
        return "remove";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        Set<Datatype> range = new HashSet<>(listThePartLimitTypes());
        range.add(CharacterValue.TYPE);
        return List.of(Parameter.of("series"),
                Parameter.belongTo("part", "count", Set.copyOf(range)),
                Parameter.belongTo("key", "which", Set.of()));
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
            return evaluator.askTheRebolActor(nativeName(), arguments, refinements)
                    .orElseGet(() -> removeFrom(arguments.getFirst(), arguments, refinements));
        };
    }

    private Value removeFrom(Value subject, List<Value> arguments, Set<String> refinements) {
        Optional<Value> key = refinementArgument("key", 0, arguments, refinements);
        return switch (subject) {
            case PortValue queue when queue.findTheEventQueue().isPresent() ->
                    throw Raised.of(EvaluationFailure.NO_PORT_ACTION,
                            SetWordValue.of(nativeName()));
            case MapValue map -> {
                key.ifPresent(map::remove);
                yield map;
            }
            case BitsetValue members -> {
                members.requireChangeable();
                yield new BitsetActions(members).remove(refinements,
                        refinement -> refinementArgument(refinement, 0, arguments, refinements)
                                .orElseGet(NoneValue::none));
            }
            case RebolSeries series -> removeFromTheSeries(series, key,
                    refinementArgument("part", 0, arguments, refinements));
            case Value anythingElse -> refuseArgument(anythingElse, "series");
        };
    }

    private Value removeFromTheSeries(
            RebolSeries series, Optional<Value> key, Optional<Value> part) {
        if (key.isPresent() && series instanceof AnyBlockValue pairs) {
            pairs.removeTheFirstPairWhoseKey(
                    item -> Comparison.areIdenticallyEqual(item, key.get()));
            return series;
        }
        if (key.isPresent()) {
            throw Raised.of(EvaluationFailure.FEATURE_NA,
                    "/key removes from a map or a bitset, not a series");
        }
        long howMany = part.map(count -> countHowMany(series, count)).orElse(ONE_ITEM);
        return Actions.of(series).orElseThrow().remove(howMany);
    }

    private long countHowMany(RebolSeries series, Value count) {
        return switch (count) {
            case IntegerValue whole
                    when whole.magnitude() > Integer.MAX_VALUE || whole.magnitude() < Integer.MIN_VALUE ->
                    throw Raised.of(EvaluationFailure.OUT_OF_RANGE, Long.toString(whole.magnitude()));
            case IntegerValue whole -> whole.magnitude();
            case DecimalValue fraction ->
                    (long) Comparison.asDouble(fraction);
            case AnyDecimalValue ignored -> throw Raised.of(EvaluationFailure.INVALID_PART, count);
            case PairValue ignored -> throw Raised.of(EvaluationFailure.INVALID_PART, count);
            case RebolSeries upTo when series.datatype() == upTo.datatype()
                    && series.sharesStorageWith(upTo) -> upTo.index() - series.index();
            case RebolSeries ignored -> throw Raised.of(EvaluationFailure.INVALID_PART, count);
            default -> ONE_ITEM;
        };
    }
}
