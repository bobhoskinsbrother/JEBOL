package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.Postbox;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.EventValue;
import org.jebol.domain.value.JavaObjectValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class DoCallbackNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "do-callback";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("event", Set.of(EventValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            EventValue event = (EventValue) arguments.getFirst();
            if (event.model() == EventValue.Model.CALLBACK
                    && event.attached() instanceof JavaObjectValue carried
                    && carried.held().orElse(null) instanceof Postbox.Callback callback) {
                return answer(callback, evaluator);
            }
            return NoneValue.none();
        };
    }

    private Value answer(Postbox.Callback callback, Evaluator evaluator) {
        try {
            Value answer = evaluator.applyFunction(
                    callback.target().context().readValueAt(callback.word()), callback.arguments());
            callback.answer().answer(answer);
            return answer;
        } catch (RuntimeException failed) {
            callback.answer().fail(failed);
            throw failed;
        }
    }
}
