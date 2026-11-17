package org.jebol.android.endtoend;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BackAndTheSavedPlaceEndToEndTest {

    private static final Duration PATIENCE = Duration.ofSeconds(30);

    private static final String AN_APP_WITH_PLACES = """
            view/no-wait make gob! [size: 300x200 color: 40.160.40]
            handle-events [
                name: 'moves
                priority: 100
                handler: func [event] [
                    if find [back forward] event/type [print rejoin ["arrived " mold current-place]]
                    none
                ]
            ]
            print rejoin ["started-at " mold current-place]
            if empty? current-place [go 'second]
            print "ready"
            do-events
            """;

    @Test
    @DisplayName("Back moves the history, and at the first place it leaves the app")
    void backMovesTheHistoryThenLeaves() {
        TheApp app = TheApp.runningScript(AN_APP_WITH_PLACES);
        app.waitForLineStarting("ready", PATIENCE);

        app.pressBack();
        assertThat(app.waitForLineStarting("arrived", PATIENCE)).isEqualTo("arrived []");
        assertThat(app.isInFront()).isTrue();

        app.pressBack();
        app.device().wait(Until.gone(By.pkg(TheApp.PACKAGE).depth(0)), PATIENCE.toMillis());
        assertThat(app.isInFront()).isFalse();
    }

    @Test
    @DisplayName("Back at the first place with unsaved work asks first, and Cancel stays")
    void backWithUnsavedWorkAsks() {
        TheApp app = TheApp.runningScript("""
                view/no-wait make gob! [size: 300x200 color: 160.40.40]
                unsaved-work true
                print "ready"
                do-events
                """);
        app.waitForLineStarting("ready", PATIENCE);

        app.pressBack();
        UiObject2 cancel = app.device().wait(Until.findObject(By.text("Cancel")), PATIENCE.toMillis());
        assertThat(cancel).as("the app asks before leaving with unsaved work").isNotNull();
        cancel.click();

        app.device().wait(Until.gone(By.text("Cancel")), PATIENCE.toMillis());
        assertThat(app.isInFront()).isTrue();
    }

    @Test
    @DisplayName("a process Android ended comes back at the place it was on")
    void aRelaunchResumesAtTheSavedPlace() {
        TheApp app = TheApp.runningScript(AN_APP_WITH_PLACES);
        app.waitForLineStarting("ready", PATIENCE);

        app.pressHome();
        app.device().wait(Until.gone(By.pkg(TheApp.PACKAGE).depth(0)), PATIENCE.toMillis());
        app.shell("logcat -c");
        app.endTheProcess();
        assertThat(app.isProcessAlive()).isFalse();
        app.bringBack();

        assertThat(app.waitForLineStarting("started-at", PATIENCE)).isEqualTo("started-at [second]");
    }
}
