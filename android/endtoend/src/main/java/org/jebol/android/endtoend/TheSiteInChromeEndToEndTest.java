package org.jebol.android.endtoend;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import org.jebol.testing.Needs;
import org.jebol.testing.RunsOn;
import org.jebol.testing.TestTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TheSiteInChromeEndToEndTest {

    private static final String CHROME = "com.android.chrome";
    private static final Duration PATIENCE = Duration.ofSeconds(90);
    private static final int[] STARTING_GREEN = {40, 160, 60};
    private static final int[] SECOND_BLUE = {40, 60, 200};
    private static final int SAMPLE_STEP = 16;
    private static final String CHROMES_FLAGS = "_ --disable-fre --no-default-browser-check --no-first-run\n";

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final UiDevice device = UiDevice.getInstance(instrumentation);

    @Test
    @RunsOn(value = TestTarget.ANDROID, requires = Needs.CHROME_WITH_EXNREF,
            because = "it opens the shipped web build in the device's own Chrome")
    @DisplayName("Chrome on the device opens the shipped site, and a swipe from the screen's edge moves the site's history")
    void aSwipeFromTheEdgeMovesTheSitesHistory() {
        String host = TheHost.address();
        String port = TheHost.port("hostSitePort");
        TheApp.notLaunched().wakeAndPassSwipeScreen();
        shell("pm grant " + CHROME + " android.permission.POST_NOTIFICATIONS");
        shell("cmd overlay enable com.android.internal.systemui.navbar.gestural");
        shell("am set-debug-app --persistent " + CHROME);
        writeThroughTheShell("/data/local/tmp/chrome-command-line", CHROMES_FLAGS);
        shell("am force-stop " + CHROME);
        Intent opening = new Intent(Intent.ACTION_VIEW, Uri.parse("http://" + host + ":" + port + "/"));
        opening.setPackage(CHROME);
        opening.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        instrumentation.getContext().startActivity(opening);
        try {
            assertThat(waitUntilShowing(STARTING_GREEN))
                    .as(() -> "the site opened at its first place; " + describeScreen()).isTrue();
            int middle = device.getDisplayHeight() / 2;
            device.click(device.getDisplayWidth() / 2, middle / 2);
            assertThat(waitUntilShowing(SECOND_BLUE))
                    .as(() -> "the tap took the site to its second place; " + describeScreen()).isTrue();
            device.swipe(1, middle, device.getDisplayWidth() / 2, middle, 30);
            assertThat(waitUntilShowing(STARTING_GREEN))
                    .as(() -> "the swipe took the site back to its first place; " + describeScreen()).isTrue();
        } finally {
            shell("am force-stop " + CHROME);
        }
    }

    private boolean waitUntilShowing(int[] colour) {
        long givenUpAt = System.nanoTime() + PATIENCE.toNanos();
        while (System.nanoTime() < givenUpAt) {
            if (isShowing(colour)) {
                return true;
            }
            device.waitForIdle(500);
        }
        return false;
    }

    private boolean isShowing(int[] colour) {
        File shot = new File(instrumentation.getContext().getCacheDir(), "chrome.png");
        if (!device.takeScreenshot(shot)) {
            return false;
        }
        Bitmap screen = BitmapFactory.decodeFile(shot.getPath());
        if (screen == null) {
            return false;
        }
        for (int y = 0; y < screen.getHeight(); y += SAMPLE_STEP) {
            for (int x = 0; x < screen.getWidth(); x += SAMPLE_STEP) {
                int pixel = screen.getPixel(x, y);
                if (Math.abs(Color.red(pixel) - colour[0]) <= 3 && Math.abs(Color.green(pixel) - colour[1]) <= 3
                        && Math.abs(Color.blue(pixel) - colour[2]) <= 3) {
                    return true;
                }
            }
        }
        return false;
    }

    private String describeScreen() {
        return "in front: " + windowInFront() + "; text showing: " + textShowing() + "; commonest colours: " + commonestColours();
    }

    private String windowInFront() {
        for (String line : shell("dumpsys window").split("\n")) {
            if (line.contains("mCurrentFocus=")) {
                return line.trim();
            }
        }
        return "nothing focused";
    }

    private String textShowing() {
        List<String> texts = new ArrayList<>();
        for (UiObject2 found : device.findObjects(By.textContains(""))) {
            String text = found.getText();
            if (text != null && !text.isBlank() && texts.size() < 20) {
                texts.add(text.strip());
            }
        }
        return texts.isEmpty() ? "none" : String.join(" | ", texts);
    }

    private String commonestColours() {
        File shot = new File(instrumentation.getContext().getCacheDir(), "chrome.png");
        Bitmap screen = device.takeScreenshot(shot) ? BitmapFactory.decodeFile(shot.getPath()) : null;
        if (screen == null) {
            return "no screenshot";
        }
        Map<Integer, Integer> counts = new HashMap<>();
        for (int y = 0; y < screen.getHeight(); y += SAMPLE_STEP) {
            for (int x = 0; x < screen.getWidth(); x += SAMPLE_STEP) {
                counts.merge(screen.getPixel(x, y) & 0xFFFFFF, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> String.format("#%06X x%d", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private void writeThroughTheShell(String path, String text) {
        ParcelFileDescriptor[] streams = instrumentation.getUiAutomation().executeShellCommandRw("tee " + path);
        try (FileOutputStream writing = new ParcelFileDescriptor.AutoCloseOutputStream(streams[1]);
             FileInputStream reading = new ParcelFileDescriptor.AutoCloseInputStream(streams[0])) {
            writing.write(text.getBytes(StandardCharsets.UTF_8));
            writing.close();
            reading.readAllBytes();
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    private String shell(String command) {
        ParcelFileDescriptor output = instrumentation.getUiAutomation().executeShellCommand(command);
        try (FileInputStream reading = new ParcelFileDescriptor.AutoCloseInputStream(output)) {
            return new String(reading.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }
}
