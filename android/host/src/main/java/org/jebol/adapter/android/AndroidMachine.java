package org.jebol.adapter.android;

import org.jebol.adapter.host.PropertiesMachine;
import org.jebol.domain.host.MachinePort;
import org.jebol.domain.value.Value;

public final class AndroidMachine implements MachinePort {

    private final PropertiesMachine properties = new PropertiesMachine();

    @Override
    public String operatingSystem() {
        return properties.operatingSystem();
    }

    @Override
    public char fileSeparator() {
        return properties.fileSeparator();
    }

    @Override
    public String homeDirectory() {
        return properties.homeDirectory();
    }

    @Override
    public String currentDirectory() {
        return properties.currentDirectory();
    }

    @Override
    public long processNumber() {
        return android.os.Process.myPid();
    }

    @Override
    public Value launcherFor(String root, String dataDirectory) {
        return properties.launcherFor(root, dataDirectory);
    }
}
