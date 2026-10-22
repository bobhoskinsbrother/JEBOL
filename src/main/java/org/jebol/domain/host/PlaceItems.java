package org.jebol.domain.host;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;

final class PlaceItems {

    List<Value> copyEach(List<Value> given) {
        return given.stream().map(this::copy).toList();
    }

    Value copy(Value given) {
        return switch (given) {
            case BlockValue block -> BlockValue.of(copyEach(block.listItems()));
            case StringValue text -> StringValue.of(text.text());
            case WordValue word -> word;
            case IntegerValue whole -> whole;
            case DecimalValue decimal -> decimal;
            case LogicValue logic -> logic;
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG, given);
        };
    }
}
