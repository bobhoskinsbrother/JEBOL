package org.jebol.domain.eval;

import org.jebol.domain.value.Value;

import java.util.function.Supplier;

public final class FileSystemCalls {

    public Value answeredOrRaised(Supplier<Value> operation) {
        try {
            return operation.get();
        } catch (FilePort.Denied denied) {
            throw denied.raised();
        }
    }
}
