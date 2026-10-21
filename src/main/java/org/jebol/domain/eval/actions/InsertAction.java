package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Actions;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Value;

public class InsertAction extends AddingAction {

    private static final boolean AT_END = false;

    public InsertAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "insert";
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case PortValue queue when queue.findTheEventQueue().isPresent() ->
                    queue.queue(arguments.get(1), AT_END);
            case Value subject when Actions.of(subject).isPresent() ->
                    Actions.of(subject).orElseThrow().insert(
                            readWhatIsAskedOf(subject, arguments, refinements, evaluator, context));
            case Value anythingElse -> refuseDatatype(anythingElse);
        };
    }
}
