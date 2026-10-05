package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;

import java.util.List;

public abstract class WithinCodepointsNative extends DefaultNative {

    protected abstract int highest();

    @Override
    public List<Parameter> parameters() {
        return acceptsWhateverComesAlong("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case CharacterValue character -> LogicValue.of(character.codepoint() <= highest());
            case StringValue text -> LogicValue.of(text.text().codePoints()
                    .allMatch(codepoint -> codepoint <= highest()));
            default -> refuseTheArgument(arguments.getFirst(), "string or character");
        };
    }
}
