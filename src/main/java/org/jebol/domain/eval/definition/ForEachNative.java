package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.MapActions;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.function.Supplier;

public class ForEachNative extends LoopingNative {

    @Override
    public String name() {
        return "foreach";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.softQuoted("target"),
                Parameter.required("series"),
                Parameter.required("body", A_BLOCK));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> arguments.get(1) instanceof NoneValue
                ? NoneValue.none()
                : forEachLoop(evaluator, context, arguments.get(0), arguments.get(1),
                        (BlockValue) arguments.get(2));
    }

    private Value forEachLoop(Evaluator evaluator, Context within,
            Value target, Value series, BlockValue body) {

        List<WordValue> names = loopNamesIn(target);
        List<WordValue> taking = namesThatTakeAValue(names);
        MapActions.refuseMoreNamesThanAPairHas(series, taking);
        Supplier<List<Value>> itemsAsTheyStandNow = () -> keysOnly(series, taking.size());

        Context locals = Context.loopFrameOf(within);
        names.forEach(name -> locals.register(name.spelling()));
        BlockValue bound = Binder.bind(body, locals);

        return answerOfTheLoop(() -> {
            Value last = NoneValue.none();
            int at = 0;
            List<Value> items = itemsAsTheyStandNow.get();
            while (at < items.size()) {
                at = setLoopNamesFillingWithNonePastTheEnd(locals, names, items, at, series);
                last = oneRoundCatchingContinue(evaluator, bound, locals);
                items = itemsAsTheyStandNow.get();
            }
            return last;
        });
    }

    private List<Value> keysOnly(Value series, int howManyNames) {
        if (howManyNames != 1) {
            return series.items();
        }
        return switch (series) {
            case ObjectValue object -> object.context().slots().stream()
                    .filter(slot -> !slot.canonical().equals("self"))
                    .<Value>map(slot -> WordValue.of(slot.spelling()))
                    .toList();
            case MapValue map -> map.keys();
            default -> series.items();
        };
    }
}
