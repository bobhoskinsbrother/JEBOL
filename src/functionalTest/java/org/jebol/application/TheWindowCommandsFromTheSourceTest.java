package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.host.ScreenCursor;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;
import org.jebol.domain.host.SystemCursor;
import org.jebol.domain.value.GobValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
class TheWindowCommandsFromTheSourceTest {

    private static final String TRUE = "#(true)";

    private static final String FACE_FINDER = """
            face-named: func [window name /local found walk] [
                walk: func [gob] [
                    foreach sub any [gob/pane []] [
                        if all [object? sub/data name = select sub/data 'name] [found: sub/data]
                        walk sub
                    ]
                ]
                walk window
                found
            ]""";

    private final RecordingScreen screen = RecordingScreen.measure(1024, 768);

    private Interpreter grantedInterpreterOn(RecordingScreen given) {
        return Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(given),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.WINDOWS).withTimeLimit(Duration.ofSeconds(50)));
    }

    private Interpreter ungrantedInterpreterOn(RecordingScreen given) {
        return Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(given),
                OutputPort.discard(),
                Bounds.standard());
    }

    private String answerFrom(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String answerTo(String source) {
        return answerFrom(grantedInterpreterOn(screen), source);
    }

    private void assertSpecIs(String word, String written) {
        Interpreter interpreter = grantedInterpreterOn(screen);
        assertThat(answerFrom(interpreter, "mold/flat spec-of :" + word))
                .isEqualTo(answerFrom(interpreter, "mold/flat [" + written + "]"));
    }

    @Nested
    @DisplayName("each declares what boot/window.reb declares")
    class TheSpecs {

        @Test
        @DisplayName("cursor")
        void declaresCursor() {
            assertSpecIs("cursor", "{Changes the mouse cursor image.} image [integer! image! none!]");
        }

        @Test
        @DisplayName("show")
        void declaresShow() {
            assertSpecIs("show", "{Display or update a graphical object or block of them.} gob [gob! none! block!]");
        }

        @Test
        @DisplayName("gui-metric, with both of its refinements")
        void declaresGuiMetric() {
            assertSpecIs("gui-metric", """
                    {Returns specific gui related metric setting.}
                    keyword [word!] {Available keywords: SCREENS, BORDER-FIXED, BORDER-SIZE, SCREEN-DPI, LOG-SIZE, \
                    PHYS-SIZE, SCREEN-SIZE, VIRTUAL-SCREEN-SIZE, TITLE-SIZE, WINDOW-MIN-SIZE, WORK-ORIGIN and WORK-SIZE.}
                    /set val {Value used to set specific setting(works only on 'writable' keywords).}
                    /display idx [integer!] {Display index, starting with 0}""");
        }

        @Test
        @DisplayName("show-soft-keyboard")
        void declaresShowSoftKeyboard() {
            assertSpecIs("show-soft-keyboard", """
                    {Display on-screen keyboard for user input.}
                    /attach gob [gob!] {GOB which should be visible during the input operation}""");
        }

        @Test
        @DisplayName("and a native no file declares still names the refinement that owns an argument")
        void namesTheRefinementOwningAnArgument() {
            assertSpecIs("request-color", "/default color [tuple!]");
        }
    }

    @Nested
    @DisplayName("system/modules")
    class TheModuleTable {

        @Test
        @DisplayName("holds the word window, and its value is none, as a build with no window extension has")
        void holdsWindowAsNone() {
            assertThat(answerTo("to logic! find words-of system/modules 'window")).isEqualTo(TRUE);
            assertThat(answerTo("none? select system/modules 'window")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and importing it is still refused")
        void refusesImportingWindow() {
            assertThat(answerTo("error? try [import 'window]")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("a metric neither host serves")
    class TheUnservedMetrics {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"restore", "minimize", "maximize", "activate", "virtual-screen-size", "nosuch"})
        @DisplayName("raises command-fail, the RXR_BAD_ARGS of both hosts' default branch, with no sentence in it")
        void raisesCommandFail(String word) {
            Interpreter interpreter = grantedInterpreterOn(screen);
            answerFrom(interpreter, "failed: try [gui-metric '" + word + "]");
            assertThat(answerFrom(interpreter, "failed/id")).isEqualTo("command-fail");
            assertThat(answerFrom(interpreter, "none? failed/arg1")).isEqualTo(TRUE);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"restore", "minimize", "maximize", "activate"})
        @DisplayName("under /set the four arrange a window instead, so a value that is not one is invalid-arg (decision 72)")
        void refusesAValueThatIsNoWindowUnderSet(String word) {
            Interpreter interpreter = grantedInterpreterOn(screen);
            answerFrom(interpreter, "failed: try [gui-metric/set '" + word + " true]");
            assertThat(answerFrom(interpreter, "failed/id")).isEqualTo("invalid-arg");
        }
    }

    @Nested
    @DisplayName("cursor")
    class TheCursor {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "32512", "32513", "32514", "32515", "32516", "32642", "32643", "32644", "32645", "32646", "32648",
                "32649", "32650", "32651", "0", "-1", "99", "none", "make image! 2x2", "make image! 0x0"})
        @DisplayName("takes an id, an image or none, and answers unset as the Windows host's RXR_UNSET does")
        void answersUnset(String written) {
            assertThat(answerTo("unset? cursor " + written)).isEqualTo(TRUE);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"{hand}", "1.5", "1x1", "'hand", "[32512]"})
        @DisplayName("refuses anything else before a host sees it")
        void refusesTheWrongType(String written) {
            Interpreter interpreter = grantedInterpreterOn(screen);
            answerFrom(interpreter, "failed: try [cursor " + written + "]");
            assertThat(answerFrom(interpreter, "failed/id")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("tells the screen which cursor: a known id is that cursor, and an unknown one or none hides it")
        void tellsTheScreenWhichCursor() {
            answerFrom(grantedInterpreterOn(screen), """
                    cursor 32649
                    cursor 99
                    cursor 0
                    cursor -1
                    cursor none""");
            assertThat(screen.listCursorsAskedFor()).containsExactly(
                    new ScreenCursor.FromSystem(SystemCursor.HAND),
                    new ScreenCursor.Hidden(), new ScreenCursor.Hidden(),
                    new ScreenCursor.Hidden(), new ScreenCursor.Hidden());
        }

        @Test
        @DisplayName("and an image is a picture of its own size and pixels, where an empty one hides the pointer")
        void tellsTheScreenWhichPicture() {
            answerFrom(grantedInterpreterOn(screen), """
                    cursor make image! [1x1 #{0A141E}]
                    cursor make image! 0x0""");
            assertThat(screen.listCursorsAskedFor()).hasSize(2);
            assertThat(screen.listCursorsAskedFor().getFirst())
                    .isInstanceOfSatisfying(ScreenCursor.Picture.class, picture -> {
                        assertThat(picture.pixels().wide()).isEqualTo(1);
                        assertThat(picture.pixels().high()).isEqualTo(1);
                        assertThat(picture.pixels().rgba()).startsWith((byte) 10, (byte) 20, (byte) 30);
                    });
            assertThat(screen.listCursorsAskedFor().getLast()).isEqualTo(new ScreenCursor.Hidden());
        }

        @Test
        @DisplayName("does nothing and answers unset with no display, as the posix host's stub does")
        void answersUnsetWithNoDisplay() {
            assertThat(answerFrom(grantedInterpreterOn(RecordingScreen.absent()), "unset? cursor 32512"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("is refused without the grant, as every window command is")
        void refusesWithoutTheGrant() {
            Interpreter interpreter = ungrantedInterpreterOn(screen);
            answerFrom(interpreter, "failed: try [cursor 32512]");
            assertThat(answerFrom(interpreter, "failed/id")).isEqualTo("no-service");
        }
    }

    @Nested
    @DisplayName("show-soft-keyboard")
    class TheSoftKeyboard {

        @Test
        @DisplayName("does nothing and answers unset, because neither host has a branch for it")
        void answersUnset() {
            assertThat(answerTo("unset? show-soft-keyboard")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and takes a gob under /attach")
        void takesAGobUnderAttach() {
            assertThat(answerTo("unset? show-soft-keyboard/attach make gob! []")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and nothing but a gob")
        void refusesAnythingElseUnderAttach() {
            Interpreter interpreter = grantedInterpreterOn(screen);
            answerFrom(interpreter, "failed: try [show-soft-keyboard/attach 1]");
            assertThat(answerFrom(interpreter, "failed/id")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("and is refused without the grant")
        void refusesWithoutTheGrant() {
            Interpreter interpreter = ungrantedInterpreterOn(screen);
            answerFrom(interpreter, "failed: try [show-soft-keyboard]");
            assertThat(answerFrom(interpreter, "failed/id")).isEqualTo("no-service");
        }
    }

    @Nested
    @DisplayName("a drag in the 2010 GUI, which calls cursor from its own event handler")
    class ADragInTheGui {

        private Interpreter interpreterHoldingADrag() {
            Interpreter interpreter = grantedInterpreterOn(screen);
            answerFrom(interpreter, FACE_FINDER);
            answerFrom(interpreter, """
                    window: view/no-wait [pressing: button {Press}]
                    pressing: face-named window 'pressing
                    middle: (pressing/gob/offset + (pressing/gob/size / 2)) + pressing/gob/parent/offset
                    dragged: make gob! [size: 10x10]
                    append window dragged
                    gui-events/drag: make guie/drag [face: pressing gob: dragged start: 0x0 active: true]""");
            return interpreter;
        }

        private ScreenEventDetail.At middleOfTheButton(Interpreter interpreter) {
            String[] axes = answerFrom(interpreter, "middle").split("x");
            return new ScreenEventDetail.At(
                    (int) Math.round(Double.parseDouble(axes[0])),
                    (int) Math.round(Double.parseDouble(axes[1])));
        }

        private String operatorActs(Interpreter interpreter, ScreenEventType kind) {
            GobValue window = screen.listWhatOpened().getFirst();
            screen.actAsTheOperator(kind, window, middleOfTheButton(interpreter));
            return answerFrom(interpreter, """
                    set/any 'outcome try [wait [gui-event-port 0]]
                    either error? :outcome [mold reduce [outcome/type outcome/id outcome/arg1]] ['fine]""");
        }

        @Test
        @DisplayName("letting go of it puts the arrow back without an error")
        void endsWithoutAnError() {
            Interpreter interpreter = interpreterHoldingADrag();
            assertThat(operatorActs(interpreter, ScreenEventType.UP)).isEqualTo("fine");
            assertThat(answerFrom(interpreter, "none? gui-events/drag")).isEqualTo(TRUE);
            assertThat(screen.listCursorsAskedFor()).last()
                    .isEqualTo(new ScreenCursor.FromSystem(SystemCursor.ARROW));
        }

        @Test
        @DisplayName("and every cursor the GUI names is one the system has")
        void namesOnlyCursorsTheSystemHas() {
            Interpreter interpreter = interpreterHoldingADrag();
            assertThat(answerFrom(interpreter, """
                    unknown: copy []
                    foreach id values-of system-cursors [unless integer? id [append unknown id]]
                    foreach id values-of system-cursors [cursor id]
                    length? unknown""")).isEqualTo("0");
            assertThat(screen.listCursorsAskedFor()).hasSize(15)
                    .allMatch(ScreenCursor.FromSystem.class::isInstance);
        }
    }
}
