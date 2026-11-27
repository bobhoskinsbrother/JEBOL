package org.jebol.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestTargetTest {

    private static final String HOTSPOT = "OpenJDK 64-Bit Server VM";
    private static final String ART = "Dalvik";

    @Test
    @DisplayName("the targets are goal 20.4c's, one for one")
    void namesTheTargetsOfGoalTwentyFourC() {
        assertThat(Arrays.stream(TestTarget.values()).map(Enum::name)).containsExactly(
                "DESKTOP_LINUX", "DESKTOP_MAC", "DESKTOP_WINDOWS",
                "WEB_LINUX", "WEB_MAC", "WEB_WINDOWS",
                "ANDROID", "IOS");
    }

    @Test
    @DisplayName("a run names its target, and that is the target it runs")
    void aRunNamesItsTarget() {
        assertThat(TestTarget.named("DESKTOP_LINUX", "Linux", HOTSPOT)).isEqualTo(TestTarget.DESKTOP_LINUX);
        assertThat(TestTarget.named("WEB_MAC", "Mac OS X", HOTSPOT)).isEqualTo(TestTarget.WEB_MAC);
        assertThat(TestTarget.named("DESKTOP_WINDOWS", "Windows 11", HOTSPOT)).isEqualTo(TestTarget.DESKTOP_WINDOWS);
        assertThat(TestTarget.named("ANDROID", "Linux", ART)).isEqualTo(TestTarget.ANDROID);
    }

    @Test
    @DisplayName("a run that names no target is refused, saying how to name one")
    void aRunNamingNoTargetIsRefused() {
        assertThatThrownBy(() -> TestTarget.named(null, "Linux", HOTSPOT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jebol.target");
        assertThatThrownBy(() -> TestTarget.named(" ", "Linux", HOTSPOT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jebol.target");
    }

    @Test
    @DisplayName("a run naming something that is not a target is refused, listing the targets")
    void aRunNamingNoSuchTargetIsRefused() {
        assertThatThrownBy(() -> TestTarget.named("DESKTOP_BEOS", "Linux", HOTSPOT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DESKTOP_BEOS")
                .hasMessageContaining("DESKTOP_LINUX");
    }

    @Test
    @DisplayName("a desktop or web target on another system is refused, naming the system this is")
    void anotherSystemsTargetIsRefused() {
        assertThatThrownBy(() -> TestTarget.named("DESKTOP_WINDOWS", "Linux", HOTSPOT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DESKTOP_WINDOWS")
                .hasMessageContaining("Linux");
        assertThatThrownBy(() -> TestTarget.named("WEB_MAC", "Windows 10", HOTSPOT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Windows 10");
    }

    @Test
    @DisplayName("Android is Android's runtime, and a JVM is never Android")
    void androidIsAndroidsRuntimeAlone() {
        assertThatThrownBy(() -> TestTarget.named("ANDROID", "Linux", HOTSPOT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(HOTSPOT);
        assertThatThrownBy(() -> TestTarget.named("DESKTOP_LINUX", "Linux", ART))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ART);
    }

    @Test
    @DisplayName("iOS is refused until goal 3 gives it a runner")
    void iosIsRefusedUntilItHasARunner() {
        assertThatThrownBy(() -> TestTarget.named("IOS", "Mac OS X", HOTSPOT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("goal 3");
    }
}
