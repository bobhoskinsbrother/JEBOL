package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Actions;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.ModuleValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.StructValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.Set;

public class LengthAction extends SeriesOrFileAction {

    public LengthAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String name() {
        return "length?";
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> evaluator
                .theRebolActorsAnswer(name(), arguments, Set.of())
                .orElseGet(() -> lengthOf(arguments.getFirst(), evaluator));
    }

    private Value lengthOf(Value subject, Evaluator evaluator) {
        return switch (subject) {
            case NoneValue nothing -> nothing;
            case PortValue port -> lengthOfThePort(port, evaluator);
            case TupleValue tuple -> IntegerValue.of(tuple.shownCount());
            case WordValue word -> IntegerValue.of(
                    word.spelling().codePointCount(0, word.spelling().length()));
            case Value kind when Actions.of(kind).isPresent() ->
                    IntegerValue.of(Actions.of(kind).orElseThrow().length());
            case RebolSeries series -> IntegerValue.of(series.lengthFromHere());
            case ModuleValue module -> IntegerValue.of(module.context().fieldCount());
            case StructValue struct -> IntegerValue.of(struct.size());
            case Value anythingElse -> refuseTheArgument(anythingElse, "series");
        };
    }

    private Value lengthOfThePort(PortValue port, Evaluator evaluator) {
        if (port.eventQueue().isPresent()) {
            return IntegerValue.of(port.eventQueue().orElseThrow().lengthFromHere());
        }
        return switch (port.schemeName()) {
            case "clipboard" -> throw Raised.of(EvaluationFailure.NO_PORT_ACTION,
                    WordValue.of(name()).as(Datatype.SET_WORD));
            case "udp" -> IntegerValue.of(port.fieldValue("data") instanceof RebolSeries held
                    ? held.lengthFromHere()
                    : 0);
            case "file", "dir" -> theFileBehind(port, evaluator).lengthLeft();
            default -> IntegerValue.of(port.context().fieldCount());
        };
    }
}
