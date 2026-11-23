package org.jebol.adapter.host;

import java.util.ArrayList;
import java.util.List;

final class ShellCommands {

    private static final String QUOTE = "\"";

    private ShellCommands() {
    }

    static List<String> asShellCommand(List<String> command, boolean throughShell, boolean onWindows) {
        if (!throughShell) {
            return command;
        }
        return onWindows ? wrapForCmd(command) : wrapForSh(command);
    }

    private static List<String> wrapForCmd(List<String> command) {
        return List.of("cmd.exe", "/C", QUOTE + String.join(" ", command) + QUOTE);
    }

    private static List<String> wrapForSh(List<String> command) {
        List<String> withShell = new ArrayList<>(List.of("/bin/sh", "-c"));
        withShell.addAll(command);
        return withShell;
    }
}
