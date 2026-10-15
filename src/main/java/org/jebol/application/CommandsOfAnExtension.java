package org.jebol.application;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.ExtensionDispatcher;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

final class CommandsOfAnExtension implements ExtensionDispatcher {

    private final Extension extension;

    private final AtomicInteger handlesMade = new AtomicInteger();

    CommandsOfAnExtension(Extension extension) {
        this.extension = extension;
    }

    @Override
    public Value dispatch(long command, List<Value> arguments, Set<String> refinements,
            Evaluator evaluator, Context context) {
        if (command < 0 || command >= extension.commands().size()) {
            throw Raised.of(EvaluationFailure.COMMAND_FAIL,
                    StringValue.of(extension.name() + " has no command " + command));
        }
        OneCommandCall call = new OneCommandCall(
                extension, arguments, refinements, evaluator, context, handlesMade);
        try {
            return extension.commands().get((int) command).behaviour().answer(call);
        } catch (Raised raised) {
            throw raised;
        } catch (RuntimeException failed) {
            if (call.escapedFromTheScript(failed)) {
                throw failed;
            }
            throw Raised.of(EvaluationFailure.COMMAND_FAIL, StringValue.of(theMessageOf(failed)));
        }
    }

    private String theMessageOf(RuntimeException failed) {
        return failed.getMessage() == null ? failed.getClass().getSimpleName() : failed.getMessage();
    }
}
