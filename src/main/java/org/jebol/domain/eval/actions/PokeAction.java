package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Actions;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class PokeAction extends DefaultNative implements ActionValue {

    @Override
    public String nativeName() {
        return "poke";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("series", TypesetValue.SERIES.membersAnd(
                        PortValue.TYPE, MapValue.TYPE, GobValue.TYPE, BitsetValue.TYPE)),
                Parameter.of("index"),
                Parameter.of("value", TypesetValue.ANY_TYPE.members()));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case PortValue queue when queue.findEventQueue().isPresent() ->
                    pokeIntoQueue(queue.findEventQueue().orElseThrow(), arguments);
            case Value subject -> Actions.of(subject)
                    .orElseThrow(() -> Raised.cannotUse(subject, this))
                    .poke(arguments.get(1), arguments.get(2));
        };
    }

    private Value pokeIntoQueue(AnyBlockValue queued, List<Value> arguments) {
        if (!(arguments.get(2) instanceof EventValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, arguments.get(2));
        }
        return Actions.of(queued).orElseThrow()
                .poke(arguments.get(1), arguments.get(2));
    }
}
