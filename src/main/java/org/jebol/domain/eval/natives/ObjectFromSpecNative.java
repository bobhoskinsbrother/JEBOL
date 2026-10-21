package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public abstract class ObjectFromSpecNative extends DefaultNative {

    protected abstract String nameTheSpec();

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of(nameTheSpec(), Set.of(BlockValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("only");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                makeObjectFrom((AnyBlockValue) arguments.getFirst(), evaluator, context);
    }

    protected ObjectValue makeObjectFrom(
            AnyBlockValue spec, Evaluator evaluator, Context enclosing) {

        return evaluator.evaluateInto(evaluator.makeFreshObjectWithin(enclosing), spec);
    }

}
