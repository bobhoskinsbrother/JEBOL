package org.jebol.domain.eval;

import org.jebol.domain.value.Datatype;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

final class PathDispatch {

    private final Map<Datatype, Dispatcher> byDatatype = new EnumMap<>(Datatype.class);

    PathDispatch() {
        Dispatcher context = new ContextDispatcher();
        byDatatype.put(Datatype.OBJECT, context);
        byDatatype.put(Datatype.PORT, context);
        byDatatype.put(Datatype.MODULE, context);
        byDatatype.put(Datatype.TASK, context);
        byDatatype.put(Datatype.MAP, new MapDispatcher());
        byDatatype.put(Datatype.DATE, new DateDispatcher());
        byDatatype.put(Datatype.PAIR, new PairDispatcher());
        byDatatype.put(Datatype.TUPLE, new TupleDispatcher());
    }

    Optional<Dispatcher> forDatatype(Datatype datatype) {
        return Optional.ofNullable(byDatatype.get(datatype));
    }
}
