package org.jebol.domain.eval;

import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.Slot;
import org.jebol.domain.value.Value;

final class MapDispatcher implements Dispatcher {

    @Override
    public Value readFrom(Value target, Value selector) {
        return mapIn(target).select(selector);
    }

    @Override
    public Slot placeWithin(Slot holder, Value selector) {
        MapValue map = mapIn(holder.value());
        return new MapSlot(map, selector, map.select(selector));
    }

    @Override
    public void writeTo(Slot place, Value selector, Value written) {
        MapValue map = mapIn(place.value());
        if (map.isProtected()) {
            throw Raised.of(EvaluationFailure.PROTECTED, "map is protected");
        }
        MapSlot.write(map, selector, written);
    }

    private static MapValue mapIn(Value target) {
        if (target instanceof MapValue map) {
            return map;
        }
        throw Raised.of(EvaluationFailure.BAD_PATH_TYPE,
                target.datatype().literalSpelling());
    }
}
