package org.jebol.application;

public record ExtensionCommand(String name, String spec, CommandBehaviour behaviour) {

    public ExtensionCommand {
        if (name == null || spec == null || behaviour == null) {
            throw new IllegalArgumentException("a command needs a name, a spec and a behaviour; a spec may be empty");
        }
    }
}
