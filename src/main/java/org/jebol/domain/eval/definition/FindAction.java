package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.BitsetActions;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.SeriesSearch;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorValue;

import java.util.List;
import java.util.Set;

public class FindAction extends SeriesSearchAction {

    @Override
    public String nativeName() {
        return "find";
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("tail", "last", "only", "case", "any", "same", "part",
                "with", "skip", "reverse", "match");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value subject = arguments.getFirst();
            Value wanted = arguments.get(1);
            return switch (subject) {
                case NoneValue nothing -> nothing;
                case Value object when object.isAnyObject() ->
                        object.declaresAFieldFindCanReachBy(wanted)
                                ? LogicValue.of(true)
                                : NoneValue.none();
                case MapValue map -> map.storedKeyLike(wanted, refinements.contains("case"));
                case BitsetValue bitset -> LogicValue.of(new BitsetActions(bitset).holds(
                        wanted, refinements.contains("any"), !refinements.contains("case")));
                case TypesetValue typeset -> LogicValue.of(
                        wanted instanceof DatatypeValue(Datatype represents)
                                && typeset.holds(represents));
                case GobValue searched -> theChildFound(searched, wanted);
                case ImageValue picture -> picture.thePixelFound(wanted,
                        refinements.contains("match"), refinements.contains("only"),
                        refinements.contains("tail"));
                case VectorValue numbers -> refuseTheDatatype(numbers);
                case RebolSeries series ->
                        theMatchFound(series, wanted, arguments, refinements);
                case Value anythingElse -> refuseTheDatatype(anythingElse);
            };
        };
    }

    private Value theChildFound(GobValue searched, Value wanted) {
        if (!(wanted instanceof GobValue child)) {
            return NoneValue.none();
        }
        int at = searched.storage().positionOf(child.storage());
        return at == 0 ? NoneValue.none() : searched.atIndex(at);
    }

    private Value theMatchFound(RebolSeries series, Value wanted, List<Value> arguments,
            Set<String> refinements) {
        SeriesSearch search = new SeriesSearch(series, wanted, refinements,
                argumentOf("part", 0, arguments, refinements),
                argumentOf("skip", 0, arguments, refinements),
                argumentOf("with", 0, arguments, refinements));
        series.refuseANeedleItCannotHold(wanted, nativeName());
        if (search.stridesForwardByLessThanOne()) {
            if (series instanceof BlockValue) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                        "find/skip needs a record width of at least one, not "
                                + search.stride());
            }
            return NoneValue.none();
        }
        int found = search.position();
        if (found < 0 || (refinements.contains("match") && found != series.index())) {
            return NoneValue.none();
        }
        return series.atIndex(refinements.contains("tail")
                ? found + search.lengthMatchedAt(found)
                : found);
    }
}
