package org.jebol.android.endtoend;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ARestartInPlaceLeavesNothingBehindEndToEndTest {

    private static final Duration PATIENCE = Duration.ofSeconds(30);

    @Test
    @DisplayName("a restart in place leaves no window, place, unsaved work, keyboard or held Back behind")
    void aRestartLeavesNothingBehind() {
        TheApp app = TheApp.runningScript("""
                view/no-wait make gob! [size: 300x200 color: 200.40.40]
                go 'second
                unsaved-work true
                print rejoin ["at " mold current-place]
                do-events
                """);
        assertThat(app.waitForLineStarting("at ", PATIENCE)).isEqualTo("at [second]");
        app.tapAt(new int[] {200, 200});
        app.typeOnKeys("hi");
        assertThat(app.waitUntilKeyboardShownIs(true, PATIENCE)).as("typing raised the keyboard").isTrue();

        app.restartWith("""
                print rejoin ["windows " length? any [system/view/screen-gob/pane []]]
                print rejoin ["place " mold current-place]
                print "ready"
                do-events
                """);

        assertThat(app.waitForLineStarting("windows ", PATIENCE)).isEqualTo("windows 0");
        assertThat(app.waitForLineStarting("place ", PATIENCE)).isEqualTo("place []");
        app.waitForLineStarting("ready", PATIENCE);
        assertThat(app.waitUntilKeyboardShownIs(false, PATIENCE)).as("the keyboard went down").isTrue();
        assertThat(app.isViewBlank()).as("the last script's window is gone from the view").isTrue();
        assertThat(app.printed()).doesNotContain("at [second]");

        app.pressBack();

        assertThat(app.device().wait(Until.gone(By.pkg(TheApp.PACKAGE).depth(0)), PATIENCE.toMillis()))
                .as("Back left the app without asking").isTrue();
        assertThat(app.printedAsErrors()).isEmpty();
    }

    @Test
    @DisplayName("a restart that comes while the last script is still starting ends that script all the same")
    void aRestartDuringStartingStillRestarts() {
        TheApp app = TheApp.launchedAfresh("""
                print "first"
                do-events
                """);

        app.restartWith("""
                print "second"
                do-events
                """);

        assertThat(app.waitForLineStarting("second", PATIENCE)).isEqualTo("second");
        assertThat(app.printed()).doesNotContain("first");
        assertThat(app.printedAsErrors()).isEmpty();
    }
}
