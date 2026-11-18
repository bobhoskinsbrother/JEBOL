package org.jebol.android.unit;

import org.jebol.adapter.android.AndroidEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class AndroidEnvironmentTest {

    private final AndroidEnvironment environment = new AndroidEnvironment("org.jebol.android", Locale.UK);

    @Test
    @DisplayName("it names exactly a desktop's variables, wrapped to the app's own places")
    void namesTheAppsOwnVariables() {
        assertThat(environment.readAll()).containsOnlyKeys("HOME", "PWD", "TMPDIR", "LANG", "USER", "LOGNAME");
        assertThat(environment.valueOf("HOME")).isEqualTo("/");
        assertThat(environment.valueOf("PWD")).isEqualTo("/");
        assertThat(environment.valueOf("TMPDIR")).isEqualTo("/tmp");
    }

    @Test
    @DisplayName("USER and LOGNAME are the app's identifier")
    void theUserIsTheApp() {
        assertThat(environment.valueOf("USER")).isEqualTo("org.jebol.android");
        assertThat(environment.valueOf("LOGNAME")).isEqualTo("org.jebol.android");
    }

    @Test
    @DisplayName("LANG is the locale written as POSIX writes it")
    void theLanguageIsTheLocale() {
        assertThat(environment.valueOf("LANG")).isEqualTo("en_GB.UTF-8");
        assertThat(new AndroidEnvironment("a", Locale.FRENCH).valueOf("LANG")).isEqualTo("fr.UTF-8");
        assertThat(new AndroidEnvironment("a", Locale.ROOT).valueOf("LANG")).isEqualTo("C.UTF-8");
    }

    @Test
    @DisplayName("nothing of the device's own environment reaches a script")
    void noneOfTheDevicesVariables() {
        assertThat(System.getenv("ANDROID_DATA")).isNotNull();
        assertThat(environment.valueOf("ANDROID_DATA")).isNull();
        assertThat(environment.valueOf("PATH")).isNull();
        assertThat(environment.valueOf("SHELL")).isNull();
    }

    @Test
    @DisplayName("a variable set is read back and listed, and one taken away is gone")
    void aScriptsOwnVariables() {
        environment.assign("GREETING", "hello");
        assertThat(environment.valueOf("GREETING")).isEqualTo("hello");
        assertThat(environment.readAll()).containsEntry("GREETING", "hello");

        environment.assign("HOME", null);
        assertThat(environment.valueOf("HOME")).isNull();
        assertThat(environment.readAll()).doesNotContainKey("HOME");
    }

    @Test
    @DisplayName("a copy starts with what was set and then goes its own way")
    void aCopyIsItsOwn() {
        environment.assign("GREETING", "hello");
        AndroidEnvironment copy = environment.copy();
        copy.assign("GREETING", "bye");

        assertThat(copy.valueOf("GREETING")).isEqualTo("bye");
        assertThat(environment.valueOf("GREETING")).isEqualTo("hello");
    }
}
