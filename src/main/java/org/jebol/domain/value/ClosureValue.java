package org.jebol.domain.value;

import java.util.List;

public final class ClosureValue extends DefinedFunctionValue {

    public static final Datatype TYPE = new DefinedFunctionDatatype("closure") {

        @Override
        Value fromTheFunction(FunctionValue made) {
            return made.asClosure();
        }
    };

    ClosureValue(
            AnyBlockValue spec, AnyBlockValue body, List<Parameter> parameters,
            List<String> localNames, Context closedOver, Context wordsSharedWithTheFunctionItWasMadeFrom) {
        super(spec, body, parameters, localNames, closedOver, wordsSharedWithTheFunctionItWasMadeFrom);
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    public ClosureValue makeSameKindRunning(AnyBlockValue anotherBody, Context closedOverInstead) {
        return new ClosureValue(spec(), anotherBody, parameters(), localNames(), closedOverInstead,
                Context.collectTheWordsAFunctionDeclares());
    }

    @Override
    public Context makeFreshCallFrame() {
        return Context.makeChildOf(closedOver());
    }
}
