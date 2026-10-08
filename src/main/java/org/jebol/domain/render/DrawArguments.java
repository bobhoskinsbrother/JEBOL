package org.jebol.domain.render;

import org.jebol.domain.value.AnyDecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;

import java.util.List;
import java.util.Optional;

final class DrawArguments {

    List<PairValue> everyPairIn(List<Value> arguments) {
        return arguments.stream()
                .filter(PairValue.class::isInstance)
                .map(PairValue.class::cast)
                .toList();
    }

    Optional<Colour> colourAt(List<Value> arguments, int slot) {
        return slot < arguments.size() && arguments.get(slot) instanceof TupleValue parts
                ? Optional.of(Colour.ofTuple(parts))
                : Optional.empty();
    }

    Optional<Colour> colourIn(List<Value> arguments) {
        return colourAt(arguments, 0);
    }

    Optional<PairValue> pairAt(List<Value> arguments, int slot) {
        return slot < arguments.size() && arguments.get(slot) instanceof PairValue pair
                ? Optional.of(pair)
                : Optional.empty();
    }

    Optional<Double> numberAt(List<Value> arguments, int slot) {
        if (slot >= arguments.size()) {
            return Optional.empty();
        }
        return switch (arguments.get(slot)) {
            case AnyDecimalValue fraction -> Optional.of(fraction.quantity());
            case IntegerValue whole -> Optional.of((double) whole.magnitude());
            default -> Optional.empty();
        };
    }

    Optional<String> wordAt(List<Value> arguments, int slot) {
        return slot < arguments.size() && arguments.get(slot) instanceof AnyWordValue word
                ? Optional.of(word.canonical())
                : Optional.empty();
    }

    double numberIn(List<Value> arguments, double whenAbsent) {
        for (Value each : arguments) {
            if (each instanceof AnyDecimalValue fraction) {
                return fraction.quantity();
            }
            if (each instanceof IntegerValue(long magnitude)) {
                return magnitude;
            }
        }
        return whenAbsent;
    }

    List<Double> everyNumberIn(List<Value> arguments) {
        return arguments.stream()
                .filter(one -> one instanceof AnyDecimalValue || one instanceof IntegerValue)
                .map(this::asNumber)
                .toList();
    }

    double asNumber(Value value) {
        return switch (value) {
            case AnyDecimalValue fraction -> fraction.quantity();
            case IntegerValue whole -> whole.magnitude();
            default -> 0;
        };
    }
}
