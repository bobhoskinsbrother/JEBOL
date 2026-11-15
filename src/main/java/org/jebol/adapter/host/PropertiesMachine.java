package org.jebol.adapter.host;

import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.MachinePort;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;

import java.util.Properties;

public final class PropertiesMachine implements MachinePort {

    private static final String NAME_OF_UNNAMED_SYSTEM = "JVM";
    private static final char DEFAULT_SEPARATOR = '/';

    private final Properties properties;

    public PropertiesMachine() {
        this(System.getProperties());
    }

    public PropertiesMachine(Properties properties) {
        this.properties = properties;
    }

    @Override
    public String operatingSystem() {
        String reported = property("os.name");
        if (reported.startsWith("Windows")) {
            return "Windows";
        }
        if (reported.startsWith("Mac")) {
            return "macOS";
        }
        if (reported.startsWith("Linux")) {
            return "Linux";
        }
        return reported.isEmpty() ? NAME_OF_UNNAMED_SYSTEM : reported;
    }

    @Override
    public char fileSeparator() {
        String reported = property("file.separator");
        return reported.isEmpty() ? DEFAULT_SEPARATOR : reported.charAt(0);
    }

    @Override
    public String homeDirectory() {
        return property("user.home");
    }

    @Override
    public String currentDirectory() {
        return property("user.dir");
    }

    @Override
    public long processNumber() {
        throw new FilePort.Denied("no-port", "this machine has no process number to give");
    }

    @Override
    public Value launcherFor(String root, String dataDirectory) {
        return NoneValue.none();
    }

    String property(String name) {
        return properties.getProperty(name, "");
    }
}
