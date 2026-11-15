package org.jebol.adapter.host;

import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.NoneValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropertiesMachineTest {

    private static Properties propertiesWith(String... namesAndValues) {
        Properties properties = new Properties();
        for (int at = 0; at + 1 < namesAndValues.length; at += 2) {
            properties.setProperty(namesAndValues[at], namesAndValues[at + 1]);
        }
        return properties;
    }

    private PropertiesMachine machineWith(String... namesAndValues) {
        return new PropertiesMachine(propertiesWith(namesAndValues));
    }

    @ParameterizedTest(name = "[{0}] is {1}")
    @CsvSource(delimiter = '|', value = {
            "Windows 11    | Windows",
            "Windows       | Windows",
            "Mac OS X      | macOS",
            "Mac           | macOS",
            "Linux         | Linux",
            "FreeBSD       | FreeBSD",
            "TeaVM         | TeaVM",
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
    @DisplayName("the separator is the one reported, a backslash or a slash")
    void readsSeparator() {
        assertThat(machineWith("file.separator", "\\").fileSeparator()).isEqualTo('\\');
        assertThat(machineWith("file.separator", "/").fileSeparator()).isEqualTo('/');
    }

    @Test
    @DisplayName("a machine that reports no separator is taken to use a slash")
    void defaultsSeparatorToSlash() {
        assertThat(machineWith().fileSeparator()).isEqualTo('/');
        assertThat(machineWith("file.separator", "").fileSeparator()).isEqualTo('/');
    }

    @Test
    @DisplayName("home and the current directory are user.home and user.dir, or empty where none is reported")
    void readsDirectories() {
        PropertiesMachine machine = machineWith("user.home", "/home/someone", "user.dir", "/work");

        assertThat(machine.homeDirectory()).isEqualTo("/home/someone");
        assertThat(machine.currentDirectory()).isEqualTo("/work");
        assertThat(machineWith().homeDirectory()).isEmpty();
        assertThat(machineWith().currentDirectory()).isEmpty();
    }

    @Test
    @DisplayName("it makes no launcher, so a script it hosts has no second interpreter to start")
    void makesNoLauncher() {
        assertThat(List.of(machineWith().launcherFor("/", ""))).containsExactly(NoneValue.none());
    }

    @Test
    @DisplayName("it has no process number to give, and says so as a machine given none does")
    void refusesAProcessNumber() {
        assertThatThrownBy(() -> machineWith().processNumber())
                .isInstanceOf(FilePort.Denied.class)
                .hasMessageContaining("process number");
    }
}
