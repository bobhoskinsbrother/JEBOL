package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Actions;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

public class AppendAction extends AddingAction {

    private static final boolean AT_THE_END = true;

    public AppendAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "append";
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value added = arguments.get(1);
            return switch (arguments.getFirst()) {
                case PortValue queue when queue.eventQueue().isPresent() ->
                        queue.queued(added, AT_THE_END);
                case PortValue file when file.isAFile() -> {
                    if (refinements.contains("dup") || refinements.contains("only")) {
                        throw Raised.of(EvaluationFailure.BAD_REFINES,
                                "append on a file port is a write, and takes no dup or only");
                    }
                    yield theFileBehind(file, evaluator).appended(added);
                }
                case Value subject when Actions.of(subject).isPresent() ->
                        Actions.of(subject).orElseThrow().append(
                                askedOf(subject, arguments, refinements, evaluator, context));
                case Value anythingElse -> refuseTheDatatype(anythingElse);
            };
        };
    }
}
