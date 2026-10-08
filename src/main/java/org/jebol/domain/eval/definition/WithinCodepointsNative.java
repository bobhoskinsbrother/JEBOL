package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public abstract class WithinCodepointsNative extends DefaultNative {

    protected abstract int highest();

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWhateverComesAlong("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case CharacterValue character -> LogicValue.of(character.codepoint() <= highest());
            case IntegerValue codepoint -> LogicValue.of(codepoint.magnitude() <= highest());
            case StringValue text -> LogicValue.of(text.text().codePoints()
                    .allMatch(codepoint -> codepoint <= highest()));
            default -> refuseTheArgument(arguments.getFirst(), "value");
        };
    }
}
