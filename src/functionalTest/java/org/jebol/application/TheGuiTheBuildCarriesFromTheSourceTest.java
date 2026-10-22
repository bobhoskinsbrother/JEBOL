package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;
import org.jebol.domain.value.GobValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
class TheGuiTheBuildCarriesFromTheSourceTest {

    private static final String TRUE = "#(true)";
    private static final String FALSE = "#(false)";

    private final RecordingScreen screen = RecordingScreen.measure(1024, 768);

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

    private Interpreter interpreterOn(RecordingScreen given) {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(given),
                OutputPort.discard(),
                Bounds.standard()
                        .grant(HostService.WINDOWS)
                        .withTimeLimit(Duration.ofSeconds(50)));
        answerTo(interpreter, FACE_FINDER);
        return interpreter;
    }

    private Interpreter interpreter() {
        return interpreterOn(screen);
    }

    private String answerTo(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String guiIsLoaded(Interpreter interpreter) {
        return answerTo(interpreter, "value? 'guie");
    }

    private String waitForEvents(Interpreter interpreter) {
        return answerTo(interpreter, """
                set/any 'outcome try [wait [gui-event-port 0]]
                either error? :outcome [mold reduce [outcome/type outcome/id outcome/arg1]] ['fine]""");
    }

    @Nested
    @DisplayName("loading it")
    class Loading {

        @Test
        @DisplayName("a fresh interpreter has not loaded it, and has LOAD-GUI to load it")
        void isNotLoadedAtBoot() {
            Interpreter interpreter = interpreter();

            assertThat(guiIsLoaded(interpreter)).isEqualTo(FALSE);
            assertThat(answerTo(interpreter, "value? 'make-panel")).isEqualTo(FALSE);
            assertThat(answerTo(interpreter, "function? :load-gui")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("LOAD-GUI loads it and answers the GUI's own VIEW")
        void loadGuiAnswersTheGuisView() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, """
                    gui-view: load-gui
                    reduce [function? :gui-view value? 'guie first spec-of :gui-view]"""))
                    .isEqualTo("""
                            [#(true) #(true) {Displays a window view from a layout block, face (panel), or low level graphics object (gob).}]""");
        }

        @Test
        @DisplayName("a second LOAD-GUI answers the same VIEW and loads nothing again")
        void loadsOnce() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, """
                    first-view: load-gui
                    first-environment: guie
                    second-view: load-gui
                    reduce [same? :first-view :second-view same? first-environment guie]"""))
                    .isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("once loaded, the script's VIEW, UNVIEW and DO-EVENTS are the GUI's, and the library keeps Rebol's")
        void theScriptsWordsAreTheGuis() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, """
                    gui-view: load-gui
                    rebols-view: get in system/contexts/lib 'view
                    reduce [
                        same? :view :gui-view
                        same? :unview get in system/contexts/lib 'unview
                        same? :do-events get in system/contexts/lib 'do-events
                        first spec-of :rebols-view
                    ]"""))
                    .isEqualTo("""
                            [#(true) #(false) #(false) "Displays a window view."]""");
        }

        @Test
        @DisplayName("needs no service beyond the windows, because it reads the build")
        void needsNoOtherService() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, "function? load-gui")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("VIEW of a layout block")
    class ViewOfALayout {

        @Test
        @DisplayName("loads the GUI and answers the window it made, which is on the screen")
        void loadsTheGuiAndOpensAWindow() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, """
                    window: view/no-wait [title "Hello World" button "Close" close]
                    reduce [gob? window object? window/data window/text]"""))
                    .isEqualTo("""
                            [#(true) #(true) "Hello World"]""");
            assertThat(guiIsLoaded(interpreter)).isEqualTo(TRUE);
            assertThat(screen.listWhatOpened()).hasSize(1);
        }

        @Test
        @DisplayName("passes /options on to the GUI, so a title asked for is the window's")
        void passesItsOptionsOn() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, """
                    window: view/no-wait/options [button "Go"] [title: "Asked for"]
                    window/text == {Asked for}"""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("drops /as-is, which means nothing to a block, and still opens the GUI's window")
        void dropsAsIs() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, """
                    window: view/no-wait/as-is [button "Go"]
                    reduce [gob? window object? window/data]"""))
                    .isEqualTo("[#(true) #(true)]");
            assertThat(screen.listWhatOpened()).hasSize(1);
        }

        @Test
        @DisplayName("without /no-wait waits until the operator closes the window, then carries on")
        void waitsWithoutNoWait() {
            RecordingScreen closing = RecordingScreen.measure(1024, 768).closeWhateverOpens();
            Interpreter interpreter = interpreterOn(closing);

            assertThat(answerTo(interpreter, """
                    view [button "Close" close]
                    finished: true""")).isEqualTo(TRUE);
            assertThat(closing.listWhatOpened()).hasSize(1);
        }

        @Test
        @DisplayName("of an empty block still opens a window")
        void ofAnEmptyBlockOpensAWindow() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, "gob? view/no-wait []")).isEqualTo(TRUE);
            assertThat(screen.listWhatOpened()).hasSize(1);
        }

        @Test
        @DisplayName("a second layout opens a second window through the same GUI")
        void aSecondLayoutUsesTheSameGui() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, """
                    view/no-wait [button "One"]
                    environment: guie
                    view/no-wait [button "Two"]
                    reduce [same? environment guie length? system/view/screen-gob]"""))
                    .isEqualTo("[#(true) 2]");
            assertThat(screen.listWhatOpened()).hasSize(2);
        }

        @Test
        @DisplayName("that names a style the GUI has not got prints the GUI's error, naming where it stopped, halts the run and opens nothing")
        void thatTheGuiCannotReadHalts() {
            StringBuilder printed = new StringBuilder();
            Interpreter interpreter = Interpreter.createOn(
                    AnInterpreter.standardPorts().withScreen(screen),
                    printed::append,
                    Bounds.standard()
                            .grant(HostService.WINDOWS)
                            .withTimeLimit(Duration.ofSeconds(50)));
            String source = """
                    try [view/no-wait [title "Broken" no-such-style "Press"]]
                    print {carried on}""";
            interpreter.defineNewWordsIn(source);

            ScriptOutcome outcome = interpreter.run(source);

            assertThat(outcome.succeeded()).isFalse();
            assertThat(printed.toString())
                    .contains("** GUI ERROR: Cannot parse the GUI dialect at:")
                    .contains("no-such-style")
                    .doesNotContain("carried on");
            assertThat(screen.listWhatOpened()).isEmpty();
        }

        @Test
        @DisplayName("names a face for the layout's own actions, which can reach another face by its name")
        void anActionReachesAFaceByItsName() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, """
                    window: view/no-wait [
                        entry: field "typed"
                        going: button "Go" do [set-face entry "done"]
                    ]
                    do-face get-panel-var window 'going
                    (get-face get-panel-var window 'entry) == {done}"""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("keeps a face's name out of the script's words, and GET-PANEL-VAR finds it from the window")
        void aNameIsThePanelsNotTheScripts() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, """
                    window: view/no-wait [entry: field "typed"]
                    reduce [value? 'entry object? get-panel-var window 'entry (get-face get-panel-var window 'entry) == {typed}]"""))
                    .isEqualTo("[#(false) #(true) #(true)]");
        }

        @Test
        @DisplayName("with no screen is refused as no-service, as VIEW of a gob is")
        void withNoScreenIsRefused() {
            Interpreter interpreter = interpreterOn(RecordingScreen.absent());

            assertThat(answerTo(interpreter, """
                    failure: try [view/no-wait [button "Nowhere"]]
                    reduce [failure/type failure/id]"""))
                    .isEqualTo("[Access no-service]");
        }

        @Test
        @DisplayName("keeps its event port in a word of its own, and leaves system/view as Rebol declares it")
        void keepsItsEventPortInItsOwnWord() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, """
                    view/no-wait [button "Go"]
                    reduce [port? gui-event-port block? gui-event-port/extra/handlers words-of system/view]"""))
                    .isEqualTo("[#(true) #(true) [screen-gob handler metrics]]");
        }

        @Test
        @DisplayName("and the window answers the operator: a click on its button runs the button's action")
        void answersTheOperator() {
            Interpreter interpreter = interpreter();
            answerTo(interpreter, """
                    clicked: false
                    window: view/no-wait [pressing: button "Press" do [clicked: true]]
                    pressing: face-named window 'pressing
                    middle: (pressing/gob/offset + (pressing/gob/size / 2)) + pressing/gob/parent/offset""");
            String middle = answerTo(interpreter, "middle");
            String[] axes = middle.split("x");
            ScreenEventDetail.At at = new ScreenEventDetail.At(
                    (int) Math.round(Double.parseDouble(axes[0])),
                    (int) Math.round(Double.parseDouble(axes[1])));
            GobValue window = screen.listWhatOpened().getFirst();

            for (ScreenEventType kind : new ScreenEventType[] {
                    ScreenEventType.MOVE, ScreenEventType.DOWN, ScreenEventType.UP}) {
                screen.actAsTheOperator(kind, window, at);
                assertThat(waitForEvents(interpreter)).as("after %s", kind).isEqualTo("fine");
            }

            assertThat(answerTo(interpreter, "clicked")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("VIEW of anything else stays Rebol's")
    class ViewOfAnythingElse {

        @Test
        @DisplayName("a gob opens a window and loads no GUI")
        void aGob() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, "gob? view/no-wait make gob! [size: 40x40]")).isEqualTo(TRUE);
            assertThat(guiIsLoaded(interpreter)).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("an image opens a window and loads no GUI")
        void anImage() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, "gob? view/no-wait make image! 20x20")).isEqualTo(TRUE);
            assertThat(guiIsLoaded(interpreter)).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("a face object opens a window and loads no GUI")
        void aFaceObject() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, """
                    gob? view/no-wait object [size: 30x30 gob: make gob! [size: 30x30]]"""))
                    .isEqualTo(TRUE);
            assertThat(guiIsLoaded(interpreter)).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("a number is refused by VIEW's own declaration")
        void aNumberIsRefused() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, """
                    failure: try [view 42]
                    reduce [failure/type failure/id failure/arg1]"""))
                    .isEqualTo("[Script expect-arg view]");
            assertThat(guiIsLoaded(interpreter)).isEqualTo(FALSE);
        }
    }

    @Nested
    @DisplayName("the carried copy runs on 3.22")
    class TheEditsFor322 {

        @Test
        @DisplayName("the object spec that ended in a set-word has that field, set to none")
        void theTrailingSetWordIsNone() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, """
                    load-gui
                    reduce [did find words-of guie/face 'options none? guie/face/options]"""))
                    .isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("GET-FACET given set-words sets the words, which SET refuses to do with a set-word")
        void getFacetSetsTheWordsOfSetWords() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, """
                    load-gui
                    face: make-face 'progress []
                    get-facet face [bar-color:]
                    bar-color"""))
                    .isEqualTo("0.128.128");
        }

        @Test
        @DisplayName("a colour the source wrote with four numbers keeps the opacity 2010 meant by it")
        void aFourNumberColourIsInverted() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, """
                    load-gui
                    reduce [
                        select guie/styles/bar/facets 'area-color
                        select guie/styles/progress/facets 'area-color
                    ]"""))
                    .isEqualTo("[0.0.0.55 80.80.80.127]");
        }

        @Test
        @DisplayName("SPAN-COLORS scales a colour's transparency, as 2010 did")
        void spanColorsScalesTransparency() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, """
                    load-gui
                    reduce [span-colors 0.0.0.55 [1 2] span-colors 80.80.80.128 [0.5] span-colors 10.20.30 [2]]"""))
                    .isEqualTo("[[0.0.0.55 0.0.0.0] [40.40.40.192] [20.40.60]]");
        }

        @Test
        @DisplayName("every gob the GUI queues to show is shown, though 3.22's SHOW of a block shows nothing")
        void theShowQueueIsShownAGobAtATime() {
            Interpreter interpreter = interpreter();
            answerTo(interpreter, """
                    window: view/no-wait [one: button {One} two: button {Two}]
                    one: face-named window 'one
                    two: face-named window 'two""");
            int shownBefore = screen.listWhatWasShown().size();

            assertThat(answerTo(interpreter, """
                    show-later one
                    show-later two
                    show-now
                    empty? guie/shows""")).isEqualTo(TRUE);

            assertThat(screen.listWhatWasShown().size() - shownBefore).isEqualTo(2);
        }

        @Test
        @DisplayName("a key the host reports as control reaches the focused field, so end and then left move the caret")
        void aControlKeyReachesTheFocusedField() {
            Interpreter interpreter = interpreter();
            assertThat(answerTo(interpreter, """
                    window: view/no-wait [entry: field "abc"]
                    entry: face-named window 'entry
                    focus entry
                    same? entry guie/focal-face""")).isEqualTo(TRUE);
            GobValue window = screen.listWhatOpened().getFirst();

            for (String named : new String[] {"end", "left"}) {
                screen.actAsTheOperator(ScreenEventType.CONTROL, window, new ScreenEventDetail.NamedKey(named));
                assertThat(waitForEvents(interpreter)).as("after %s", named).isEqualTo("fine");
            }
            screen.actAsTheOperator(ScreenEventType.KEY, window, new ScreenEventDetail.Typed('x'));
            assertThat(waitForEvents(interpreter)).isEqualTo("fine");

            assertThat(answerTo(interpreter, "(get-face entry) == {abxc}"))
                    .as("the field holds %s", answerTo(interpreter, "mold get-face entry"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("RESET with no name resets every face in its panel, as the guide's survey expects")
        void resetWithNoNameResetsThePanel() {
            Interpreter interpreter = interpreter();

            assertThat(answerTo(interpreter, """
                    window: view/no-wait [
                        entry: field "typed"
                        tick: check "Ticked" on
                        clearing: button "Reset" reset
                    ]
                    entry: face-named window 'entry
                    tick: face-named window 'tick
                    clearing: face-named window 'clearing
                    set-face tick false
                    do-face clearing
                    reduce [empty? get-face entry get-face tick]"""))
                    .isEqualTo("[#(true) #(true)]");
        }
    }
}
