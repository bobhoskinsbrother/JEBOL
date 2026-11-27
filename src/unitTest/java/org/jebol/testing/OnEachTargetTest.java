package org.jebol.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jebol.testing.OnEachTarget.elsewhere;
import static org.jebol.testing.OnEachTarget.on;
import static org.jebol.testing.TestTarget.ANDROID;
import static org.jebol.testing.TestTarget.DESKTOP_LINUX;
import static org.jebol.testing.TestTarget.DESKTOP_MAC;
import static org.jebol.testing.TestTarget.DESKTOP_WINDOWS;

class OnEachTargetTest {

    @Test
    @DisplayName("the answer named for this target is the one expected")
    void picksTheAnswerNamedForThisTarget() {
        assertThat(OnEachTarget.expectedOn(DESKTOP_WINDOWS, on(DESKTOP_WINDOWS, "4"), elsewhere("1"))).isEqualTo("4");
        assertThat(OnEachTarget.expectedOn(DESKTOP_MAC, on(DESKTOP_LINUX, DESKTOP_MAC, "/"), on(DESKTOP_WINDOWS, "")))
                .isEqualTo("/");
    }

    @Test
    @DisplayName("a target no answer names takes the answer for elsewhere")
    void takesElsewhereWhenNoAnswerNamesThisTarget() {
        assertThat(OnEachTarget.expectedOn(ANDROID, on(DESKTOP_WINDOWS, "4"), elsewhere("1"))).isEqualTo("1");
    }

    @Test
    @DisplayName("a target no answer covers fails, naming it, rather than asserting nothing")
    void failsForATargetNoAnswerCovers() {
        assertThatThrownBy(() -> OnEachTarget.expectedOn(DESKTOP_MAC, on(DESKTOP_WINDOWS, "4"), on(DESKTOP_LINUX, "1")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("DESKTOP_MAC");
    }

    @Test
    @DisplayName("a target named twice fails, since the test would say two things about it")
    void failsForATargetNamedTwice() {
        assertThatThrownBy(() -> OnEachTarget.expectedOn(DESKTOP_LINUX, on(DESKTOP_LINUX, "1"), on(DESKTOP_LINUX, DESKTOP_MAC, "2")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("DESKTOP_LINUX");
    }

    @Test
    @DisplayName("elsewhere may be given once at most")
    void failsForElsewhereGivenTwice() {
        assertThatThrownBy(() -> OnEachTarget.expectedOn(ANDROID, elsewhere("1"), elsewhere("2")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("elsewhere");
    }
}
