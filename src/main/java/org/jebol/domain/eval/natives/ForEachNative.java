package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.MapActions;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.function.Supplier;

public class ForEachNative extends LoopingNative {

    @Override
    public String nativeName() {
        return "foreach";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.quoteSoftly("target"),
                Parameter.of("series"),
                Parameter.of("body", BLOCK));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> arguments.get(1) instanceof NoneValue
                ? NoneValue.none()
                : loopOverEach(evaluator, context, arguments.get(0), arguments.get(1),
                        (AnyBlockValue) arguments.get(2));
    }

    private Value loopOverEach(Evaluator evaluator, Context within,
                               Value target, Value series, AnyBlockValue body) {

        List<AnyWordValue> names = loopNamesIn(target);
        List<AnyWordValue> taking = keepTheNamesThatTakeAValue(names);
        MapActions.refuseMoreNamesThanPairHas(series, taking);
        Supplier<List<Value>> itemsAsTheyStandNow = () -> takeTheKeysOnly(series, taking.size());

        Context locals = Context.loopFrameOf(within);
        names.forEach(name -> locals.register(name.spelling()));
        AnyBlockValue bound = Binder.bind(body, locals);

        return answerOfTheLoop(() -> {
            Value last = NoneValue.none();
            int at = 0;
            List<Value> items = itemsAsTheyStandNow.get();
            while (at < items.size()) {
                at = setLoopNamesFillingWithNonePastEnd(locals, names, items, at, series);
                last = runOneRoundCatchingContinue(evaluator, bound, locals);
                items = itemsAsTheyStandNow.get();
            }
            return last;
        });
    }

    private List<Value> takeTheKeysOnly(Value series, int howManyNames) {
        if (howManyNames != 1) {
            return series.listTheItems();
        }
        return switch (series) {
            case ObjectValue object -> object.context().slots().stream()
                    .<Value>map(slot -> WordValue.of(slot.spelling()))
                    .toList();
            case MapValue map -> map.keys();
            default -> series.listTheItems();
        };
    }
}
