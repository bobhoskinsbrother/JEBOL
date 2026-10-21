package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Actions;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.StructValue;
import org.jebol.domain.value.Value;

public class ClearAction extends SeriesOrFileAction {

    public ClearAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "clear";
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case NoneValue nothing -> nothing;
            case PortValue file when file.isAFile() ->
                    findFileBehind(file, evaluator).truncateAtPosition();
            case PortValue queue when queue.findEventQueue().isPresent() ->
                    queue.withItsQueueEmptied();
            case Value subject when Actions.of(subject).isPresent() ->
                    Actions.of(subject).orElseThrow().clear();
            case StructValue struct -> {
                struct.clear();
                yield struct;
            }
            case Value anythingElse -> refuseDatatype(anythingElse);
        };
    }
}
