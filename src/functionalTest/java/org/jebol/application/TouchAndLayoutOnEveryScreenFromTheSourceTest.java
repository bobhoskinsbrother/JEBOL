package org.jebol.application;

import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;
import org.jebol.domain.host.WindowHistory;
import org.jebol.domain.render.DrawingCommand;
import org.jebol.domain.render.TextMeasurement;
import org.jebol.domain.value.PairValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TouchAndLayoutOnEveryScreenFromTheSourceTest {

    private static final String TRUE = "#(true)";
    private static final String FALSE = "#(false)";

    private static final String GUI_HELPERS = """
            face-named: func [window name /local found walk] [
                walk: func [gob] [
                    foreach sub any [gob/pane []] [
                        if all [object? sub/data name = select sub/data 'name] [found: sub/data]
                        walk sub
                    ]
                ]
                walk window
                found
            ]
            on-screen: func [window face] [
                second map-gob-offset/reverse face/gob face/gob/size / 2
            ]
            faces-within?: func [window wide /local fits walk] [
                fits: true
                walk: func [gob at] [
                    foreach sub any [gob/pane []] [
                        if all [
                            object? sub/data
                            sub = select sub/data 'gob
                            (at/x + sub/offset/x + sub/size/x) > (wide + 1)
                        ] [fits: false]
                        walk sub at + sub/offset
                    ]
                ]
                walk window 0x0
                fits
            ]
            clicked: false
            survey: [
                title "Opinion Survey"
                text "Do you want programs to be easy to build?"
                panel 2 [
                    label "Answer:"
                    group [radio "Agree" radio "Disagree" radio "Not sure"]
                    pad
                    check "I'm a programmer."
                    label "Name:"
                    name-field: field
                    label "Comment:"
                    comment-area: area
                ]
                group [
                    go: button "Submit" do [clicked: true]
                    button "Cancel" close
                ]
            ]""";

    private static final class SomebodyLooking implements OneSurfaceScreen.Viewer {

        private final List<List<DrawingCommand>> painted = new ArrayList<>();
        private int timesTypingTaken;
        private int timesTypingReleased;

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public TextMeasurement textMeasurement(DefaultFont carried) {
            return carried.measurement();
        }

        @Override
        public void paint(List<DrawingCommand> painting) {
            painted.add(painting);
        }

        @Override
        public void takeTyping(String heading) {
            timesTypingTaken++;
        }

        @Override
        public void releaseTyping() {
            timesTypingReleased++;
        }

        List<DrawingCommand> readTheLastPainted() {
            return painted.getLast();
        }
    }

    private final SomebodyLooking viewer = new SomebodyLooking();
    private final OneSurfaceScreen screen =
            OneSurfaceScreen.showTo(viewer, new WindowHistory(), new ClasspathFiles());

    private Interpreter onAScreenOf(int wide, int high) {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(screen),
                OutputPort.discard(),
                Bounds.standard()
                        .grant(HostService.WINDOWS)
                        .withTimeLimit(Duration.ofSeconds(50)));
        screen.measure(wide, high);
        answerTo(interpreter, GUI_HELPERS);
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static int[] readPlace(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        PairValue place = (PairValue) interpreter.run(source).value();
        return new int[] {(int) Math.round(place.x()), (int) Math.round(place.y())};
    }

    private static void deliver(Interpreter interpreter) {
        answerTo(interpreter, "loop 4 [wait [gui-event-port 0]]");
    }

    private void touch(ScreenEventType kind, int across, int down) {
        screen.report(kind, new ScreenEventDetail.Touched(across, down));
    }

    private void point(ScreenEventType kind, int across, int down) {
        screen.report(kind, new ScreenEventDetail.At(across, down));
    }

    private void tap(Interpreter interpreter, int[] at) {
        touch(ScreenEventType.DOWN, at[0], at[1]);
        touch(ScreenEventType.UP, at[0], at[1]);
        deliver(interpreter);
    }

    private void fingerDrags(Interpreter interpreter, int[] from, int[] to) {
        touch(ScreenEventType.DOWN, from[0], from[1]);
        for (int step = 1; step <= 6; step++) {
            touch(ScreenEventType.MOVE, from[0] + (to[0] - from[0]) * step / 6, from[1] + (to[1] - from[1]) * step / 6);
        }
        touch(ScreenEventType.UP, to[0], to[1]);
        deliver(interpreter);
    }

    private void mouseDrags(Interpreter interpreter, int[] from, int[] to) {
        point(ScreenEventType.DOWN, from[0], from[1]);
        for (int step = 1; step <= 6; step++) {
            point(ScreenEventType.MOVE, from[0] + (to[0] - from[0]) * step / 6, from[1] + (to[1] - from[1]) * step / 6);
        }
        point(ScreenEventType.UP, to[0], to[1]);
        deliver(interpreter);
    }

    private static int[] moved(int[] at, int across, int down) {
        return new int[] {at[0] + across, at[1] + down};
    }

    @Nested
    @DisplayName("the touch flag")
    class TheTouchFlag {

        private Interpreter watching(Interpreter interpreter) {
            answerTo(interpreter, """
                    seen: copy []
                    w: view/no-wait make gob! [size: 300x300 color: 10.20.30]
                    handle-events [
                        name: 'watching
                        priority: 90
                        handler: func [event] [
                            if find [down up move] event/type [
                                append/only seen reduce [event/type event/offset event/flags]
                            ]
                            event
                        ]
                    ]""");
            return interpreter;
        }

        @Test
        @DisplayName("a finger's press reaches the script saying touch")
        void aFingersPressSaysTouch() {
            Interpreter interpreter = watching(onAScreenOf(1280, 800));
            int[] corner = readPlace(interpreter, "w/offset");

            touch(ScreenEventType.DOWN, corner[0] + 10, corner[1] + 20);

            assertThat(answerTo(interpreter, "wait [system/ports/event 0]  seen"))
                    .isEqualTo("[[down 10x20 [touch]]]");
        }

        @Test
        @DisplayName("a mouse's press does not")
        void aMousesPressDoesNot() {
            Interpreter interpreter = watching(onAScreenOf(1280, 800));
            int[] corner = readPlace(interpreter, "w/offset");

            point(ScreenEventType.DOWN, corner[0] + 10, corner[1] + 20);

            assertThat(answerTo(interpreter, "wait [system/ports/event 0]  seen"))
                    .isEqualTo("[[down 10x20 _]]");
        }

        @Test
        @DisplayName("a script cannot make an event say it, as R3's MAKE EVENT! sets no flag")
        void aScriptCannotMakeIt() {
            Interpreter interpreter = onAScreenOf(1280, 800);

            assertThat(answerTo(interpreter, "error? try [make event! [type: 'down flags: [touch]]]"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("size-class?")
    class SizeClasses {

        @ParameterizedTest(name = "a screen {0} wide is {1}")
        @CsvSource({"384, compact", "599, compact", "600, medium", "839, medium", "840, expanded", "1280, expanded"})
        @DisplayName("of the screen, given none")
        void ofTheScreen(int wide, String sizeClass) {
            Interpreter interpreter = onAScreenOf(wide, 800);

            assertThat(answerTo(interpreter, "size-class? none")).isEqualTo(sizeClass);
        }

        @Test
        @DisplayName("of a window, its own width")
        void ofAWindow() {
            Interpreter interpreter = onAScreenOf(1280, 800);

            assertThat(answerTo(interpreter, """
                    reduce [
                        size-class? make gob! [size: 500x400]
                        size-class? make gob! [size: 700x400]
                        size-class? make gob! [size: 900x400]
                    ]""")).isEqualTo("[compact medium expanded]");
        }

        @Test
        @DisplayName("needs no GUI")
        void needsNoGui() {
            Interpreter interpreter = Interpreter.createOn(
                    AnInterpreter.standardPorts().withScreen(screen),
                    OutputPort.discard(),
                    Bounds.standard().grant(HostService.WINDOWS));
            screen.measure(384, 800);

            assertThat(answerTo(interpreter, "reduce [size-class? none value? 'guie]"))
                    .isEqualTo("[compact #(false)]");
        }
    }

    @Nested
    @DisplayName("a GUI window and its space")
    class TheLayout {

        @Test
        @DisplayName("opens at its natural size when that fits")
        void opensAtItsNaturalSizeWhenItFits() {
            Interpreter interpreter = onAScreenOf(1280, 800);

            assertThat(answerTo(interpreter, """
                    w: view/no-wait survey
                    natural: w/size
                    unview w
                    w: view/no-wait survey
                    w/size = natural""")).isEqualTo(TRUE);
            assertThat(answerTo(interpreter, "w/size/x < 1280")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("opens as wide as a screen narrower than it, with every face reflowed inside")
        void isReflowedToANarrowerScreen() {
            Interpreter interpreter = onAScreenOf(300, 800);

            assertThat(answerTo(interpreter, "w: view/no-wait survey  w/size/x")).isEqualTo("300.0");
            deliver(interpreter);
            assertThat(answerTo(interpreter, "faces-within? w 300")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but a plain gob window, which cannot take another size, keeps its own")
        void aFixedWindowKeepsItsSize() {
            Interpreter interpreter = onAScreenOf(300, 800);

            assertThat(answerTo(interpreter, "w: view/no-wait make gob! [size: 640x360]  w/size"))
                    .isEqualTo("640x360");
        }

        @Test
        @DisplayName("opens as tall as a screen shorter than it")
        void isAsTallAsAShorterScreen() {
            Interpreter interpreter = onAScreenOf(1280, 200);

            assertThat(answerTo(interpreter, "w: view/no-wait survey  w/size/y")).isEqualTo("200.0");
        }

        @Test
        @DisplayName("keeps an offset it was given wherever it still fits there")
        void keepsAGivenOffset() {
            Interpreter interpreter = onAScreenOf(1280, 800);

            assertThat(answerTo(interpreter, "w: view/no-wait/options survey [offset: 600x20]  w/offset"))
                    .isEqualTo("600x20");
        }

        @Test
        @DisplayName("reflows again when its space narrows, as a turn of the phone or a narrowed frame does")
        void reflowsWhenItsSpaceNarrows() {
            Interpreter interpreter = onAScreenOf(1280, 800);
            answerTo(interpreter, "w: view/no-wait survey");

            screen.measure(300, 800);
            deliver(interpreter);

            assertThat(answerTo(interpreter, "w/size/x")).isEqualTo("300.0");
            assertThat(answerTo(interpreter, "faces-within? w 300")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("a finger in the GUI")
    class AFinger {

        @Test
        @DisplayName("tapping a button clicks it")
        void aTapClicks() {
            Interpreter interpreter = onAScreenOf(1280, 800);
            answerTo(interpreter, "w: view/no-wait survey");

            tap(interpreter, readPlace(interpreter, "on-screen w face-named w 'go"));

            assertThat(answerTo(interpreter, "clicked")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("moving less than the slop before lifting is still a tap")
        void aSmallMoveIsStillATap() {
            Interpreter interpreter = onAScreenOf(1280, 800);
            answerTo(interpreter, "w: view/no-wait survey");
            int[] at = readPlace(interpreter, "on-screen w face-named w 'go");

            touch(ScreenEventType.DOWN, at[0], at[1]);
            touch(ScreenEventType.MOVE, at[0] + 5, at[1] + 2);
            touch(ScreenEventType.UP, at[0] + 5, at[1] + 2);
            deliver(interpreter);

            assertThat(answerTo(interpreter, "clicked")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("dragging from a slider drags it, as a mouse does")
        void dragsASlider() {
            Interpreter interpreter = onAScreenOf(1280, 800);
            answerTo(interpreter, "w: view/no-wait [s: slider]  s: face-named w 's  was: get-face s");
            int[] at = readPlace(interpreter, "on-screen w s");

            fingerDrags(interpreter, at, moved(at, 60, 0));

            assertThat(answerTo(interpreter, "was <> get-face s")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("dragging on a window taller than its space scrolls what it holds, and clicks nothing")
        void scrollsATallWindow() {
            Interpreter interpreter = onAScreenOf(1280, 200);
            answerTo(interpreter, "w: view/no-wait survey  held: w/1/offset/y");
            int[] at = readPlace(interpreter, "w/offset + 200x150");

            fingerDrags(interpreter, at, moved(at, 0, -80));

            assertThat(answerTo(interpreter, "reduce [w/1/offset/y < held  clicked]"))
                    .isEqualTo("[#(true) #(false)]");
        }

        @Test
        @DisplayName("a drag that began on a button and scrolled leaves the button unclicked")
        void aScrollFromAButtonDoesNotClickIt() {
            Interpreter interpreter = onAScreenOf(1280, 200);
            answerTo(interpreter, "w: view/no-wait survey");
            int[] bottom = readPlace(interpreter, "w/offset + 200x190");
            fingerDrags(interpreter, bottom, moved(bottom, 0, -180));
            int[] button = readPlace(interpreter, "on-screen w face-named w 'go");

            fingerDrags(interpreter, button, moved(button, 0, 60));

            assertThat(answerTo(interpreter, "clicked")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("dragging in a text area scrolls its text and selects nothing")
        void scrollsATextArea() {
            Interpreter interpreter = onAScreenOf(1280, 800);
            answerTo(interpreter, """
                    w: view/no-wait [notes: area 200x60]
                    notes: face-named w 'notes
                    set-face notes form array/initial 40 "a line of text^/"
                    text-face: notes/gob/1/data
                    held: text-face/gob/1/offset/y""");
            int[] at = readPlace(interpreter, "on-screen w notes");

            fingerDrags(interpreter, at, moved(at, 0, -40));

            assertThat(answerTo(interpreter, "reduce [text-face/gob/1/offset/y < held  none? text-face/state/mark-head]"))
                    .isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("where a mouse dragging in a text area selects, as R3's does")
        void aMouseSelects() {
            Interpreter interpreter = onAScreenOf(1280, 800);
            answerTo(interpreter, """
                    w: view/no-wait [notes: area 200x60]
                    notes: face-named w 'notes
                    set-face notes form array/initial 40 "a line of text^/"
                    text-face: notes/gob/1/data""");
            int[] at = readPlace(interpreter, "on-screen w notes");

            mouseDrags(interpreter, moved(at, -60, 0), moved(at, 40, 0));

            assertThat(answerTo(interpreter, "none? text-face/state/mark-head")).isEqualTo(FALSE);
        }
    }

    @Nested
    @DisplayName("the keyboard")
    class TheKeyboard {

        @Test
        @DisplayName("tapping a field takes the typing, so the keyboard rises")
        void tappingAFieldTakesTheTyping() {
            Interpreter interpreter = onAScreenOf(384, 800);
            answerTo(interpreter, "w: view/no-wait survey");

            tap(interpreter, readPlace(interpreter, "on-screen w face-named w 'name-field"));

            assertThat(viewer.timesTypingTaken).isEqualTo(1);
        }

        @Test
        @DisplayName("tapping a button takes none")
        void tappingAButtonTakesNone() {
            Interpreter interpreter = onAScreenOf(1280, 800);
            answerTo(interpreter, "w: view/no-wait survey");

            tap(interpreter, readPlace(interpreter, "on-screen w face-named w 'go"));

            assertThat(viewer.timesTypingTaken).isZero();
        }

        @Test
        @DisplayName("tapping a field and then the window behind it lowers the keyboard")
        void tappingAwayLowersIt() {
            Interpreter interpreter = onAScreenOf(384, 800);
            answerTo(interpreter, "w: view/no-wait survey");
            tap(interpreter, readPlace(interpreter, "on-screen w face-named w 'name-field"));

            tap(interpreter, readPlace(interpreter, "w/offset + 5x5"));

            assertThat(viewer.timesTypingReleased).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("the one-surface screen")
    class OneSurface {

        private Interpreter watching(Interpreter interpreter, String opening) {
            answerTo(interpreter, opening);
            answerTo(interpreter, """
                    seen: copy []
                    handle-events [
                        name: 'watching
                        priority: 90
                        handler: func [event] [
                            if find [down up move] event/type [
                                append/only seen reduce [event/type event/offset event/flags]
                            ]
                            event
                        ]
                    ]""");
            return interpreter;
        }

        private String seen(Interpreter interpreter) {
            return answerTo(interpreter, "wait [system/ports/event 0]  seen");
        }

        private DrawingCommand.Fill findTheFillOf(int red, int green, int blue) {
            return viewer.readTheLastPainted().stream()
                    .filter(DrawingCommand.Fill.class::isInstance)
                    .map(DrawingCommand.Fill.class::cast)
                    .filter(fill -> fill.colour().red() == red && fill.colour().green() == green
                            && fill.colour().blue() == blue)
                    .findFirst()
                    .orElseThrow();
        }

        private static final String TOO_WIDE =
                "w: view/no-wait/options make gob! [size: 640x360 color: 200.10.30] [offset: 20x20]";

        private static final String WITH_A_FIELD_AT_THE_BOTTOM = """
                w: view/no-wait/options make gob! [size: 384x800 color: 10.20.30] [offset: 0x0]
                append w field: make gob! [offset: 0x700 size: 384x40 color: 200.10.30]
                show w
                show-soft-keyboard/attach field""";

        @Test
        @DisplayName("a gob window wider than the screen is shown smaller, keeping its proportions, at the top left")
        void aWindowTooWideIsShownSmaller() {
            watching(onAScreenOf(384, 800), TOO_WIDE);

            DrawingCommand.Fill backdrop = findTheFillOf(200, 10, 30);

            assertThat(backdrop.where().clip().across()).isZero();
            assertThat(backdrop.where().clip().down()).isZero();
            assertThat(backdrop.where().clip().wide()).isEqualTo(384);
            assertThat(backdrop.where().clip().high()).isEqualTo(216);
        }

        @Test
        @DisplayName("and still has its own size and offset")
        void keepsItsOwnSizeAndOffset() {
            Interpreter interpreter = watching(onAScreenOf(384, 800), TOO_WIDE);

            assertThat(answerTo(interpreter, "reduce [w/size w/offset]")).isEqualTo("[640x360 20x20]");
        }

        @Test
        @DisplayName("and a press where it was shown reaches it where it is")
        void aPressIsMappedBack() {
            Interpreter interpreter = watching(onAScreenOf(384, 800), TOO_WIDE);

            touch(ScreenEventType.DOWN, 192, 108);

            assertThat(seen(interpreter)).isEqualTo("[[down 320x180 [touch]]]");
        }

        @Test
        @DisplayName("a window that fits but lies partly beyond the screen is shown on it, and heard there")
        void aWindowPartlyOffIsShownOnIt() {
            Interpreter interpreter = watching(onAScreenOf(384, 800),
                    "w: view/no-wait/options make gob! [size: 100x100 color: 200.10.30] [offset: 350x10]");

            assertThat(findTheFillOf(200, 10, 30).where().clip().across()).isEqualTo(284);

            touch(ScreenEventType.DOWN, 300, 20);

            assertThat(seen(interpreter)).isEqualTo("[[down 16x10 [touch]]]");
        }

        @Test
        @DisplayName("show-soft-keyboard takes the typing")
        void showSoftKeyboardTakesTheTyping() {
            watching(onAScreenOf(384, 800), WITH_A_FIELD_AT_THE_BOTTOM);

            assertThat(viewer.timesTypingTaken).isEqualTo(1);
        }

        @Test
        @DisplayName("a keyboard covering the attached gob moves the picture up just enough, and a press is heard where it was")
        void theKeyboardMovesThePictureUp() {
            Interpreter interpreter = watching(onAScreenOf(384, 800), WITH_A_FIELD_AT_THE_BOTTOM);

            screen.measureKeyboard(300);
            seen(interpreter);

            assertThat(findTheFillOf(200, 10, 30).where().clip().down()).isEqualTo(700 - 240);

            touch(ScreenEventType.DOWN, 10, 480);

            assertThat(seen(interpreter)).isEqualTo("[[down 10x720 [touch]]]");
        }

        @Test
        @DisplayName("the keyboard going puts the picture back")
        void theKeyboardGoingPutsItBack() {
            Interpreter interpreter = watching(onAScreenOf(384, 800), WITH_A_FIELD_AT_THE_BOTTOM);
            screen.measureKeyboard(300);
            seen(interpreter);

            screen.measureKeyboard(0);
            seen(interpreter);

            assertThat(findTheFillOf(200, 10, 30).where().clip().down()).isEqualTo(700);
        }

        @Test
        @DisplayName("a touch outside the attached gob lowers the keyboard, and still reaches the script")
        void aTouchElsewhereLowersIt() {
            Interpreter interpreter = watching(onAScreenOf(384, 800), WITH_A_FIELD_AT_THE_BOTTOM);

            touch(ScreenEventType.DOWN, 10, 20);

            assertThat(seen(interpreter)).isEqualTo("[[down 10x20 [touch]]]");
            assertThat(viewer.timesTypingReleased).isEqualTo(1);
        }

        @Test
        @DisplayName("a touch on the attached gob keeps it")
        void aTouchOnItKeepsIt() {
            Interpreter interpreter = watching(onAScreenOf(384, 800), WITH_A_FIELD_AT_THE_BOTTOM);

            touch(ScreenEventType.DOWN, 10, 710);
            seen(interpreter);

            assertThat(viewer.timesTypingReleased).isZero();
        }
    }
}
