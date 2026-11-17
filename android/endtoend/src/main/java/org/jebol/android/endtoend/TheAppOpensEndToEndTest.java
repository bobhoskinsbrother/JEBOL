package org.jebol.android.endtoend;

import android.content.Context;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheAppOpensEndToEndTest {

    private static final String THE_APP = "org.jebol.android";

    private static final long SECONDS_TO_OPEN = 20_000;

    @Test
    @DisplayName("launching the app brings its activity to the front")
    void opensToItsActivity() {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(THE_APP);
        assertThat(launch).as("the app is installed with a launcher activity").isNotNull();
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        context.startActivity(launch);

        assertThat(device.wait(Until.hasObject(By.pkg(THE_APP).depth(0)), SECONDS_TO_OPEN)).isTrue();
    }
}
