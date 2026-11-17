package org.jebol.android.endtoend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TheExamplesInTheAppEndToEndTest {

    private static final Duration PATIENCE = Duration.ofSeconds(60);

    private TheApp app;

    @BeforeEach
    void openTheExamples() {
        app = TheApp.runningWhatItCarries();
        app.waitForLineStarting("open", PATIENCE);
    }

    @Test
    @DisplayName("a drag moves the slider")
    void aDragMovesTheSlider() {
        int[] slider = app.readPairAfter(app.waitForLineStarting("slider-at", PATIENCE), "slider-at");
        String was = app.waitForLineStarting("slider ", PATIENCE);

        app.drag(slider, new int[] {slider[0] + 60, slider[1]});

        assertThat(app.waitForLineStartingOtherThan("slider ", was, PATIENCE)).isNotEqualTo(was);
    }

    @Test
    @DisplayName("letters typed reach the survey's field")
    void typedLettersReachTheField() {
        int[] field = app.readPairAfter(app.waitForLineStarting("field-at", PATIENCE), "field-at");
        app.waitForLineStarting("field ", PATIENCE);

        app.tapAt(field);
        app.typeOnKeys("Ada");

        assertThat(app.waitForLineStarting("field Ada", PATIENCE)).endsWith("Ada");
        assertThat(app.isKeyboardShown()).as("focusing the field raised the soft keyboard").isTrue();
    }

    @Test
    @DisplayName("a drag moves the scroller")
    void aDragMovesTheScroller() {
        int[] scroller = app.readPairAfter(app.waitForLineStarting("scroller-at", PATIENCE), "scroller-at");
        String was = app.waitForLineStarting("scroller ", PATIENCE);

        app.drag(scroller, new int[] {scroller[0] + 40, scroller[1] + 40});

        assertThat(app.waitForLineStartingOtherThan("scroller ", was, PATIENCE)).isNotEqualTo(was);
    }
}
