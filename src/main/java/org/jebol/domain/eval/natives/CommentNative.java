package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.UnsetValue;

import java.util.List;

public class CommentNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "comment";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWhateverComesAlong("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> UnsetValue.unset();
    }
}
