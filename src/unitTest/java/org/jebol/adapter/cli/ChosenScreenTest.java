package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static String headlessAfter(String... arguments) {
        String was = System.getProperty("java.awt.headless");
        try {
            ChosenScreen.setHeadlessBasedOnWhetherAScreenIsNeeded(arguments);
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
