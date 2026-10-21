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

final class ExtensionCommands implements ExtensionDispatcher {

    private final Extension extension;

    private final AtomicInteger handlerCount = new AtomicInteger();

    ExtensionCommands(Extension extension) {
        this.extension = extension;
    }

    @Override
    public Value dispatch(long command, List<Value> arguments, Set<String> refinements,
            Evaluator evaluator, Context context) {
        if (command < 0 || command >= extension.commands().size()) {
            throw Raised.of(EvaluationFailure.COMMAND_FAIL,
                    StringValue.of(extension.name() + " has no command " + command));
        }
        SingleCommand call = new SingleCommand(
                extension, arguments, refinements, evaluator, context, handlerCount);
        try {
            return extension.commands().get((int) command).behaviour().answer(call);
        } catch (Raised raised) {
            throw raised;
        } catch (RuntimeException failed) {
            if (call.escapesFromScript(failed)) {
                throw failed;
            }
            throw Raised.of(EvaluationFailure.COMMAND_FAIL, StringValue.of(messageFrom(failed)));
        }
    }

    private String messageFrom(RuntimeException failed) {
        return failed.getMessage() == null ? failed.getClass().getSimpleName() : failed.getMessage();
    }
}
