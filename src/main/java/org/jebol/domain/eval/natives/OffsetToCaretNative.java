package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.render.GobText;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class OffsetToCaretNative extends TextMeasuringNative {

    public OffsetToCaretNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "offset-to-caret";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("gob", Set.of(GobValue.TYPE)),
                Parameter.required("position", Set.of(PairValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            GobText text = theTextOf((GobValue) arguments.get(0), evaluator);
            PairValue point = (PairValue) arguments.get(1);
            return text.theBlockAtTheCaret(
                            text.lines().caretNearest(point.x(), point.y(), text.layout(), text.theGobsOwnBox()))
                    .<Value>map(found -> found)
                    .orElseGet(NoneValue::none);
        };
    }
}
