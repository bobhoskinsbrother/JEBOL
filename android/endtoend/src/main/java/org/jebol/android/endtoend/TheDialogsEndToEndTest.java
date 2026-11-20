package org.jebol.android.endtoend;

import android.graphics.Rect;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TheDialogsEndToEndTest {

    private static final Duration PATIENCE = Duration.ofSeconds(30);
    private static final String ASK_FOR_A_PASSWORD = """
            answer: request-password
            print either none? answer ["declined"] [join "typed " mold answer]
            """;
    private static final String ASK_FOR_A_COLOUR = """
            answer: request-color
            print either none? answer ["declined"] [join "colour " mold answer]
            """;
    private static final BySelector OK = By.res("android:id/button1");
    private static final BySelector CANCEL = By.res("android:id/button2");

    private static UiObject2 waitFor(TheApp app, BySelector selector) {
        UiObject2 found = app.device().wait(Until.findObject(selector), PATIENCE.toMillis());
        if (found == null) {
            throw new AssertionError("the app showed no " + selector + " in " + PATIENCE);
        }
        return found;
    }

    @Test
    @DisplayName("REQUEST-PASSWORD is a password field: what is typed is masked, and OK answers it")
    void aPasswordIsTypedAndAnswered() {
        TheApp app = TheApp.runningScript(ASK_FOR_A_PASSWORD);
        waitFor(app, By.desc("password"));

        app.typeOnKeys("s3cret");

        assertThat(app.device().wait(Until.hasObject(By.desc("password").text("••••••")), PATIENCE.toMillis()))
                .isTrue();
        waitFor(app, OK).click();
        assertThat(app.waitForLineStarting("typed ", PATIENCE)).isEqualTo("typed \"s3cret\"");
    }

    @Test
    @DisplayName("Cancel on the password dialog answers none")
    void aPasswordCancelledIsNone() {
        TheApp app = TheApp.runningScript(ASK_FOR_A_PASSWORD);
        waitFor(app, By.desc("password"));
        app.typeOnKeys("unwanted");

        waitFor(app, CANCEL).click();

        assertThat(app.waitForLineStarting("declined", PATIENCE)).isEqualTo("declined");
    }

    @Test
    @DisplayName("Back on the password dialog answers none")
    void aPasswordBackedOutOfIsNone() {
        TheApp app = TheApp.runningScript(ASK_FOR_A_PASSWORD);
        waitFor(app, By.desc("password"));

        app.pressBack();
        if (!app.device().wait(Until.gone(By.desc("password")), Duration.ofSeconds(2).toMillis())) {
            app.pressBack();
        }

        assertThat(app.waitForLineStarting("declined", PATIENCE)).isEqualTo("declined");
    }

    @Test
    @DisplayName("the colour dialog answers the colour its hex field is given")
    void aHexColourIsAnswered() {
        TheApp app = TheApp.runningScript(ASK_FOR_A_COLOUR);

        waitFor(app, By.desc("hex")).setText("336699");
        waitFor(app, OK).click();

        assertThat(app.waitForLineStarting("colour ", PATIENCE)).isEqualTo("colour 51.102.153");
    }

    @Test
    @DisplayName("the colour dialog starts at /default's colour, and at white without one")
    void theColourDialogStartsAtTheDefault() {
        TheApp app = TheApp.runningScript("print ['colour mold request-color/default 255.0.170]");
        assertThat(waitFor(app, By.desc("hex")).getText()).isEqualTo("ff00aa");
        waitFor(app, OK).click();
        assertThat(app.waitForLineStarting("colour ", PATIENCE)).isEqualTo("colour 255.0.170");

        app.restartWith(ASK_FOR_A_COLOUR);
        assertThat(waitFor(app, By.desc("hex")).getText()).isEqualTo("ffffff");
    }

    @Test
    @DisplayName("moving the value slider to its end changes the swatch and the answer to black")
    void aSliderChangesTheAnswer() {
        TheApp app = TheApp.runningScript("print ['colour mold request-color/default 51.102.153]");
        Rect slider = waitFor(app, By.desc("value")).getVisibleBounds();

        app.device().swipe(slider.centerX(), slider.centerY(), slider.left - slider.width(), slider.centerY(), 20);

        assertThat(app.device().wait(Until.hasObject(By.desc("hex").text("000000")), PATIENCE.toMillis())).isTrue();
        waitFor(app, OK).click();
        assertThat(app.waitForLineStarting("colour ", PATIENCE)).isEqualTo("colour 0.0.0");
    }

    @Test
    @DisplayName("Cancel on the colour dialog answers none")
    void aColourCancelledIsNone() {
        TheApp app = TheApp.runningScript(ASK_FOR_A_COLOUR);

        waitFor(app, CANCEL).click();

        assertThat(app.waitForLineStarting("declined", PATIENCE)).isEqualTo("declined");
    }
}
