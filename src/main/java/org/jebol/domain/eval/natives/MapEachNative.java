package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.ArrayList;
import java.util.List;

public class MapEachNative extends LoopingNative {

    @Override
    public String nativeName() {
        return "map-each";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.softQuoted("word"),
                Parameter.required("series", A_BLOCK),
                Parameter.required("body", A_BLOCK));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Context locals = Context.loopFrameOf(context);
            List<WordValue> names = loopNamesIn(arguments.get(0));
            names.forEach(name -> locals.register(name.spelling()));
            BlockValue bound = Binder.bind((BlockValue) arguments.get(2), locals);
            List<Value> items = arguments.get(1).items();
            List<Value> gathered = new ArrayList<>();
            int at = 0;
            while (at < items.size()) {
                at = setLoopNamesFillingWithNonePastTheEnd(
                        locals, names, items, at, arguments.get(1));
                Value made = evaluator.evaluateOrRaise(bound, locals);
                if (!(made instanceof UnsetValue)) {
                    gathered.add(made);
                }
            }
            return BlockValue.block(gathered);
        };
    }
}
