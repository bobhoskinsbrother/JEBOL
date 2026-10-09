package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.render.GobText;
import org.jebol.domain.render.TextLines;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class CaretToOffsetNative extends TextMeasuringNative {

    public CaretToOffsetNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "caret-to-offset";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("gob", Set.of(GobValue.TYPE)),
                Parameter.required("element", Set.of(IntegerValue.TYPE, BlockValue.TYPE)),
                Parameter.required("position", Set.of(IntegerValue.TYPE, StringValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            GobText text = theTextOf((GobValue) arguments.get(0), evaluator);
            TextLines.CaretPlace placed = text.lines().whereTheCaretIs(
                    text.theRunAt(thePlaceInTheBlock(arguments.get(1))),
                    theCharacterBefore(arguments.get(2)),
                    text.layout(), text.theGobsOwnBox());
            return inWholePixels(placed.across(), placed.top());
        };
    }

    private int thePlaceInTheBlock(Value element) {
        return switch (element) {
            case IntegerValue(long place) -> (int) place;
            case AnyBlockValue atTheString -> atTheString.index();
            default -> 0;
        };
    }

    private int theCharacterBefore(Value position) {
        return switch (position) {
            case IntegerValue(long place) -> (int) place - 1;
            case AnyStringValue atTheCaret -> atTheCaret.index() - 1;
            default -> 0;
        };
    }
}
