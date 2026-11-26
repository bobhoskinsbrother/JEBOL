package org.jebol.adapter.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

class AnAppDeclaresItsIdentityToWindowsTest {

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @DisplayName("on Windows the process declares the app's identity, so its windows group with the shortcut that carries it")
    void declaresTheProcesssIdentityOnWindows() {
        assertThat(WindowsAppIdentity.declareForProcess("org.jebol.test.identity")).isTrue();

        assertThat(WindowsAppIdentity.processIdentity()).contains("org.jebol.test.identity");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("elsewhere there is nothing to declare, and asking does nothing")
    void declaresNothingElsewhere() {
        assertThat(WindowsAppIdentity.declareForProcess("org.jebol.test.identity")).isFalse();
        assertThat(WindowsAppIdentity.declareForWindow("Notes", "org.jebol.test.identity")).isFalse();
        assertThat(WindowsAppIdentity.processIdentity()).isEmpty();
    }
}
