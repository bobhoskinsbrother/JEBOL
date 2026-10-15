package org.jebol.domain.eval;

import org.jebol.domain.value.Context;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public interface ExtensionDispatcher {

    Value dispatch(long command, List<Value> arguments, Set<String> refinements,
            Evaluator evaluator, Context context);
}
