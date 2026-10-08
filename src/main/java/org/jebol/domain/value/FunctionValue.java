package org.jebol.domain.value;

import java.util.List;

public final class FunctionValue extends DefinedFunctionValue {

    public FunctionValue(
            AnyBlockValue spec, AnyBlockValue body, List<Parameter> parameters,
            List<String> localNames, Context closedOver) {
        super(spec, body, parameters, localNames, closedOver, Context.theWordsAFunctionDeclares());
    }

    @Override
    public Datatype datatype() {
        return Datatype.FUNCTION;
    }

    @Override
    public FunctionValue sameKindRunning(AnyBlockValue anotherBody, Context closedOverInstead) {
        return new FunctionValue(spec(), anotherBody, parameters(), localNames(), closedOverInstead);
    }

    @Override
    public Context aFreshCallFrame() {
        Context frame = Context.childOf(closedOver());
        frame.markAsCallFrameOf(this);
        return frame;
    }

    public ClosureValue asClosure() {
        return new ClosureValue(spec(), body(), parameters(), localNames(), closedOver(), declaredWords());
    }
}
