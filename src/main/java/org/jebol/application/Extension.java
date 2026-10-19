package org.jebol.application;

import java.util.ArrayList;
import java.util.List;

public record Extension(
        String name,
        String version,
        List<ExtensionCommand> commands,
        String script,
        List<HandleType> handleTypes,
        StartExtension start) {

    public Extension {
        if (name == null || version == null || commands == null || script == null
                || handleTypes == null || start == null) {
            throw new IllegalArgumentException("an extension needs every part; use an empty one to have none");
        }
        commands = List.copyOf(commands);
        handleTypes = List.copyOf(handleTypes);
    }

    public Extension(String name, String version, List<ExtensionCommand> commands) {
        this(name, version, commands, "", List.of(), postbox -> {
        });
    }

    public Extension withScript(String rebol) {
        return new Extension(name, version, commands, rebol, handleTypes, start);
    }

    public Extension withHandleType(HandleType type) {
        List<HandleType> more = new ArrayList<>(handleTypes);
        more.add(type);
        return new Extension(name, version, commands, script, more, start);
    }

    public Extension startWith(StartExtension extension) {
        return new Extension(name, version, commands, script, handleTypes, extension);
    }
}
