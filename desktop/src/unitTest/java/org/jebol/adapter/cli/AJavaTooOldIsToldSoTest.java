package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AJavaTooOldIsToldSoTest {

    private final JavaVersionCheck check = new JavaVersionCheck(21);

    @Test
    @DisplayName("one short of the oldest is refused, naming both versions")
    void oneShortIsRefused() {
        assertThat(check.refusalFor("20")).contains("JEBOL needs Java 21 or later; this is Java 20.");
    }

    @Test
    @DisplayName("the oldest itself runs")
    void theOldestRuns() {
        assertThat(check.refusalFor("21")).isEmpty();
    }

    @Test
    @DisplayName("one past the oldest runs, and so does the build's own 25")
    void newerRuns() {
        assertThat(check.refusalFor("22")).isEmpty();
        assertThat(check.refusalFor("25")).isEmpty();
    }

    @Test
    @DisplayName("Java 17, the long-term release before, is refused")
    void seventeenIsRefused() {
        assertThat(check.refusalFor("17")).contains("JEBOL needs Java 21 or later; this is Java 17.");
    }

    @Test
    @DisplayName("Java 8 calls itself 1.8, and is named 8")
    void eightIsNamedEight() {
        assertThat(check.refusalFor("1.8")).contains("JEBOL needs Java 21 or later; this is Java 8.");
    }

    @Test
    @DisplayName("a version with a minor part is judged by its first")
    void aMinorPartIsIgnored() {
        assertThat(check.refusalFor("21.0.9")).isEmpty();
        assertThat(check.refusalFor("20.0.2")).contains("JEBOL needs Java 21 or later; this is Java 20.");
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", " ", "abc", "1.", ".", "-1", "1.x", "x.8", "99999999999999999999"})
    @DisplayName("a version that cannot be read is let through")
    void anUnreadableVersionIsLetThrough(String version) {
        assertThat(check.refusalFor(version)).isEmpty();
    }

    @Test
    @DisplayName("no version at all is let through")
    void noVersionIsLetThrough() {
        assertThat(check.refusalFor(null)).isEmpty();
    }

    @Test
    @DisplayName("the oldest is the level every source set is compiled at, 21, and nothing older")
    void theOldestIsTwentyOne() {
        assertThat(JebolLauncher.OLDEST_JAVA).isEqualTo(21);
    }
}
