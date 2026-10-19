package org.jebol.application;

import org.jebol.domain.host.MachinePort;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

public final class JavaMachine implements MachinePort {

    private static final String NAME_OF_UNNAMED_SYSTEM = "JVM";
    private static final char DEFAULT_SEPARATOR = '/';
    private static final String DATA_SWITCH = "--data";
    private static final String ROOT_SWITCH = "--root";
    private static final String CONSOLE = "org.jebol.adapter.cli.Repl";

    private final Properties properties;

    public JavaMachine() {
        this(System.getProperties());
    }

    JavaMachine(Properties properties) {
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
        return ProcessHandle.current().pid();
    }

    @Override
    public Value launcherFor(String root, String dataDirectory) {
        try {
            Path launcher = Files.createTempFile(Path.of(property("java.io.tmpdir")), "jebol-boot", ".sh");
            Files.writeString(launcher, writeLauncherScript(root, dataDirectory));
            if (!ExecutableFile.markRunnable(launcher.toFile())) {
                return NoneValue.none();
            }
            launcher.toFile().deleteOnExit();
            return FileValue.of(launcher.toString().replace('\\', '/'));
        } catch (IOException unwritable) {
            return NoneValue.none();
        }
    }

    private String writeLauncherScript(String root, String dataDirectory) {
        return "#!/bin/sh\nexec \"" + findJvm()
                + "\" -Djava.awt.headless=true -cp \"" + makeClasspathAbsolute()
                + "\" " + CONSOLE + " " + ROOT_SWITCH + " \"" + root + "\""
                + writeDataSwitch(dataDirectory)
                + " \"$@\"\n";
    }

    private String findJvm() {
        return ProcessHandle.current().info().command()
                .orElse(property("java.home") + "/bin/java");
    }

    private String makeClasspathAbsolute() {
        return Arrays.stream(property("java.class.path").split(File.pathSeparator))
                .filter(entry -> !entry.isEmpty())
                .map(entry -> Path.of(entry).toAbsolutePath().toString())
                .collect(Collectors.joining(File.pathSeparator));
    }

    private String writeDataSwitch(String dataDirectory) {
        return dataDirectory.isEmpty() ? "" : " " + DATA_SWITCH + " \"" + dataDirectory + "\"";
    }

    private String property(String name) {
        return properties.getProperty(name, "");
    }
}
