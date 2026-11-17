package org.jebol.android.timings;

import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AndroidTimingsTest {

    private static final String THE_APP = "org.jebol.android";
    private static final String ACTIVITY = THE_APP + "/.JebolActivity";
    private static final String TIMING_TAG = "jebol-timing";
    private static final String MEASURED_TAG = "jebol-measured";
    private static final String MEASURED_FILE = "timings.txt";
    private static final int TIMES = 10;
    private static final long PATIENCE_MILLISECONDS = 60_000;
    private static final Pattern TOTAL_TIME = Pattern.compile("TotalTime: (\\d+)");

    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private final float density = InstrumentationRegistry.getInstrumentation().getContext()
            .getResources().getDisplayMetrics().density;

    @BeforeEach
    void switchTheTimingLinesOn() {
        shell("setprop log.tag." + TIMING_TAG + " DEBUG");
    }

    @AfterEach
    void switchTheTimingLinesOff() {
        shell("setprop log.tag." + TIMING_TAG + " INFO");
    }

    @Test
    @DisplayName("cold start to the first picture, ten launches")
    void measuresColdStart() {
        List<Long> activityTotals = new ArrayList<>();
        List<Long> toFirstPicture = new ArrayList<>();
        for (int launch = 0; launch < TIMES; launch++) {
            shell("am force-stop " + THE_APP);
            shell("logcat -c");
            long asked = SystemClock.uptimeMillis();
            Matcher total = TOTAL_TIME.matcher(shell("am start -W -n " + ACTIVITY));
            assertThat(total.find()).as("am start -W reports a TotalTime").isTrue();
            activityTotals.add(Long.parseLong(total.group(1)));
            toFirstPicture.add(waitForTimed("first-picture") - asked);
        }

        record("cold start, am start -W TotalTime", activityTotals);
        record("cold start, asked to first picture drawn", toFirstPicture);
    }

    @Test
    @DisplayName("a tap on the slider to the end of the repaint it causes, ten taps")
    void measuresTapToRepaint() {
        int[] slider = openTheExamples();
        List<Long> tapToRepaint = new ArrayList<>();
        for (int tap = 0; tap < TIMES; tap++) {
            int across = slider[0] + (tap % 2 == 0 ? -40 : 40);
            shell("logcat -c");
            Rect view = theViewsBounds();
            device.click(view.left + toPixels(across), view.top + toPixels(slider[1]));
            long released = waitForTimed("touched up");
            tapToRepaint.add(waitForTimedAfter("drawn", released) - released);
            SystemClock.sleep(300);
        }

        record("tap to repaint", tapToRepaint);
    }

    @Test
    @DisplayName("frame times while the slider is dragged")
    void measuresFramesWhileDragging() {
        int[] slider = openTheExamples();
        shell("logcat -c");

        Rect view = theViewsBounds();
        device.drag(view.left + toPixels(slider[0] - 60), view.top + toPixels(slider[1]),
                view.left + toPixels(slider[0] + 60), view.top + toPixels(slider[1]), 200);
        long released = waitForTimed("touched up");
        SystemClock.sleep(500);

        List<Long> drawn = timedLines("drawn");
        long pressed = timedLines("touched down").getFirst();
        List<Long> frames = new ArrayList<>();
        long previous = pressed;
        for (long at : drawn) {
            if (at > pressed && at <= released + 100) {
                frames.add(at - previous);
                previous = at;
            }
        }
        assertThat(frames).as("the drag repainted the slider").isNotEmpty();

        record("frame time while dragging the slider", frames);
    }

    private int[] openTheExamples() {
        shell("am force-stop " + THE_APP);
        shell("logcat -c");
        shell("am start -W -n " + ACTIVITY);
        String line = waitForLine("jebol", "slider-at");
        String[] parts = line.substring("slider-at".length()).trim().split("x");
        waitForLine("jebol", "open");
        SystemClock.sleep(1000);
        return new int[] {(int) Double.parseDouble(parts[0]), (int) Double.parseDouble(parts[1])};
    }

    private void record(String what, List<Long> milliseconds) {
        List<Long> sorted = milliseconds.stream().sorted().toList();
        long median = sorted.get(sorted.size() / 2);
        long ninetyFifth = sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95) - 1));
        String measured = what + ": median " + median + " ms, 95th percentile " + ninetyFifth + " ms, "
                + sorted.size() + " samples " + milliseconds;
        Log.i(MEASURED_TAG, measured);
        File kept = new File(InstrumentationRegistry.getInstrumentation().getContext().getExternalFilesDir(null),
                MEASURED_FILE);
        try {
            Files.write(kept.toPath(), (measured + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    private long waitForTimed(String what) {
        return waitForTimedAfter(what, Long.MIN_VALUE);
    }

    private long waitForTimedAfter(String what, long after) {
        long givenUpAt = SystemClock.uptimeMillis() + PATIENCE_MILLISECONDS;
        while (SystemClock.uptimeMillis() < givenUpAt) {
            Optional<Long> found = timedLines(what).stream().filter(at -> at > after).findFirst();
            if (found.isPresent()) {
                return found.get();
            }
            SystemClock.sleep(50);
        }
        throw new AssertionError("no " + what + " line came in " + PATIENCE_MILLISECONDS + " ms");
    }

    private List<Long> timedLines(String what) {
        return shell("logcat -d -v raw -s " + TIMING_TAG + ":*").lines()
                .filter(line -> line.startsWith(what + " "))
                .map(line -> Long.parseLong(line.substring(line.lastIndexOf(' ') + 1).trim()))
                .toList();
    }

    private String waitForLine(String tag, String start) {
        long givenUpAt = SystemClock.uptimeMillis() + PATIENCE_MILLISECONDS;
        while (SystemClock.uptimeMillis() < givenUpAt) {
            Optional<String> found = shell("logcat -d -v raw -s " + tag + ":*").lines()
                    .filter(line -> line.startsWith(start)).findFirst();
            if (found.isPresent()) {
                return found.get();
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("nothing starting " + start + " was printed");
    }

    private Rect theViewsBounds() {
        UiObject2 view = device.wait(Until.findObject(By.pkg(THE_APP).clazz("android.view.View")),
                PATIENCE_MILLISECONDS);
        assertThat(view).as("the app shows a view to touch").isNotNull();
        return view.getVisibleBounds();
    }

    private int toPixels(int dp) {
        return Math.round(dp * density + density / 2);
    }

    private String shell(String command) {
        try {
            return device.executeShellCommand(command);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }
}
