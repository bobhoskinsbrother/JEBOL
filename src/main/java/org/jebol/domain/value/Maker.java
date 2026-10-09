package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;

public interface Maker {

    Value makeObjectFrom(ObjectValue prototype, BlockValue body);

    Value objectMergedFrom(ObjectValue prototype, ObjectValue other);

    Value makeFunctionFrom(AnyFunctionValue prototype, AnyBlockValue spec);

    Value makeStructFrom(StructValue prototype, Value spec);

    ObjectValue objectEvaluatedFrom(AnyBlockValue body);

    FunctionValue functionBoundFrom(AnyBlockValue spec, AnyBlockValue body);

    Value systemFunctionApplied(String name, Value argument);

    Value simpleValueOf(Value piece);

    ErrorValue spokenHere(ErrorValue error);

    StructSpec.LayoutRegistry structLayouts();

    Optional<List<Value>> valuesReadFrom(String source);

    AnyBlockValue sourceRead(String source);
}
