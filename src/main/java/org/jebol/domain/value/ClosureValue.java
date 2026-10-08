package org.jebol.domain.value;

import java.util.List;

public final class ClosureValue extends DefinedFunctionValue {

    ClosureValue(
            AnyBlockValue spec, AnyBlockValue body, List<Parameter> parameters,
            List<String> localNames, Context closedOver, Context wordsSharedWithTheFunctionItWasMadeFrom) {
        super(spec, body, parameters, localNames, closedOver, wordsSharedWithTheFunctionItWasMadeFrom);
    }

    @Override
    public Datatype datatype() {
        return Datatype.CLOSURE;
    }

    @Override
    public ClosureValue sameKindRunning(AnyBlockValue anotherBody, Context closedOverInstead) {
        return new ClosureValue(spec(), anotherBody, parameters(), localNames(), closedOverInstead,
                Context.theWordsAFunctionDeclares());
    }

    @Override
    public Context aFreshCallFrame() {
        return Context.childOf(closedOver());
    }
}
