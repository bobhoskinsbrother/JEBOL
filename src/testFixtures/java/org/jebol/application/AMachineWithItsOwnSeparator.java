package org.jebol.application;

import org.jebol.domain.host.MachinePort;
import org.jebol.domain.value.Value;

public final class AMachineWithItsOwnSeparator implements MachinePort {

    private final MachinePort machine;
    private final char separator;

    public AMachineWithItsOwnSeparator(MachinePort machine, char separator) {
        this.machine = machine;
        this.separator = separator;
    }

    @Override
    public String operatingSystem() {
        return machine.operatingSystem();
    }

    @Override
    public char fileSeparator() {
        return separator;
    }

    @Override
    public String homeDirectory() {
        return machine.homeDirectory();
    }

    @Override
    public String currentDirectory() {
        return machine.currentDirectory();
    }

    @Override
    public long processNumber() {
        return machine.processNumber();
    }

    @Override
    public Value launcherFor(String root, String dataDirectory) {
        return machine.launcherFor(root, dataDirectory);
    }
}
