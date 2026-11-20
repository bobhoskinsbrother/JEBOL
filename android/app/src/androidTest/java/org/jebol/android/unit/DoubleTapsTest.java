package org.jebol.android.unit;

import androidx.test.platform.app.InstrumentationRegistry;

import org.jebol.adapter.android.DoubleTaps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DoubleTapsTest {

    private static final long TIMEOUT_MILLISECONDS = 300;
    private static final float TOUCH_SLOP = 20;
    private static final float DOUBLE_TAP_SLOP = 100;

    private final DoubleTaps taps = new DoubleTaps(TIMEOUT_MILLISECONDS, TOUCH_SLOP, DOUBLE_TAP_SLOP);

    private void tap(long at, float across, float down) {
        taps.press(at, across, down);
        taps.lift(at + 60);
    }

    @Test
    @DisplayName("the first press is pressed once")
    void theFirstPressIsOnce() {
        taps.press(1_000, 50, 50);

        assertThat(taps.wasPressedTwice()).isFalse();
    }

    @Test
    @DisplayName("a press soon after a tap, near where it was, is pressed twice")
    void aPressSoonAndNearIsTwice() {
        tap(1_000, 50, 50);

        taps.press(1_200, 80, 70);

        assertThat(taps.wasPressedTwice()).isTrue();
    }

    @Test
    @DisplayName("a press after the double-tap timeout has passed since the lift is pressed once")
    void aPressTooLateIsOnce() {
        tap(1_000, 50, 50);

        taps.press(1_060 + TIMEOUT_MILLISECONDS + 1, 50, 50);

        assertThat(taps.wasPressedTwice()).isFalse();
    }

    @Test
    @DisplayName("a press exactly at the double-tap timeout is still pressed twice, as Android counts it")
    void aPressAtTheTimeoutIsTwice() {
        tap(1_000, 50, 50);

        taps.press(1_060 + TIMEOUT_MILLISECONDS, 50, 50);

        assertThat(taps.wasPressedTwice()).isTrue();
    }

    @Test
    @DisplayName("a press farther from the tap than the double-tap slop is pressed once")
    void aPressTooFarIsOnce() {
        tap(1_000, 50, 50);

        taps.press(1_200, 50 + DOUBLE_TAP_SLOP, 50);

        assertThat(taps.wasPressedTwice()).isFalse();
    }

    @Test
    @DisplayName("a press soon after a drag rather than a tap is pressed once")
    void aPressAfterADragIsOnce() {
        taps.press(1_000, 50, 50);
        taps.move(50, 50 + TOUCH_SLOP + 1);
        taps.lift(1_100);

        taps.press(1_200, 50, 50);

        assertThat(taps.wasPressedTwice()).isFalse();
    }

    @Test
    @DisplayName("a finger that wanders within the touch slop still taps")
    void aWanderWithinTheSlopStillTaps() {
        taps.press(1_000, 50, 50);
        taps.move(50 + TOUCH_SLOP, 50);
        taps.lift(1_100);

        taps.press(1_200, 50, 50);

        assertThat(taps.wasPressedTwice()).isTrue();
    }

    @Test
    @DisplayName("the press after a double tap starts again, once")
    void theThirdPressIsOnce() {
        tap(1_000, 50, 50);
        tap(1_200, 50, 50);

        taps.press(1_400, 50, 50);

        assertThat(taps.wasPressedTwice()).isFalse();
    }

    @Test
    @DisplayName("taken from the device's ViewConfiguration, a quick second tap in place is pressed twice")
    void fromTheDevicesConfiguration() {
        DoubleTaps device = DoubleTaps.from(InstrumentationRegistry.getInstrumentation().getTargetContext());
        device.press(1_000, 50, 50);
        device.lift(1_050);

        device.press(1_150, 52, 52);

        assertThat(device.wasPressedTwice()).isTrue();
    }
}
