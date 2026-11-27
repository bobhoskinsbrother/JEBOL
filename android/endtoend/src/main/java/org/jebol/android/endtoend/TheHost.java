package org.jebol.android.endtoend;

import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import static org.assertj.core.api.Assertions.assertThat;

final class TheHost {

    private TheHost() {
    }

    static String address() {
        return given("hostAddress");
    }

    static String port(String name) {
        return given(name);
    }

    private static String given(String name) {
        Bundle arguments = InstrumentationRegistry.getArguments();
        String value = arguments.getString(name);
        assertThat(value).as("the run gives the device %s", name).isNotBlank();
        return value;
    }
}
