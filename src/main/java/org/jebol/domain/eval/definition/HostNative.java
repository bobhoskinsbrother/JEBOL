package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.FilePort;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.value.Value;

import java.util.function.Supplier;

public abstract class HostNative extends DefaultNative {

    protected final GrantedServices granted;

    protected HostNative(GrantedServices granted) {
        this.granted = granted;
    }

    protected Value throughTheFileSystem(Supplier<Value> operation) {
        try {
            return operation.get();
        } catch (FilePort.Denied denied) {
            throw denied.raised();
        }
    }
}
