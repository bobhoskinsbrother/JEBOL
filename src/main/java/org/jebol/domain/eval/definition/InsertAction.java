package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Actions;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Value;

public class InsertAction extends AddingAction {

    private static final boolean AT_THE_END = false;

    public InsertAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String name() {
        return "insert";
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case PortValue queue when queue.eventQueue().isPresent() ->
                    queue.queued(arguments.get(1), AT_THE_END);
            case Value subject when Actions.of(subject).isPresent() ->
                    Actions.of(subject).orElseThrow().insert(
                            askedOf(subject, arguments, refinements, evaluator, context));
            case Value anythingElse -> refuseTheDatatype(anythingElse);
        };
    }
}
