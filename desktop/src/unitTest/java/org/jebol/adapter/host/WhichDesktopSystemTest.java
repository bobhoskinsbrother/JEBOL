package org.jebol.adapter.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class WhichDesktopSystemTest {

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({
            "Mac OS X, MACOS",
            "Windows 11, WINDOWS",
            "Windows Server 2022, WINDOWS",
            "Linux, X11",
            "FreeBSD, X11",
            "SunOS, X11",
            "'', X11"
    })
    @DisplayName("the system is read from os.name, and everything that is neither macOS nor Windows draws through X11")
    void readFromTheOperatingSystemsName(String operatingSystemName, DesktopSystem expected) {
        assertThat(DesktopSystem.named(operatingSystemName)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
            "MACOS, true",
            "WINDOWS, false",
            "X11, false"
    })
    @DisplayName("only macOS brings a minimised window back in place, without hiding and showing it again")
    void bringsMinimisedWindowBackInPlace(DesktopSystem system, boolean inPlace) {
        assertThat(system.bringsMinimisedWindowBackInPlace()).isEqualTo(inPlace);
    }
}
