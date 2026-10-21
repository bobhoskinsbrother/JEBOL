package org.jebol.domain.value;

import org.jebol.domain.eval.ExtensionDispatcher;

import java.util.Optional;

public record DispatcherHandle(HandleValue handle) {

    public static final String KIND_DISPATCHER_IS = "extension";

    public Optional<ExtensionDispatcher> findDispatcher() {
        if (!handle.typeName().equals(KIND_DISPATCHER_IS)
                || !(handle.payload() instanceof JavaObjectValue carried)) {
            return Optional.empty();
        }
        return carried.held()
                .filter(ExtensionDispatcher.class::isInstance)
                .map(ExtensionDispatcher.class::cast);
    }
}
