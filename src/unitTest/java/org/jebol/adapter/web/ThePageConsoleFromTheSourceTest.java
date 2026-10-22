package org.jebol.adapter.web;

import org.jebol.domain.host.FilePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThePageConsoleFromTheSourceTest {

    private final WebConsole console = new WebConsole();

    @Test
    @DisplayName("a page's line is typed into a form on its screen")
    void isTypedOnTheScreen() {
        assertThat(console.isTypedOnScreen()).isTrue();
    }

    @Test
    @DisplayName("with no page attached to show the form, reading a line is refused rather than left waiting")
    void refusesALineWithNoPage() {
        assertThatThrownBy(console::readLine)
                .isInstanceOf(FilePort.Denied.class)
                .hasMessageContaining("no page is attached");
    }

    @Test
    @DisplayName("a hidden line is refused rather than read in the open")
    void refusesAHiddenLine() {
        assertThatThrownBy(console::readHiddenLine)
                .isInstanceOf(FilePort.Denied.class)
                .hasMessageContaining("without showing it");
    }

    @Test
    @DisplayName("a page is not a terminal and reads no single keys")
    void isNotATerminal() {
        assertThat(console.readKey()).isEqualTo(-1);
        assertThat(console.isATerminal()).isFalse();
    }
}
