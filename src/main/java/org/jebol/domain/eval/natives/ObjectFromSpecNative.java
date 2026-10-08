package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public abstract class ObjectFromSpecNative extends DefaultNative {

    protected abstract String whatTheSpecIsCalled();

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required(whatTheSpecIsCalled(), Set.of(Datatype.BLOCK)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("only");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                objectMadeFrom((BlockValue) arguments.getFirst(), evaluator, context);
    }

    protected ObjectValue objectMadeFrom(
            BlockValue spec, Evaluator evaluator, Context enclosing) {

        return evaluator.evaluatedInto(evaluator.freshObjectWithin(enclosing), spec);
    }

}
