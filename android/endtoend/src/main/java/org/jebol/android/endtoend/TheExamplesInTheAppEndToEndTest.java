package org.jebol.android.endtoend;

import android.util.DisplayMetrics;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TheExamplesInTheAppEndToEndTest {

    private static final Duration PATIENCE = Duration.ofSeconds(60);

    private static final int SURVEYS_RIGHT_EDGE_DP = 620 + 465;

    private TheApp app;

    static boolean isNarrowerThanTheSurvey() {
        DisplayMetrics screen = InstrumentationRegistry.getInstrumentation().getContext().getResources()
                .getDisplayMetrics();
        return Math.max(screen.widthPixels, screen.heightPixels) / screen.density < SURVEYS_RIGHT_EDGE_DP;
    }

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
    @DisabledIf(value = "isNarrowerThanTheSurvey",
            disabledReason = "the survey's window opens off a screen this narrow until goal 2d lays the examples out for it")
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
