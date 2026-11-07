package org.jebol.adapter.host;

import org.jebol.application.Extension;
import org.jebol.application.ExtensionCommand;
import org.jebol.application.ExtensionProvider;
import org.jebol.domain.value.StringValue;

import java.util.List;

public final class GreetingExtensionProvider implements ExtensionProvider {

    @Override
    public Extension provide() {
        return new Extension("greeting", "1.0.0", List.of(
                new ExtensionCommand("greet", "", call -> StringValue.of("hello from a jar"))));
    }
}
