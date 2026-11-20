package org.jebol.android.endtoend;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AScriptInTheAppEndToEndTest {

    private static final Duration PATIENCE = Duration.ofSeconds(30);

    private static final Duration A_MOMENT_TO_LEAVE_IN = Duration.ofSeconds(3);

    private static final String A_FACE_THAT_HEARS_TAPS = """
            window: view/no-wait make gob! [size: 300x200 color: 200.40.40]
            window/offset: 10x150
            show system/view/screen-gob
            handle-events [
                name: 'taps
                priority: 100
                handler: func [event] [
                    if event/type = 'up [print rejoin ["up-at " event/offset]]
                    if event/type = 'key [print rejoin ["key " to integer! event/key]]
                    if event/type = 'resize [print rejoin ["resize " event/offset]]
                    if find [minimize restore] event/type [print form event/type]
                    none
                ]
            ]
            print "ready"
            do-events
            """;

    private static final String A_FIELD_THAT_SAYS_WHAT_IS_SELECTED = """
            w: view/no-wait [words: field 300x24 "hello world"]
            walk: func [gob] [
                foreach sub any [gob/pane []] [
                    if all [object? sub/data 'words = select sub/data 'name] [words: sub/data]
                    walk sub
                ]
            ]
            walk w
            shown: first words/gob/pane
            world-at: second map-gob-offset/reverse shown
                1x8 + caret-to-offset shown back tail shown/text at words/facets/text-edit 9
            selected: does [
                any [all [words/state/mark-head copy/part words/state/mark-head words/state/mark-tail] "nothing"]
            ]
            caret: does [either words/state/cursor [index? words/state/cursor] [0]]
            loop 4 [wait [gui-event-port 0]]
            print rejoin ["world-at " world-at]
            was: none
            forever [
                wait [gui-event-port 0:00:00.1]
                now-selected: rejoin [selected " at " caret]
                if now-selected <> was [was: now-selected  print rejoin ["selected " now-selected]]
            ]
            """;

    @Test
    @DisplayName("a double tap on a field's word selects the word, as Android's own text fields do")
    void aDoubleTapSelectsAWord() {
        TheApp app = TheApp.runningScript(A_FIELD_THAT_SAYS_WHAT_IS_SELECTED);
        int[] world = app.readPairAfter(app.waitForLineStarting("world-at ", PATIENCE), "world-at ");

        app.doubleTapAt(world);

        assertThat(app.waitForLineStarting("selected world", PATIENCE)).startsWith("selected world at ");
    }

    @Test
    @DisplayName("a single tap on a field's word places the caret there and selects nothing")
    void aSingleTapPlacesTheCaret() {
        TheApp app = TheApp.runningScript(A_FIELD_THAT_SAYS_WHAT_IS_SELECTED);
        int[] world = app.readPairAfter(app.waitForLineStarting("world-at ", PATIENCE), "world-at ");

        app.tapAt(world);

        assertThat(app.waitForLineStarting("selected nothing at 9", PATIENCE)).isEqualTo("selected nothing at 9");
        assertThat(app.printed()).noneMatch(line -> line.startsWith("selected world"));
    }

    @Test
    @DisplayName("what a script prints is a line in logcat under the tag jebol")
    void printsToLogcat() {
        TheApp app = TheApp.runningScript("""
                print "hello, logcat"
                prin "held "
                print "until it ends"
                """);

        assertThat(app.waitForLineStarting("hello, logcat", PATIENCE)).isEqualTo("hello, logcat");
        assertThat(app.waitForLineStarting("held", PATIENCE)).isEqualTo("held until it ends");
    }

    @Test
    @DisplayName("QUIT leaves the app, and nothing is logged as an error")
    void quitLeavesTheApp() {
        TheApp app = TheApp.runningScript("""
                view/no-wait make gob! [size: 300x200 color: 40.200.40]
                print "ready"
                quit
                """);
        app.waitForLineStarting("ready", PATIENCE);

        app.device().wait(Until.gone(By.pkg(TheApp.PACKAGE).depth(0)), PATIENCE.toMillis());

        assertThat(app.isInFront()).isFalse();
        assertThat(app.printedAsErrors()).isEmpty();
    }

    @Test
    @DisplayName("a script that runs to its end leaves its window showing")
    void aScriptThatEndsStays() {
        TheApp app = TheApp.runningScript("""
                view/no-wait make gob! [size: 300x200 color: 40.40.200]
                print "ended"
                """);
        app.waitForLineStarting("ended", PATIENCE);

        boolean left = app.device().wait(Until.gone(By.pkg(TheApp.PACKAGE).depth(0)), A_MOMENT_TO_LEAVE_IN.toMillis());

        assertThat(left).isFalse();
        assertThat(app.isInFront()).isTrue();
        assertThat(app.printedAsErrors()).isEmpty();
    }

    @Test
    @DisplayName("a tap on a face reaches its handler at its place in the window, in dp")
    void aTapClicksAFace() {
        TheApp app = TheApp.runningScript(A_FACE_THAT_HEARS_TAPS);
        app.waitForLineStarting("ready", PATIENCE);

        app.tapAt(new int[] {110, 200});

        assertThat(app.waitForLineStarting("up-at", PATIENCE)).isEqualTo("up-at 100x50");
    }

    @Test
    @DisplayName("a window placed past the screen's right edge is shown against it, and a tap counts from where it is shown")
    void aTapClicksAWindowMovedOnToTheScreen() {
        TheApp app = TheApp.runningScript("""
                window: view/no-wait make gob! [size: 300x200 color: 40.200.200]
                window/offset: 5000x150
                show system/view/screen-gob
                handle-events [
                    name: 'taps
                    priority: 100
                    handler: func [event] [
                        if event/type = 'up [print rejoin ["up-at " event/offset]]
                        none
                    ]
                ]
                print rejoin ["ready " to integer! system/view/screen-gob/size/x]
                do-events
                """);
        int screenWide = Integer.parseInt(app.waitForLineStarting("ready", PATIENCE).substring("ready ".length()));

        app.tapAt(new int[] {screenWide - 50, 200});

        assertThat(app.waitForLineStarting("up-at", PATIENCE)).isEqualTo("up-at 250x50");
    }

    @Test
    @DisplayName("keys typed reach the script as key events, one a character")
    void typingReachesTheScript() {
        TheApp app = TheApp.runningScript(A_FACE_THAT_HEARS_TAPS);
        app.waitForLineStarting("ready", PATIENCE);
        app.tapAt(new int[] {200, 200});

        app.typeOnKeys("hi");

        assertThat(app.waitForLineStartingOtherThan("key", "key 104", PATIENCE)).isEqualTo("key 105");
        assertThat(app.printed()).contains("key 104");
    }

    @Test
    @DisplayName("turning the device reaches the script as a resize")
    void aRotationIsAResize() {
        TheApp app = TheApp.runningScript("""
                window: view/no-wait make gob! [size: 300x200 color: 40.40.200]
                handle-events [
                    name: 'sizes
                    priority: 100
                    handler: func [event] [
                        if event/type = 'resize [print rejoin ["resize " event/offset]]
                        none
                    ]
                ]
                gui-metric/set 'maximize window
                print rejoin ["ready " system/view/screen-gob/size]
                do-events
                """);
        String upright = app.waitForLineStarting("ready", PATIENCE).substring("ready ".length());

        app.turnSideways();

        String sideways = app.waitForLineStartingOtherThan("resize", "resize " + upright, PATIENCE);
        assertThat(sideways).isNotEqualTo("resize " + upright);
        app.turnUpright();
    }

    @Test
    @DisplayName("Home, then coming back, reaches the script as minimize then restore")
    void theBackgroundIsMinimizeAndRestore() {
        TheApp app = TheApp.runningScript(A_FACE_THAT_HEARS_TAPS);
        app.waitForLineStarting("ready", PATIENCE);

        app.pressHome();
        assertThat(app.waitForLineStarting("minimize", PATIENCE)).isEqualTo("minimize");
        app.bringBack();

        assertThat(app.waitForLineStarting("restore", PATIENCE)).isEqualTo("restore");
    }
}
