package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.SeriesSearch;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorValue;
import org.jebol.domain.value.WordValue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SelectAction extends DefaultNative {

    private static final Set<Datatype> BOUNDS_A_PART = Stream.concat(
                    Typeset.NUMBER.membersAnd(Datatype.PAIR).stream(),
                    Arrays.stream(Datatype.values()).filter(Datatype::isSeries))
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public String name() {
        return "select";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("series"),
                Parameter.required("value", Typeset.ANY_TYPE.members()),
                Parameter.belongingTo("part", "range", BOUNDS_A_PART),
                Parameter.belongingTo("with", "wild", Set.of(Datatype.STRING)),
                Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("case", "skip", "any", "only", "last", "part", "same", "with",
                "reverse");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> evaluator
                .theRebolActorsAnswer("select", arguments, refinements)
                .orElseGet(() -> selected(arguments, refinements));
    }

    private Value selected(List<Value> arguments, Set<String> refinements) {
        Value subject = arguments.getFirst();
        Value wanted = arguments.get(1);
        return switch (subject) {
            case MapValue map -> map.select(wanted, refinements.contains("case"));
            case VectorValue numbers -> refuseTheDatatype(numbers);
            case NoneValue nothing -> nothing;
            case Value object when object.isAnyObject() -> theFieldWanted(object, wanted);
            case RebolSeries series -> {
                series.refuseANeedleItCannotHold(wanted, name());
                yield theItemAfterTheMatch(series, wanted, arguments, refinements);
            }
            case Value anythingElse -> refuseTheDatatype(anythingElse);
        };
    }

    private Value theFieldWanted(Value object, Value wanted) {
        if (!object.declaresAFieldFindCanReachBy(wanted)) {
            return NoneValue.none();
        }
        return object.fieldValue(((WordValue) wanted).canonical());
    }

    private Value theItemAfterTheMatch(
            RebolSeries series, Value wanted, List<Value> arguments, Set<String> refinements) {
        SeriesSearch search = new SeriesSearch(series, wanted, refinements,
                argumentOf("part", 0, arguments, refinements),
                argumentOf("skip", 0, arguments, refinements),
                argumentOf("with", 0, arguments, refinements));
        if (search.stridesForwardByLessThanOne()) {
            return refuseARecordWidthBelowOne(series, search.stride());
        }
        int found = search.position();
        if (found < 0) {
            return NoneValue.none();
        }
        int after = found - 1 + search.lengthMatchedAt(found);
        return after >= search.end()
                ? NoneValue.none()
                : series.head().items().get(after);
    }

    private Value refuseARecordWidthBelowOne(RebolSeries series, long stride) {
        if (series instanceof BlockValue) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "select/skip needs a record width of at least one, not " + stride);
        }
        return NoneValue.none();
    }
}
