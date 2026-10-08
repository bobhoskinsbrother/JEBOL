package org.jebol.domain.value;

public sealed interface AnyFunctionValue extends Value permits DeclaresParameters, OperatorValue {

    @Override
    default Value make(Value spec, Maker maker) {
        if (!(spec instanceof AnyBlockValue block)) {
            throw refusedAsAPrototypeFor(spec);
        }
        return maker.makeFunctionFrom(this, block);
    }

    default Raised refusedAsAPrototypeFor(Value spec) {
        return Raised.of(EvaluationFailure.CANNOT_USE, spec, datatype());
    }
}
