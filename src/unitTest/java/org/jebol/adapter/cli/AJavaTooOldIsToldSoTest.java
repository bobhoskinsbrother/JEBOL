package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AJavaTooOldIsToldSoTest {

    private final JavaVersionCheck check = new JavaVersionCheck(25);

    @Test
    @DisplayName("one short of the oldest is refused, naming both versions")
    void oneShortIsRefused() {
        assertThat(check.refusalFor("24")).contains("JEBOL needs Java 25 or later; this is Java 24.");
    }

    @Test
    @DisplayName("the oldest itself runs")
    void theOldestRuns() {
        assertThat(check.refusalFor("25")).isEmpty();
    }

    @Test
    @DisplayName("one past the oldest runs")
    void onePastRuns() {
        assertThat(check.refusalFor("26")).isEmpty();
    }

    @Test
    @DisplayName("Java 21, the long-term release before, is refused")
    void twentyOneIsRefused() {
        assertThat(check.refusalFor("21")).contains("JEBOL needs Java 25 or later; this is Java 21.");
    }

    @Test
    @DisplayName("Java 8 calls itself 1.8, and is named 8")
    void eightIsNamedEight() {
        assertThat(check.refusalFor("1.8")).contains("JEBOL needs Java 25 or later; this is Java 8.");
    }

    @Test
    @DisplayName("a version with a minor part is judged by its first")
    void aMinorPartIsIgnored() {
        assertThat(check.refusalFor("25.0.1")).isEmpty();
        assertThat(check.refusalFor("24.0.2")).contains("JEBOL needs Java 25 or later; this is Java 24.");
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
    @DisplayName("the oldest is the build's own 25, and nothing older")
    void theOldestIsTwentyFive() {
        assertThat(JebolLauncher.OLDEST_JAVA).isEqualTo(25);
    }
}
