package org.jebol.domain.value;

import org.jebol.domain.eval.ExtensionDispatcher;

import java.util.Optional;

public record DispatcherHandle(HandleValue handle) {

    public static final String THE_KIND_A_DISPATCHER_IS = "extension";

    public Optional<ExtensionDispatcher> dispatcher() {
        if (!handle.typeName().equals(THE_KIND_A_DISPATCHER_IS)
                || !(handle.payload() instanceof JavaObjectValue carried)) {
            return Optional.empty();
        }
        return carried.held()
                .filter(ExtensionDispatcher.class::isInstance)
                .map(ExtensionDispatcher.class::cast);
    }
}
