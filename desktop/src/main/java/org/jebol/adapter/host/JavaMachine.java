package org.jebol.adapter.host;

import org.jebol.domain.eval.LocalFileConvention;
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

    private static final String DATA_SWITCH = "--data";
    private static final String ROOT_SWITCH = "--root";
    private static final String CONSOLE = "org.jebol.adapter.cli.Repl";
    private static final char WINDOWS_SEPARATOR = '\\';

    private final PropertiesMachine reported;

    public JavaMachine() {
        this(System.getProperties());
    }

    JavaMachine(Properties properties) {
        this.reported = new PropertiesMachine(properties);
    }

    @Override
    public String operatingSystem() {
        return reported.operatingSystem();
    }

    @Override
    public char fileSeparator() {
        return reported.fileSeparator();
    }

    @Override
    public String homeDirectory() {
        return reported.homeDirectory();
    }

    @Override
    public String currentDirectory() {
        return reported.currentDirectory();
    }

    @Override
    public long processNumber() {
        return ProcessHandle.current().pid();
    }

    @Override
    public Value launcherFor(String root, String dataDirectory) {
        boolean onWindows = fileSeparator() == WINDOWS_SEPARATOR;
        try {
            Path launcher = Files.createTempFile(Path.of(property("java.io.tmpdir")), "jebol-boot",
                    onWindows ? ".cmd" : ".sh");
            Files.writeString(launcher, onWindows
                    ? writeCommandScript(root, dataDirectory)
                    : writeLauncherScript(root, dataDirectory));
            if (!ExecutableFile.markRunnable(launcher.toFile())) {
                return NoneValue.none();
            }
            launcher.toFile().deleteOnExit();
            return FileValue.of(LocalFileConvention.forSeparator(fileSeparator())
                    .readLocalPath(launcher.toString())
                    .orElse(launcher.toString().replace('\\', '/')));
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

    private String writeCommandScript(String root, String dataDirectory) {
        String startingJava = "@\"" + findJvm()
                + "\" -Djava.awt.headless=true --enable-native-access=ALL-UNNAMED -cp \"" + makeClasspathAbsolute()
                + "\" " + CONSOLE + " " + ROOT_SWITCH + " \"" + doubleTrailingBackslashes(root) + "\""
                + writeDataSwitch(doubleTrailingBackslashes(dataDirectory));
        return startingJava.replace("%", "%%") + " %*\r\n@exit /b %errorlevel%\r\n";
    }

    private static String doubleTrailingBackslashes(String quoted) {
        int end = quoted.length();
        while (end > 0 && quoted.charAt(end - 1) == WINDOWS_SEPARATOR) {
            end--;
        }
        return quoted + quoted.substring(end);
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
        return reported.property(name);
    }
}
