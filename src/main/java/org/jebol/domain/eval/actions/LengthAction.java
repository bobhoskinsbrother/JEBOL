package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Actions;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
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
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.SetWordValue;

import java.util.Set;

public class LengthAction extends SeriesOrFileAction {

    public LengthAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "length?";
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> evaluator
                .askTheRebolActor(nativeName(), arguments, Set.of())
                .orElseGet(() -> measureTheLengthOf(arguments.getFirst(), evaluator));
    }

    private Value measureTheLengthOf(Value subject, Evaluator evaluator) {
        return switch (subject) {
            case NoneValue nothing -> nothing;
            case PortValue port -> measureTheLengthOfThePort(port, evaluator);
            case TupleValue tuple -> IntegerValue.of(tuple.shownCount());
            case AnyWordValue word -> IntegerValue.of(
                    word.spelling().codePointCount(0, word.spelling().length()));
            case Value kind when Actions.of(kind).isPresent() ->
                    IntegerValue.of(Actions.of(kind).orElseThrow().measureTheLength());
            case RebolSeries series -> IntegerValue.of(series.lengthFromHere());
            case ModuleValue module -> IntegerValue.of(module.context().fieldCount());
            case StructValue struct -> IntegerValue.of(struct.size());
            case Value anythingElse -> refuseArgument(anythingElse, "series");
        };
    }

    private Value measureTheLengthOfThePort(PortValue port, Evaluator evaluator) {
        if (port.findTheEventQueue().isPresent()) {
            return IntegerValue.of(port.findTheEventQueue().orElseThrow().lengthFromHere());
        }
        return switch (port.schemeName()) {
            case "clipboard" -> throw Raised.of(EvaluationFailure.NO_PORT_ACTION,
                    SetWordValue.of(nativeName()));
            case "udp" -> IntegerValue.of(port.readFieldValue("data") instanceof RebolSeries held
                    ? held.lengthFromHere()
                    : 0);
            case "file", "dir" -> findFileBehind(port, evaluator).measureTheLengthLeft();
            default -> IntegerValue.of(port.context().fieldCount());
        };
    }
}
