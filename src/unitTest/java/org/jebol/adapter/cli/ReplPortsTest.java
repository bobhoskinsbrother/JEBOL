package org.jebol.adapter.cli;

import org.jebol.adapter.host.DesktopWindows;
import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.application.Bounds;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.WindowPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ReplPortsTest {

    private static HostPorts portsFor(String... arguments) {
        return Repl.portsFor(Bounds.standard(), arguments,
                new PrintStream(OutputStream.nullOutputStream()), false,
                Path.of("/"), new ProcessEnvironment());
    }

    @Test
    @DisplayName("a window screen brings this machine's own dialogs with it")
    void windowScreenBringsTheMachinesDialogs() {
        assertThat(portsFor("--screen=window").windows())
                .isInstanceOf(DesktopWindows.class);
    }

    @Test
    @DisplayName("with no window asked for, the dialogs refuse as they always did")
    void noWindowLeavesTheDialogsRefusing() {
        assertThatExceptionOfType(WindowPort.Denied.class)
                .isThrownBy(() -> portsFor("script.r3").windows().chooseColour(Optional.empty()))
                .withMessageContaining("no screen");
    }
}
