package org.jebol.adapter.cli;

import org.jebol.adapter.host.DesktopWindows;
import org.jebol.domain.host.WindowPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ChosenScreenTest {

    @Test
    @DisplayName("a page, and no screen asked for at all, leave the process with no window")
    void pagesAndTheBareConsole() {
        assertThat(headlessAfter()).isEqualTo("true");
        assertThat(headlessAfter("--screen=web")).isEqualTo("true");
        assertThat(headlessAfter("script.r3")).isEqualTo("true");
    }

    @Test
    @DisplayName("a window turns the display back on")
    void choosesAWindow() {
        assertThat(headlessAfter("--screen=window")).isEqualTo("false");
    }

    @Test
    @DisplayName("a window screen is answered with this machine's own dialogs")
    void aWindowScreenBringsTheMachinesDialogs() {
        assertThat(ChosenScreen.windowPortFor(new String[] {"--screen=window"}))
                .isInstanceOf(DesktopWindows.class);
    }

    @Test
    @DisplayName("a page and a bare run are answered with a port that refuses")
    void otherScreensGetTheRefusingPort() {
        for (String[] arguments : new String[][] {
                {"--screen=web"}, {"script.r3"}, {}}) {
            assertThatExceptionOfType(WindowPort.Denied.class)
                    .isThrownBy(() -> ChosenScreen.windowPortFor(arguments)
                            .chooseColour(Optional.empty()))
                    .withMessageContaining("no screen");
        }
    }

    private static String headlessAfter(String... arguments) {
        String was = System.getProperty("java.awt.headless");
        try {
            ChosenScreen.setHeadlessBasedOnWhetherScreenIsNeeded(arguments);
            return System.getProperty("java.awt.headless");
        } finally {
            if (was == null) {
                System.clearProperty("java.awt.headless");
            } else {
                System.setProperty("java.awt.headless", was);
            }
        }
    }
}
