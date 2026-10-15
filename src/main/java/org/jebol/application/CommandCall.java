package org.jebol.application;

import org.jebol.domain.eval.Postbox;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public interface CommandCall {

    List<Value> arguments();

    Set<String> refinements();

    Value evaluated(AnyBlockValue block);

    HandleValue handleOf(String kind, Object held);

    <T> T heldBy(Value handle, Class<T> type);

    Raised failure(ErrorCategory category, String id, Value... arguments);

    Postbox postbox();
}
