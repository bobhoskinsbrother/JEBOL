package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EventValue;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class MapEventNative extends GobMappingNative {

    @Override
    public String nativeName() {
        return "map-event";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("event", Set.of(Datatype.EVENT)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                mappedEvent((EventValue) arguments.getFirst());
    }

    private Value mappedEvent(EventValue event) {
        if (!(event.attached() instanceof GobValue gob) || !event.has(EventValue.Flag.HAS_XY)) {
            return event;
        }
        List<Value> gobAndPoint = ((BlockValue) mappedInwards(gob,
                PairValue.of(event.offsetX(), event.offsetY()))).remaining();
        PairValue inside = (PairValue) gobAndPoint.get(1);
        return event
                .withAttached(EventValue.Model.GUI, gobAndPoint.getFirst())
                .withData(EventValue.packedOffset(
                                (int) Math.round(inside.x()), (int) Math.round(inside.y())),
                        EventValue.Flag.HAS_XY);
    }
}
