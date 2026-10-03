package org.jebol.domain.value;

public interface Maker {

    Value make(Datatype kind, Value spec);

    Value makeAnother(Datatype kind, Value spec);

    Value makeObjectFrom(ObjectValue prototype, Value spec);

    Value deriveFunction(Value function, BlockValue spec);

    Value makeError(Value spec);

    Value makeStructFrom(StructValue prototype, Value spec);

    Value makeEventFrom(EventValue prototype, Value spec);
}
