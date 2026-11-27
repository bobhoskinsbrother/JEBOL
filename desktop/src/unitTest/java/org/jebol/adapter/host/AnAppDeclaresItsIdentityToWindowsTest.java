package org.jebol.adapter.host;

import org.jebol.testing.RunsOn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jebol.testing.TestTarget.DESKTOP_LINUX;
import static org.jebol.testing.TestTarget.DESKTOP_MAC;
import static org.jebol.testing.TestTarget.DESKTOP_WINDOWS;

class AnAppDeclaresItsIdentityToWindowsTest {

    @Test
    @RunsOn(value = DESKTOP_WINDOWS, because = "only Windows has an AppUserModelID to declare")
    @DisplayName("on Windows the process declares the app's identity, so its windows group with the shortcut that carries it")
    void declaresTheProcesssIdentityOnWindows() {
        assertThat(WindowsAppIdentity.declareForProcess("org.jebol.test.identity")).isTrue();

        assertThat(WindowsAppIdentity.processIdentity()).contains("org.jebol.test.identity");
    }

    @Test
    @RunsOn(value = {DESKTOP_LINUX, DESKTOP_MAC}, because = "it checks that a system without AppUserModelIDs declares nothing")
    @DisplayName("elsewhere there is nothing to declare, and asking does nothing")
    void declaresNothingElsewhere() {
        assertThat(WindowsAppIdentity.declareForProcess("org.jebol.test.identity")).isFalse();
        assertThat(WindowsAppIdentity.declareForWindow("Notes", "org.jebol.test.identity")).isFalse();
        assertThat(WindowsAppIdentity.processIdentity()).isEmpty();
    }
}
