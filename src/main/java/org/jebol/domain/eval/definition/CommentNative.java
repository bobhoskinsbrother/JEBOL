package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.UnsetValue;

import java.util.List;

public class CommentNative extends DefaultNative {

    @Override
    public String name() {
        return "comment";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsWhateverComesAlong("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> UnsetValue.unset();
    }
}
