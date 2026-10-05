package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ReverseAction extends DefaultNative {

    @Override
    public String name() {
        return "reverse";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("series"),
                Parameter.belongingTo("part", "limit", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("part");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Optional<Value> limit = argumentOf("part", 0, arguments, refinements);
            return switch (arguments.getFirst()) {
                case RebolSeries series when refinements.contains("part") ->
                        series.reversedFront(theFrontOf(series, limit));
                case TupleValue tuple -> tuple.reversedFront((int) Arithmetic.asMagnitude(
                        limit.orElseGet(() -> IntegerValue.of(tuple.segmentCount()))));
                case PairValue pair -> pair.reversed();
                case RebolSeries series -> series.reversedFromHere();
                case Value anythingElse -> refuseTheArgument(anythingElse, "series");
            };
        };
    }

    private int theFrontOf(RebolSeries series, Optional<Value> limit) {
        return limit.filter(IntegerValue.class::isInstance)
                .map(asked -> (int) Math.max(0, Math.min(
                        ((IntegerValue) asked).magnitude(), series.lengthFromHere())))
                .orElse(series.lengthFromHere());
    }
}
