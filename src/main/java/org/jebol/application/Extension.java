package org.jebol.application;

import java.util.ArrayList;
import java.util.List;

public record Extension(
        String name,
        String version,
        List<ExtensionCommand> commands,
        String script,
        List<HandleKind> handleKinds,
        ExtensionStart start) {

    public Extension {
        if (name == null || version == null || commands == null || script == null
                || handleKinds == null || start == null) {
            throw new IllegalArgumentException("an extension needs every part; use an empty one to have none");
        }
        commands = List.copyOf(commands);
        handleKinds = List.copyOf(handleKinds);
    }

    public Extension(String name, String version, List<ExtensionCommand> commands) {
        this(name, version, commands, "", List.of(), postbox -> {
        });
    }

    public Extension withScript(String rebol) {
        return new Extension(name, version, commands, rebol, handleKinds, start);
    }

    public Extension withHandleKind(HandleKind kind) {
        List<HandleKind> more = new ArrayList<>(handleKinds);
        more.add(kind);
        return new Extension(name, version, commands, script, more, start);
    }

    public Extension startingWith(ExtensionStart starting) {
        return new Extension(name, version, commands, script, handleKinds, starting);
    }
}
