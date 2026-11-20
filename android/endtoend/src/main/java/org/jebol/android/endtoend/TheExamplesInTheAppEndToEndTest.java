package org.jebol.android.endtoend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TheExamplesInTheAppEndToEndTest {

    private static final Duration PATIENCE = Duration.ofSeconds(60);

    private static final int CLEAR_OF_THE_EDGE_DP = 48;

    private static final int THE_PANELS_MARGIN_DP = 6;

    private TheApp app;

    @BeforeEach
    void openTheExamples() {
        app = TheApp.runningWhatItCarries();
        app.waitForLineStarting("open", PATIENCE);
    }

    private int[] bringIntoView(String place) {
        String line = app.waitForLineStarting(place, PATIENCE);
        int[] at = app.readPairAfter(line, place);
        int[] view = app.theViewsSizeInDp();
        int high = view[1];
        if (at[1] >= CLEAR_OF_THE_EDGE_DP && at[1] <= high - CLEAR_OF_THE_EDGE_DP) {
            return at;
        }
        int from = high / 2;
        int by = Math.max(-from + CLEAR_OF_THE_EDGE_DP, Math.min(from - CLEAR_OF_THE_EDGE_DP, high / 2 - at[1]));
        app.drag(new int[] {THE_PANELS_MARGIN_DP, from}, new int[] {THE_PANELS_MARGIN_DP, from + by});
        return bringIntoView(place, app.waitForLineStartingOtherThan(place, line, PATIENCE));
    }

    private int[] bringIntoView(String place, String movedTo) {
        int[] at = app.readPairAfter(movedTo, place);
        int high = app.theViewsSizeInDp()[1];
        return at[1] >= CLEAR_OF_THE_EDGE_DP && at[1] <= high - CLEAR_OF_THE_EDGE_DP ? at : bringIntoView(place);
    }

    @Test
    @DisplayName("a drag moves the slider")
    void aDragMovesTheSlider() {
        int[] slider = bringIntoView("slider-at ");
        String was = app.waitForLineStarting("slider ", PATIENCE);

        app.drag(slider, new int[] {slider[0] + 60, slider[1]});

        assertThat(app.waitForLineStartingOtherThan("slider ", was, PATIENCE)).isNotEqualTo(was);
    }

    @Test
    @DisplayName("tapping the slider raises no keyboard")
    void tappingTheSliderRaisesNoKeyboard() {
        int[] slider = bringIntoView("slider-at ");

        app.tapAt(slider);

        assertThat(app.waitUntilKeyboardShownIs(true, Duration.ofSeconds(3)))
                .as("a tap on a slider raised the soft keyboard")
                .isFalse();
    }

    @Test
    @DisplayName("letters typed reach the survey's field, which the keyboard does not cover")
    void typedLettersReachTheField() {
        int[] field = bringIntoView("field-at ");
        app.waitForLineStarting("field ", PATIENCE);
        int fieldsOwnColour = app.readColourAt(field);

        app.tapAt(field);

        assertThat(app.waitUntilKeyboardShownIs(true, PATIENCE)).as("focusing the field raised the soft keyboard").isTrue();
        app.typeOnKeys("Ada");
        assertThat(app.waitForLineStarting("field Ada", PATIENCE)).endsWith("Ada");
        assertThat(app.isShowingAboveTheKeyboard(field, fieldsOwnColour))
                .as("the field shows above the keyboard")
                .isTrue();
    }

    @Test
    @DisplayName("a drag moves the scroller")
    void aDragMovesTheScroller() {
        int[] scroller = bringIntoView("scroller-at ");
        String was = app.waitForLineStarting("scroller ", PATIENCE);

        app.drag(scroller, new int[] {scroller[0] + 40, scroller[1] + 40});

        assertThat(app.waitForLineStartingOtherThan("scroller ", was, PATIENCE)).isNotEqualTo(was);
    }

    @Test
    @DisplayName("on its side, the examples lay themselves out again and the slider still drags")
    void onItsSideTheSliderStillDrags() {
        try {
            app.restartOnItsSide();
            app.waitForLineStarting("open", PATIENCE);
            int[] slider = bringIntoView("slider-at ");
            String was = app.waitForLineStarting("slider ", PATIENCE);

            app.drag(slider, new int[] {slider[0] + 60, slider[1]});

            assertThat(app.waitForLineStartingOtherThan("slider ", was, PATIENCE)).isNotEqualTo(was);
        } finally {
            app.turnUpright();
        }
    }
}
