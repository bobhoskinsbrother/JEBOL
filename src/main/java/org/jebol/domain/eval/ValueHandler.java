package org.jebol.domain.eval;

import org.jebol.domain.value.Value;

public interface ValueHandler {

    boolean shouldHandle(Value left, Value right);

}
