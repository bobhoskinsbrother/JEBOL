package org.jebol.adapter.cli;

import org.jebol.adapter.host.DesktopWindows;
import org.jebol.adapter.host.JavaHost;
import org.jebol.adapter.web.PageWindows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChosenScreenTest {

    private final PrintStream discarded = new PrintStream(OutputStream.nullOutputStream());

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
        assertThat(headlessAfter("Linux", Map.of("DISPLAY", ":1"), "--screen=window")).isEqualTo("false");
        assertThat(headlessAfter("Linux", Map.of("WAYLAND_DISPLAY", "wayland-0"), "--screen=window"))
                .isEqualTo("false");
        assertThat(headlessAfter("Mac OS X", Map.of(), "--screen=window")).isEqualTo("false");
        assertThat(headlessAfter("Windows 11", Map.of(), "--screen=window")).isEqualTo("false");
    }

    @Test
    @DisplayName("a window asked for where X11 names no display leaves the process headless, so it says so rather than crashing")
    void aWindowWithNoDisplayStaysHeadless() {
        assertThat(headlessAfter("Linux", Map.of(), "--screen=window")).isEqualTo("true");
        assertThat(headlessAfter("Linux", Map.of("DISPLAY", ""), "--screen=window")).isEqualTo("true");
        assertThat(headlessAfter("FreeBSD", Map.of("DISPLAY", "  "), "--screen=window")).isEqualTo("true");
    }

    @Test
    @DisplayName("a window screen is answered with this machine's own dialogs")
    void aWindowScreenBringsTheMachinesDialogs() {
        assertThat(new ChosenScreen(new String[] {"--screen=window"})
                .attachTo(new JavaHost().ports(), discarded, false).windows())
                .isInstanceOf(DesktopWindows.class);
    }

    @Test
    @DisplayName("a page screen is answered with the page's own dialogs")
    void aPageScreenBringsThePagesDialogs() {
        assertThat(new ChosenScreen(new String[] {"--screen=web"})
                .attachTo(new JavaHost().ports(), discarded, false).windows())
                .isInstanceOf(PageWindows.class);
    }

    @Test
    @DisplayName("a run that asked for no screen asks for nothing to be attached, and the switch is taken out of what Rebol sees")
    void aBareRunAsksForNoScreen() {
        assertThat(new ChosenScreen(new String[] {"script.r3"}).isAskedFor()).isFalse();
        assertThat(new ChosenScreen(new String[] {"--screen=web", "script.r3"}).withoutSwitch())
                .containsExactly("script.r3");
    }

    private String headlessAfter(String... arguments) {
        return headlessAfter("Linux", Map.of("DISPLAY", ":0"), arguments);
    }

    private String headlessAfter(String operatingSystem, Map<String, String> environment, String... arguments) {
        String was = System.getProperty("java.awt.headless");
        try {
            new ChosenScreen(arguments).chooseHeadlessMode(operatingSystem, environment);
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
