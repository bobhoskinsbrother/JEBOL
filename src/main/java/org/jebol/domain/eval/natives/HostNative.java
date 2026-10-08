package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.FileSystemCalls;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Value;

import java.util.function.Supplier;

public abstract class HostNative extends DefaultNative {

    protected final GrantedServices granted;
    private final FileSystemCalls fileSystem = new FileSystemCalls();

    protected HostNative(GrantedServices granted) {
        this.granted = granted;
    }

    protected Value throughTheFileSystem(Supplier<Value> operation) {
        return fileSystem.answeredOrRaised(operation);
    }
}
