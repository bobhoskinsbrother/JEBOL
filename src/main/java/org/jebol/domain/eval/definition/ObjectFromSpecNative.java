package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class ObjectFromSpecNative extends DefaultNative {

    protected abstract String whatTheSpecIsCalled();

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required(whatTheSpecIsCalled(), Set.of(Datatype.BLOCK)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("only");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                objectMadeFrom((BlockValue) arguments.getFirst(), evaluator, context);
    }

    protected ObjectValue objectMadeFrom(
            BlockValue spec, Evaluator evaluator, Context enclosing) {

        Context fields = Context.childOf(enclosing);
        ObjectValue built = new ObjectValue(fields);
        fields.set("self", built);
        spec.setWordsFromHere().stream().map(WordValue::spelling).forEach(fields::define);
        evaluator.evaluateOrRaise(
                Binder.bindOnly(spec, fields, itsOwnFieldNames(fields)), fields);
        return built;
    }

    private Set<String> itsOwnFieldNames(Context fields) {
        return fields.slots().stream()
                .map(ContextSlot::canonical)
                .collect(Collectors.toSet());
    }
}
