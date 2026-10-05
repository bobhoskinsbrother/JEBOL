package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;

import java.util.List;

public abstract class OrdinalNative extends DefaultNative {

    protected abstract int position();

    @Override
    public List<Parameter> parameters() {
        return acceptsWhateverComesAlong("series");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                arguments.getFirst().picked(position());
    }
}
