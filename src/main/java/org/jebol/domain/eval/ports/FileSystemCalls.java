package org.jebol.domain.eval.ports;

import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.Value;

import java.util.function.Supplier;

public final class FileSystemCalls {

    public Value answerOrRaise(Supplier<Value> operation) {
        try {
            return operation.get();
        } catch (FilePort.Denied denied) {
            throw denied.raise();
        }
    }
}
