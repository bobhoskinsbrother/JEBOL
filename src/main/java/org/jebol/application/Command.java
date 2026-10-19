package org.jebol.application;

import org.jebol.domain.eval.Postbox;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public interface Command {

    List<Value> arguments();

    Set<String> refinements();

    Value evaluate(AnyBlockValue block);

    HandleValue makeHandle(String type, Object held);

    <T> T unwrap(Value handle, Class<T> type);

    Raised failure(ErrorCategory category, String id, Value... arguments);

    Postbox postbox();
}
