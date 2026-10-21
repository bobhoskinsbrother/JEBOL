package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;

public interface Maker {

    Value makeObjectFrom(ObjectValue prototype, BlockValue body);

    Value mergeObject(ObjectValue prototype, ObjectValue other);

    Value makeFunctionFrom(AnyFunctionValue prototype, AnyBlockValue spec);

    Value makeCommandFrom(AnyBlockValue definition);

    Value makeStructFrom(StructValue prototype, Value spec);

    ObjectValue evaluateObject(AnyBlockValue body);

    FunctionValue bindFunction(AnyBlockValue spec, AnyBlockValue body);

    Value applySystemFunction(String name, Value argument);

    Value readSimpleValue(Value piece);

    ErrorValue speakHere(ErrorValue error);

    StructSpec.LayoutRegistry structLayouts();

    Optional<List<Value>> readValuesFrom(String source);

    AnyBlockValue readSource(String source);
}
