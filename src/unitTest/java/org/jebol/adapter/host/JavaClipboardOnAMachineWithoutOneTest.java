package org.jebol.adapter.host;

import org.jebol.domain.host.ClipboardPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaClipboardOnAMachineWithoutOneTest {

    private static final String HEADLESS = "java.awt.headless";

    private static <T> T withNoWindowingSystem(java.util.function.Supplier<T> asking) {
        String before = System.getProperty(HEADLESS);
        System.setProperty(HEADLESS, "true");
        try {
            return asking.get();
        } finally {
            if (before == null) {
                System.clearProperty(HEADLESS);
            } else {
                System.setProperty(HEADLESS, before);
            }
        }
    }

    @Test
    @DisplayName("reading refuses rather than throwing something the domain cannot read")
    void readingRefuses() {
        assertThatThrownBy(() -> withNoWindowingSystem(
                () -> new JavaClipboard().read()))
                .isInstanceOf(ClipboardPort.Unreachable.class)
                .hasMessageContaining("no clipboard to reach");
    }

    @Test
    @DisplayName("and the reason says what went wrong rather than the word null")
    void theReasonIsReadable() {
        assertThatThrownBy(() -> withNoWindowingSystem(
                () -> new JavaClipboard().read()))
                .hasMessageNotContaining("null");
    }

    @Test
    @DisplayName("writing refuses the same way")
    void writingRefuses() {
        assertThatThrownBy(() -> withNoWindowingSystem(() -> {
            new JavaClipboard().write("anything");
            return null;
        })).isInstanceOf(ClipboardPort.Unreachable.class);
    }

    @Test
    @DisplayName("the clipboard nobody gave refuses both ways too")
    void theClipboardNobodyGave() {
        assertThatThrownBy(() -> ClipboardPort.none().read())
                .isInstanceOf(ClipboardPort.Unreachable.class);
        assertThatThrownBy(() -> ClipboardPort.none().write("x"))
                .isInstanceOf(ClipboardPort.Unreachable.class);
        assertThat(ClipboardPort.none()).isNotNull();
    }
}
