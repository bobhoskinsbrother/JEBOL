package org.jebol.adapter.host;

import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class JavaMachineTest {

    private static Properties propertiesWith(String... namesAndValues) {
        Properties properties = new Properties();
        for (int at = 0; at + 1 < namesAndValues.length; at += 2) {
            properties.setProperty(namesAndValues[at], namesAndValues[at + 1]);
        }
        return properties;
    }

    private JavaMachine machineWith(String... namesAndValues) {
        return new JavaMachine(propertiesWith(namesAndValues));
    }

    private Path launcherPath(Value launcher) {
        assertThat(List.of(launcher)).first().isInstanceOf(FileValue.class);
        return Path.of(((FileValue) launcher).text());
    }

    @ParameterizedTest(name = "[{0}] [{1}] [{2}] [{3}]")
    @CsvSource(delimiter = '|', value = {
            "Mac OS X | /     | /Users/someone | /work",
            "Windows 11 | \\  | C:\\Users\\x   | C:\\work",
            "''       | ''    | ''             | ''",
    })
    @DisplayName("the system, the separator, home and the current directory are what PropertiesMachine reads from the same properties")
    void answersAsThePropertiesDo(String system, String separator, String home, String current) {
        String[] given = {"os.name", system, "file.separator", separator, "user.home", home, "user.dir", current};
        JavaMachine machine = machineWith(given);
        PropertiesMachine reading = new PropertiesMachine(propertiesWith(given));

        assertThat(machine.operatingSystem()).isEqualTo(reading.operatingSystem());
        assertThat(machine.fileSeparator()).isEqualTo(reading.fileSeparator());
        assertThat(machine.homeDirectory()).isEqualTo(reading.homeDirectory());
        assertThat(machine.currentDirectory()).isEqualTo(reading.currentDirectory());
    }

    @Test
    @DisplayName("the process number is this JVM's own")
    void numbersThisProcess() {
        assertThat(new JavaMachine().processNumber()).isEqualTo(ProcessHandle.current().pid());
    }

    @Test
    @DisplayName("a launcher is a runnable script that starts the console rooted where it was asked, with no data switch when none was given")
    void writesLauncherWithoutData(@TempDir Path directory) throws IOException {
        JavaMachine machine = machineWith("java.io.tmpdir", directory.toString(), "java.class.path", "lib.jar");

        Path launcher = launcherPath(machine.launcherFor("/srv/root", ""));

        assertThat(launcher).startsWith(directory).isExecutable();
        assertThat(Files.readString(launcher))
                .startsWith("#!/bin/sh")
                .contains("org.jebol.adapter.cli.Repl --root \"/srv/root\"")
                .doesNotContain("--data");
    }

    @Test
    @DisplayName("a launcher told a data directory passes it on")
    void writesLauncherWithData(@TempDir Path directory) throws IOException {
        JavaMachine machine = machineWith("java.io.tmpdir", directory.toString(), "java.class.path", "lib.jar");

        Path launcher = launcherPath(machine.launcherFor("/srv/root", "/srv/root/.jebol/"));

        assertThat(Files.readString(launcher)).contains("--data \"/srv/root/.jebol/\"");
    }

    @Test
    @DisplayName("on Windows a launcher is a command script that passes every argument on and leaves with java's status")
    void writesACommandScriptOnWindows(@TempDir Path directory) throws IOException {
        JavaMachine machine = machineWith("java.io.tmpdir", directory.toString(), "java.class.path", "lib.jar",
                "file.separator", "\\");

        Path launcher = launcherPath(machine.launcherFor("/srv/root", "/srv/root/.jebol/"));

        assertThat(launcher.getFileName().toString()).endsWith(".cmd");
        assertThat(Files.readString(launcher).lines().toList())
                .first().asString()
                .startsWith("@\"")
                .contains("org.jebol.adapter.cli.Repl --root \"/srv/root\" --data \"/srv/root/.jebol/\" %*");
        assertThat(Files.readString(launcher).lines().toList())
                .last().asString().isEqualTo("@exit /b %errorlevel%");
    }

    @Test
    @DisplayName("and a percent sign in what it names is doubled, so cmd reads it as itself")
    void doublesAPercentSignOnWindows(@TempDir Path directory) throws IOException {
        JavaMachine machine = machineWith("java.io.tmpdir", directory.toString(), "java.class.path", "lib%1.jar",
                "file.separator", "\\");

        Path launcher = launcherPath(machine.launcherFor("/srv/100%/", ""));

        assertThat(Files.readString(launcher))
                .contains("lib%%1.jar")
                .contains("--root \"/srv/100%%/\"");
    }

    @Test
    @DisplayName("a launcher that cannot be written is none")
    void answersNoneWhenUnwritable(@TempDir Path directory) {
        JavaMachine machine = machineWith("java.io.tmpdir", directory.resolve("missing").toString());

        assertThat(List.of(machine.launcherFor("/", ""))).containsExactly(NoneValue.none());
    }
}
