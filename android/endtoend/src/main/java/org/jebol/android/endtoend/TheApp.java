package org.jebol.android.endtoend;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class TheApp {

    static final String PACKAGE = "org.jebol.android";

    private static final String LOG_TAG = "jebol";
    private static final String SCRIPT_EXTRA = "script";
    private static final String RESTART_EXTRA = "restart";
    private static final String TRUST_EXTRA = "trust";
    private static final String RESTART_MARKER = "restarted ";
    private static final int COLOUR_SAMPLE_STEP = 12;
    private static final Duration TO_OPEN = Duration.ofSeconds(30);
    private static final long POLL_MILLISECONDS = 200;
    private static final int COLOUR_ALLOWANCE = 6;

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final UiDevice device = UiDevice.getInstance(instrumentation);
    private final Context context = instrumentation.getContext();
    private final float density = context.getResources().getDisplayMetrics().density;
    private final String packageName;

    private TheApp() {
        this(PACKAGE);
    }

    private TheApp(String packageName) {
        this.packageName = packageName;
    }

    static TheApp notLaunched() {
        return new TheApp();
    }

    static TheApp launchedAfreshFromPackage(String packageName) {
        TheApp app = new TheApp(packageName);
        app.startAfresh(Optional.empty());
        return app;
    }

    static TheApp runningScript(String script) {
        TheApp app = new TheApp();
        app.restartOrStart(Optional.of(script));
        return app;
    }

    static TheApp runningWhatItCarries() {
        TheApp app = new TheApp();
        app.restartOrStart(Optional.empty());
        return app;
    }

    static TheApp launchedAfresh(String script) {
        TheApp app = new TheApp();
        app.startAfresh(Optional.of(script));
        return app;
    }

    static TheApp launchedAfreshWithWhatItCarries() {
        TheApp app = new TheApp();
        app.startAfresh(Optional.empty());
        return app;
    }

    static TheApp launchedAfreshTrusting(String script, String roots) {
        TheApp app = new TheApp();
        app.startAfresh(Optional.of(script), Optional.of(roots));
        return app;
    }

    void endProcess() {
        shell("am force-stop " + packageName);
    }

    private void restartOrStart(Optional<String> script) {
        if (isInFront()) {
            restartInPlace(script);
        } else {
            startAfresh(script);
        }
    }

    private void startAfresh(Optional<String> script) {
        startAfresh(script, Optional.empty());
    }

    private void startAfresh(Optional<String> script, Optional<String> roots) {
        shell("am force-stop " + packageName);
        shell("logcat -c");
        turnUpright();
        Intent launch = launchIntent();
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        script.ifPresent(source -> launch.putExtra(SCRIPT_EXTRA, source));
        roots.ifPresent(pem -> launch.putExtra(TRUST_EXTRA, pem));
        context.startActivity(launch);
        waitUntilInFront();
    }

    void restartWith(String script) {
        restartInPlace(Optional.of(script));
    }

    void restartOnItsSide() {
        turnSideways();
        restartInPlaceTurned(Optional.empty());
    }

    private void restartInPlace(Optional<String> script) {
        turnUpright();
        restartInPlaceTurned(script);
    }

    private void restartInPlaceTurned(Optional<String> script) {
        long restartsBefore = markers().count();
        Intent restart = launchIntent();
        restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        restart.putExtra(RESTART_EXTRA, true);
        script.ifPresent(source -> restart.putExtra(SCRIPT_EXTRA, source));
        context.startActivity(restart);
        long givenUpAt = System.nanoTime() + TO_OPEN.toNanos();
        while (markers().count() == restartsBefore) {
            if (System.nanoTime() > givenUpAt) {
                throw new AssertionError(packageName + " did not restart in place in " + TO_OPEN + "; printed: "
                        + printedSinceLaunch());
            }
            pause();
        }
        waitUntilInFront();
    }

    private Stream<String> markers() {
        return printedSinceLaunch().stream().filter(line -> line.startsWith(RESTART_MARKER));
    }

    void bringBack() {
        Intent launch = launchIntent();
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launch);
        waitUntilInFront();
    }

    private Intent launchIntent() {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            throw new IllegalStateException(packageName + " is not installed with a launcher activity");
        }
        return launch;
    }

    void waitUntilInFront() {
        if (!device.wait(Until.hasObject(By.pkg(packageName).depth(0)), TO_OPEN.toMillis())) {
            throw new AssertionError(packageName + " did not come to the front in " + TO_OPEN);
        }
    }

    boolean isInFront() {
        return packageName.equals(device.getCurrentPackageName());
    }

    List<String> printed() {
        return loggedSinceRestart().stream().map(LogLine::text).toList();
    }

    List<String> printedAsErrors() {
        return loggedSinceRestart().stream().filter(LogLine::isError).map(LogLine::text).toList();
    }

    private List<String> printedSinceLaunch() {
        return loggedSinceLaunch().stream().map(LogLine::text).toList();
    }

    private List<LogLine> loggedSinceRestart() {
        List<LogLine> lines = loggedSinceLaunch();
        for (int at = lines.size() - 1; at >= 0; at--) {
            if (lines.get(at).text().startsWith(RESTART_MARKER)) {
                return lines.subList(at + 1, lines.size());
            }
        }
        return lines;
    }

    private List<LogLine> loggedSinceLaunch() {
        return shell("logcat -d -v tag -s " + LOG_TAG + ":*").lines()
                .filter(line -> !line.startsWith("---------"))
                .map(LogLine::parse)
                .toList();
    }

    private record LogLine(char priority, String text) {

        static LogLine parse(String tagged) {
            int textStarts = tagged.indexOf(": ");
            return new LogLine(tagged.charAt(0), textStarts < 0 ? "" : tagged.substring(textStarts + 2));
        }

        boolean isError() {
            return priority == 'E';
        }
    }

    String waitForLineStarting(String start, Duration patience) {
        long givenUpAt = System.nanoTime() + patience.toNanos();
        while (System.nanoTime() < givenUpAt) {
            Optional<String> found = printed().stream().filter(line -> line.startsWith(start)).reduce((a, b) -> b);
            if (found.isPresent()) {
                return found.get();
            }
            pause();
        }
        throw new AssertionError("nothing starting " + start + " was printed in " + patience + "; printed: "
                + printed());
    }

    String waitForLineStartingOtherThan(String start, String was, Duration patience) {
        long givenUpAt = System.nanoTime() + patience.toNanos();
        while (System.nanoTime() < givenUpAt) {
            Optional<String> found = printed().stream().filter(line -> line.startsWith(start))
                    .filter(line -> !line.equals(was)).reduce((a, b) -> b);
            if (found.isPresent()) {
                return found.get();
            }
            pause();
        }
        throw new AssertionError("nothing starting " + start + " other than " + was + " was printed in "
                + patience + "; printed: " + printed());
    }

    int[] readPairAfter(String line, String start) {
        String[] parts = line.substring(start.length()).trim().split("x");
        return new int[] {(int) Double.parseDouble(parts[0]), (int) Double.parseDouble(parts[1])};
    }

    void tapAt(int[] dp) {
        Rect view = theViewsBounds();
        device.click(view.left + toPixels(dp[0]), view.top + toPixels(dp[1]));
    }

    void doubleTapAt(int[] dp) {
        Rect view = theViewsBounds();
        int across = view.left + toPixels(dp[0]);
        int down = view.top + toPixels(dp[1]);
        device.click(across, down);
        device.click(across, down);
    }

    void drag(int[] fromDp, int[] toDp) {
        Rect view = theViewsBounds();
        device.drag(view.left + toPixels(fromDp[0]), view.top + toPixels(fromDp[1]),
                view.left + toPixels(toDp[0]), view.top + toPixels(toDp[1]), 40);
    }

    Rect theViewsBounds() {
        UiObject2 view = device.wait(Until.findObject(By.pkg(packageName).clazz("android.view.View")), TO_OPEN.toMillis());
        if (view == null) {
            throw new AssertionError("the app shows no view to touch");
        }
        return view.getVisibleBounds();
    }

    void typeOnKeys(String text) {
        shell("input text " + text);
    }

    boolean isKeyboardShown() {
        return shell("dumpsys input_method").contains("mInputShown=true");
    }

    boolean waitUntilKeyboardShownIs(boolean shown, Duration patience) {
        long givenUpAt = System.nanoTime() + patience.toNanos();
        while (isKeyboardShown() != shown) {
            if (System.nanoTime() > givenUpAt) {
                return false;
            }
            pause();
        }
        return true;
    }

    boolean waitUntilShowingColourIs(boolean showing, int[] colour, Duration patience) {
        long givenUpAt = System.nanoTime() + patience.toNanos();
        while (isShowingColour(colour[0], colour[1], colour[2]) != showing) {
            if (System.nanoTime() > givenUpAt) {
                return false;
            }
            pause();
        }
        return true;
    }

    private boolean isShowingColour(int red, int green, int blue) {
        Rect bounds = theViewsBounds();
        File shot = new File(context.getCacheDir(), "the-view.png");
        if (!device.takeScreenshot(shot)) {
            throw new IllegalStateException("no screenshot could be taken");
        }
        Bitmap screen = BitmapFactory.decodeFile(shot.getPath());
        int sought = Color.rgb(red, green, blue);
        try {
            for (int y = bounds.top; y < bounds.bottom; y += COLOUR_SAMPLE_STEP) {
                for (int x = bounds.left; x < bounds.right; x += COLOUR_SAMPLE_STEP) {
                    if (screen.getPixel(x, y) == sought) {
                        return true;
                    }
                }
            }
            return false;
        } finally {
            screen.recycle();
            shot.delete();
        }
    }

    void pressBack() {
        device.pressBack();
    }

    void pressHome() {
        device.pressHome();
    }

    void turnSideways() {
        try {
            device.setOrientationLeft();
        } catch (android.os.RemoteException failed) {
            throw new IllegalStateException(failed);
        }
    }

    void turnUpright() {
        try {
            device.setOrientationNatural();
        } catch (android.os.RemoteException failed) {
            throw new IllegalStateException(failed);
        }
    }

    void endTheProcess() {
        shell("am kill " + packageName);
    }

    boolean isProcessAlive() {
        return !shell("pidof " + packageName).isBlank();
    }

    UiDevice device() {
        return device;
    }

    private int toPixels(int dp) {
        return Math.round(dp * density + density / 2);
    }

    int readColourAt(int[] dp) {
        Rect view = theViewsBounds();
        return takeScreenshot().getPixel(view.left + toPixels(dp[0]), view.top + toPixels(dp[1]));
    }

    boolean isShowingAboveTheKeyboard(int[] dp, int colour) {
        Rect view = theViewsBounds();
        int keyboardTop = readTheKeyboardsTop().orElse(view.bottom);
        Bitmap screen = takeScreenshot();
        int across = view.left + toPixels(dp[0]);
        for (int y = view.top; y < Math.min(keyboardTop, screen.getHeight()); y++) {
            if (isCloseTo(screen.getPixel(across, y), colour)) {
                return true;
            }
        }
        return false;
    }

    private Bitmap takeScreenshot() {
        File shot = new File(context.getCacheDir(), "the-view.png");
        if (!device.takeScreenshot(shot)) {
            throw new IllegalStateException("no screenshot could be taken");
        }
        return BitmapFactory.decodeFile(shot.getPath());
    }

    private static boolean isCloseTo(int pixel, int colour) {
        return Math.abs(Color.red(pixel) - Color.red(colour)) <= COLOUR_ALLOWANCE
                && Math.abs(Color.green(pixel) - Color.green(colour)) <= COLOUR_ALLOWANCE
                && Math.abs(Color.blue(pixel) - Color.blue(colour)) <= COLOUR_ALLOWANCE;
    }

    private Optional<Integer> readTheKeyboardsTop() {
        String keyboard = shell("settings get secure default_input_method").trim();
        int slash = keyboard.indexOf('/');
        if (slash <= 0) {
            return Optional.empty();
        }
        UiObject2 shown = device.findObject(By.pkg(keyboard.substring(0, slash)));
        return shown == null ? Optional.empty() : Optional.of(shown.getVisibleBounds().top);
    }

    int[] theViewsSizeInDp() {
        Rect view = theViewsBounds();
        return new int[] {Math.round(view.width() / density), Math.round(view.height() / density)};
    }

    String shell(String command) {
        try {
            return device.executeShellCommand(command);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    String runInShell(String script) {
        ParcelFileDescriptor[] ends = instrumentation.getUiAutomation().executeShellCommandRw("sh");
        try (InputStream printed = new ParcelFileDescriptor.AutoCloseInputStream(ends[0])) {
            try (OutputStream typed = new ParcelFileDescriptor.AutoCloseOutputStream(ends[1])) {
                typed.write(script.getBytes(StandardCharsets.UTF_8));
            }
            return new String(printed.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    private static void pause() {
        try {
            Thread.sleep(POLL_MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
