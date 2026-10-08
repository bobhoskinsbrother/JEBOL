package org.jebol.domain.eval;

import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.ModuleValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.TaskValue;
import org.jebol.domain.value.TupleValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class PathDispatch {

    private final Map<Datatype, Dispatcher> byDatatype = new HashMap<>();

    PathDispatch() {
        Dispatcher context = new ContextDispatcher();
        byDatatype.put(ObjectValue.TYPE, context);
        byDatatype.put(PortValue.TYPE, context);
        byDatatype.put(ModuleValue.TYPE, context);
        byDatatype.put(TaskValue.TYPE, context);
        byDatatype.put(MapValue.TYPE, new MapDispatcher());
        byDatatype.put(DateValue.TYPE, new DateDispatcher());
        byDatatype.put(PairValue.TYPE, new PairDispatcher());
        byDatatype.put(TupleValue.TYPE, new TupleDispatcher());
    }

    Optional<Dispatcher> forDatatype(Datatype datatype) {
        return Optional.ofNullable(byDatatype.get(datatype));
    }
}
