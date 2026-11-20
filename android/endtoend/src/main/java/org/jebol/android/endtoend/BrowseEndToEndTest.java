package org.jebol.android.endtoend;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BrowseEndToEndTest {

    private static final Duration PATIENCE = Duration.ofSeconds(30);

    private TheApp app;

    @AfterEach
    void comeBack() {
        if (app != null) {
            app.pressHome();
            app.endProcess();
        }
    }

    private void assertAnotherAppOpened() {
        assertThat(app.device().wait(Until.gone(By.pkg(TheApp.PACKAGE).depth(0)), PATIENCE.toMillis())).isTrue();
        assertThat(app.printedAsErrors()).isEmpty();
    }

    @Test
    @DisplayName("BROWSE of a url opens it in another app")
    void browseOpensAUrl() {
        app = TheApp.launchedAfresh("browse http://localhost:9/ print 'browsed");

        assertThat(app.waitForLineStarting("browsed", PATIENCE)).isEqualTo("browsed");
        assertAnotherAppOpened();
    }

    @Test
    @DisplayName("BROWSE of a file in the store opens the user's viewer on a copy it may only read")
    void browseOpensAStoreFile() {
        app = TheApp.launchedAfresh("""
                write %page.html {<html><body>from the store</body></html>}
                browse %page.html
                print 'browsed
                """);

        assertThat(app.waitForLineStarting("browsed", PATIENCE)).isEqualTo("browsed");
        assertAnotherAppOpened();
    }

    @Test
    @DisplayName("BROWSE of a file that is not there is refused with cannot-open, and no other app opens")
    void browseOfNothingIsRefused() {
        app = TheApp.launchedAfresh("e: try [browse %nothing.html] print ['refused e/id]");

        assertThat(app.waitForLineStarting("refused ", PATIENCE)).isEqualTo("refused cannot-open");
        assertThat(app.isInFront()).isTrue();
    }
}
