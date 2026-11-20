package org.jebol.android.endtoend;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

final class ThePicker {

    static final String FIXTURE_FOLDER_NAME = "jebol-e2e";
    static final String FIXTURE_FOLDER = "/sdcard/Download/" + FIXTURE_FOLDER_NAME + "/";

    private static final Duration PATIENCE = Duration.ofSeconds(20);
    private static final Pattern DOCUMENTS_UI = Pattern.compile(".*documentsui.*");
    private static final Pattern USE_THIS_FOLDER = Pattern.compile("(?i)use this folder");
    private static final Pattern ALLOW = Pattern.compile("(?i)allow");
    private static final Pattern SAVE = Pattern.compile("(?i)save");
    private static final String SHOW_ROOTS = "Show roots";
    private static final String DOWNLOAD = "Download";
    private static final String ROOT_TITLE = "android:id/title";
    private static final Duration TO_CLOSE = Duration.ofSeconds(3);
    private static final int MOST_BACKS = 8;

    private final TheApp app;
    private final UiDevice device;

    ThePicker(TheApp app) {
        this.app = app;
        this.device = app.device();
    }

    void waitUntilShown() {
        if (!device.wait(Until.hasObject(By.pkg(DOCUMENTS_UI)), PATIENCE.toMillis())) {
            throw new AssertionError("Android's picker did not open in " + PATIENCE);
        }
    }

    void goToFixtureFolder() {
        waitUntilShown();
        tap(By.desc(SHOW_ROOTS));
        tap(By.res(ROOT_TITLE).text(deviceRootTitle()));
        tap(By.res(Pattern.compile(".*title")).text(DOWNLOAD).clazz("android.widget.TextView"));
        waitFor(By.text(DOWNLOAD).res(Pattern.compile(".*breadcrumb.*")));
        openFolder(FIXTURE_FOLDER_NAME);
    }

    void choose(String name) {
        scrollTo(By.text(name)).click();
    }

    void openFolder(String name) {
        scrollTo(By.text(name)).click();
        waitFor(By.text(name).res(Pattern.compile(".*breadcrumb.*")));
    }

    void useThisFolder() {
        tap(By.text(USE_THIS_FOLDER));
        tap(By.text(ALLOW));
    }

    void save() {
        tap(By.res("android:id/button1").text(SAVE));
    }

    void cancel() {
        waitUntilShown();
        for (int pressed = 0; pressed < MOST_BACKS && isPickerInFront(); pressed++) {
            device.pressBack();
            device.wait(Until.gone(By.pkg(DOCUMENTS_UI)), TO_CLOSE.toMillis());
            device.waitForIdle();
        }
        if (isPickerInFront()) {
            throw new AssertionError("Back did not close the picker in " + MOST_BACKS + " presses");
        }
    }

    private boolean isPickerInFront() {
        return DOCUMENTS_UI.matcher(String.valueOf(device.getCurrentPackageName())).matches();
    }

    private String deviceRootTitle() {
        List<String> candidates = List.of(app.shell("settings get global device_name").trim(), "Internal storage");
        for (String candidate : candidates) {
            if (device.wait(Until.hasObject(By.res(ROOT_TITLE).text(candidate)), Duration.ofSeconds(2).toMillis())) {
                return candidate;
            }
        }
        throw new AssertionError("the picker shows no root named " + candidates);
    }

    private UiObject2 scrollTo(BySelector selector) {
        UiObject2 shown = device.wait(Until.findObject(selector), TO_CLOSE.toMillis());
        if (shown != null) {
            return shown;
        }
        UiObject2 list = waitFor(By.scrollable(true).pkg(DOCUMENTS_UI));
        UiObject2 found = list.scrollUntil(Direction.DOWN, Until.findObject(selector));
        if (found == null) {
            throw new AssertionError("the picker's list holds no " + selector);
        }
        return found;
    }

    private void tap(BySelector selector) {
        waitFor(selector).click();
    }

    private UiObject2 waitFor(BySelector selector) {
        UiObject2 found = device.wait(Until.findObject(selector), PATIENCE.toMillis());
        if (found == null) {
            throw new AssertionError("the picker showed no " + selector + " in " + PATIENCE);
        }
        return found;
    }
}
