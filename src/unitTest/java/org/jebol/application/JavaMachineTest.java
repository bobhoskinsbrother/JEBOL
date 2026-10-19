package org.jebol.application;

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

    private JavaMachine machineWith(String... namesAndValues) {
        Properties properties = new Properties();
        for (int at = 0; at + 1 < namesAndValues.length; at += 2) {
            properties.setProperty(namesAndValues[at], namesAndValues[at + 1]);
        }
        return new JavaMachine(properties);
    }

    private Path launcherPath(Value launcher) {
        assertThat(List.of(launcher)).first().isInstanceOf(FileValue.class);
        return Path.of(((FileValue) launcher).text());
    }

    @ParameterizedTest(name = "[{0}] is {1}")
    @CsvSource(delimiter = '|', value = {
            "Windows 11    | Windows",
            "Windows       | Windows",
            "Mac OS X      | macOS",
            "Mac           | macOS",
            "Linux         | Linux",
            "FreeBSD       | FreeBSD",
            "windows 10    | windows 10",
            "linux         | linux",
    })
    @DisplayName("the operating system is Rebol's word for the three it knows, and the system's own name otherwise")
    void namesOperatingSystem(String reported, String named) {
        assertThat(machineWith("os.name", reported).operatingSystem()).isEqualTo(named);
    }

    @Test
    @DisplayName("an operating system that reports no name is JVM")
    void namesUnnamedSystemJvm() {
        assertThat(machineWith("os.name", "").operatingSystem()).isEqualTo("JVM");
        assertThat(machineWith().operatingSystem()).isEqualTo("JVM");
    }

    @Test
    @DisplayName("the separator is the one the JVM reports, a backslash or a slash")
    void readsSeparator() {
        assertThat(machineWith("file.separator", "\\").fileSeparator()).isEqualTo('\\');
        assertThat(machineWith("file.separator", "/").fileSeparator()).isEqualTo('/');
    }

    @Test
    @DisplayName("a JVM that reports no separator is taken to use a slash")
    void defaultsSeparatorToSlash() {
        assertThat(machineWith().fileSeparator()).isEqualTo('/');
        assertThat(machineWith("file.separator", "").fileSeparator()).isEqualTo('/');
    }

    @Test
    @DisplayName("home and the current directory are the JVM's user.home and user.dir, or empty where it reports none")
    void readsDirectories() {
        JavaMachine machine = machineWith("user.home", "/home/someone", "user.dir", "/work");

        assertThat(machine.homeDirectory()).isEqualTo("/home/someone");
        assertThat(machine.currentDirectory()).isEqualTo("/work");
        assertThat(machineWith().homeDirectory()).isEmpty();
        assertThat(machineWith().currentDirectory()).isEmpty();
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
    @DisplayName("a launcher that cannot be written is none")
    void answersNoneWhenUnwritable(@TempDir Path directory) {
        JavaMachine machine = machineWith("java.io.tmpdir", directory.resolve("missing").toString());

        assertThat(List.of(machine.launcherFor("/", ""))).containsExactly(NoneValue.none());
    }
}
