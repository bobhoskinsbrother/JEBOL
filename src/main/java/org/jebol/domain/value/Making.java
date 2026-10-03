package org.jebol.domain.value;

public interface Making {

    Value made(Datatype kind, Value spec);

    Value madeFromAValueOf(Datatype kind, Value spec);

    Value objectLike(ObjectValue prototype, Value spec);

    Value derivedFrom(Value function, BlockValue spec);

    Value errorFrom(Value spec);

    Value structLike(StructValue prototype, Value spec);

    Value eventLike(EventValue prototype, Value spec);
}
