package org.jebol.domain.value;

public interface Maker {

    Value make(Datatype kind, Value spec);

    Value makeAnotherFrom(Datatype kind, Value spec);

    Value makeObjectFrom(ObjectValue prototype, Value spec);

    Value makeFunctionFrom(Value function, BlockValue spec);

    Value makeErrorFrom(Value spec);

    Value makeStructFrom(StructValue prototype, Value spec);

    Value makeEventFrom(EventValue prototype, Value spec);
}
