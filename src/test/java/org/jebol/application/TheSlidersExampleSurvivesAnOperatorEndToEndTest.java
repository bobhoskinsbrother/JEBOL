package org.jebol.application;

import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventKind;
import org.jebol.domain.render.PaintInstruction;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.render.TextAlignment;
import org.jebol.domain.render.TextVerticalAlignment;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class TheSlidersExampleSurvivesAnOperatorEndToEndTest {

    private static final String NOTHING_WENT_WRONG = "fine";

    private static final String OPEN_THE_EXAMPLE = """
            change-dir %examples/r3-gui/
            do %gui-322.r
            the-layout: last load %sliders.r
            gui-view: get bind 'view first find gui first [view:]
            the-middles-of: func [style /local found walk] [
                found: copy []
                walk: func [gob at /local here] [
                    foreach sub any [gob/pane []] [
                        here: at + sub/offset
                        if all [object? sub/data style = select sub/data 'style] [
                            append found here + to pair! sub/size / 2
                        ]
                        walk sub here
                    ]
                ]
                walk the-window 0x0
                found
            ]
            the-faces-styled: func [style index /local found walk] [
                found: copy []
                walk: func [gob] [
                    foreach sub any [gob/pane []] [
                        if all [object? sub/data style = select sub/data 'style] [append found sub/data]
                        walk sub
                    ]
                ]
                walk the-window
                pick found index
            ]
            the-face-named: func [name /local found walk] [
                walk: func [gob] [
                    foreach sub any [gob/pane []] [
                        if all [object? sub/data name = select sub/data 'name] [found: sub/data]
                        walk sub
                    ]
                ]
                walk the-window
                found
            ]
            the-window: gui-view/no-wait the-layout
            """;

    private static final String TAKE_WHAT_IS_WAITING = """
            set/any 'outcome try [wait [gui-event-port 0]]
            either error? :outcome [mold reduce [outcome/type outcome/id outcome/arg1 outcome/arg2 outcome/where]] ['fine]
            """;

    private final RecordingScreen screen = RecordingScreen.measuring(1024, 768);

    private Interpreter interpreter;

    @BeforeEach
    void openTheExample() {
        Bounds everything = Bounds.standard().withWallClockLimit(Duration.ofSeconds(100));
        for (HostService service : HostService.values()) {
            everything = everything.granting(service);
        }
        interpreter = Interpreter.withBounds(everything);
        interpreter.useScreen(screen);
        interpreter.useEnvironment(new ProcessEnvironment());
        interpreter.useFileSystem(FileSystemPort.rootedAt(Path.of("src/test/resources").toAbsolutePath()));
        interpreter.defineFreshWordsIn(OPEN_THE_EXAMPLE);
        assertThat(interpreter.display(interpreter.run(OPEN_THE_EXAMPLE)))
                .as("the example opens its window")
                .startsWith("make gob!");
    }

    private GobValue theWindow() {
        return screen.whatOpened().getFirst();
    }

    private String theOperatorDoes(ScreenEventKind kind, ScreenEventDetail detail) {
        screen.theOperatorDoes(kind, theWindow(), detail);
        interpreter.defineFreshWordsIn(TAKE_WHAT_IS_WAITING);
        return interpreter.display(interpreter.run(TAKE_WHAT_IS_WAITING));
    }

    private void theOperatorClicks(int across, int down) {
        ScreenEventDetail.At at = new ScreenEventDetail.At(across, down);
        assertThat(theOperatorDoes(ScreenEventKind.MOVE, at)).as("moving to %dx%d", across, down)
                .isEqualTo(NOTHING_WENT_WRONG);
        assertThat(theOperatorDoes(ScreenEventKind.DOWN, at)).as("pressing at %dx%d", across, down)
                .isEqualTo(NOTHING_WENT_WRONG);
        assertThat(theOperatorDoes(ScreenEventKind.UP, at)).as("releasing at %dx%d", across, down)
                .isEqualTo(NOTHING_WENT_WRONG);
    }

    private String theScrollersValue() {
        return interpreter.display(interpreter.run("get-face the-face-named 'sbar"));
    }

    @Test
    @DisplayName("the window is resized and moved by the operator")
    void theWindowIsResizedAndMoved() {
        assertThat(theOperatorDoes(ScreenEventKind.RESIZE, new ScreenEventDetail.At(400, 450)))
                .isEqualTo(NOTHING_WENT_WRONG);
        assertThat(theOperatorDoes(ScreenEventKind.RESIZE, new ScreenEventDetail.At(200, 200)))
                .isEqualTo(NOTHING_WENT_WRONG);
        assertThat(theOperatorDoes(ScreenEventKind.OFFSET, new ScreenEventDetail.At(10, 10)))
                .isEqualTo(NOTHING_WENT_WRONG);
    }

    @Test
    @DisplayName("the pointer is moved over every part of the window, scrollers included")
    void thePointerHoversOverEverything() {
        for (int down = 0; down <= 399; down += 7) {
            for (int across = 0; across <= 330; across += 11) {
                assertThat(theOperatorDoes(ScreenEventKind.MOVE, new ScreenEventDetail.At(across, down)))
                        .as("hovering at %dx%d", across, down)
                        .isEqualTo(NOTHING_WENT_WRONG);
            }
        }
    }

    @ParameterizedTest(name = "clicking {0} at {1}x{2} sets the scroller to {3}")
    @CsvSource({
            "Set 0%,    60, 273, 0%",
            "Set 10%,  165, 273, 10%",
            "Set 50%,  270, 273, 50%",
            "Set 90%,   60, 306, 90%",
            "Set 100%, 165, 306, 100%",
    })
    @DisplayName("each Set button sets the scroller it is attached to")
    void eachSetButtonSetsTheScroller(String label, int across, int down, String value) {
        theOperatorClicks(across, down);

        assertThat(theScrollersValue()).as("after clicking %s", label).isEqualTo(value);
    }

    @Test
    @DisplayName("Set 150% is accepted without anything going wrong")
    void setOneHundredAndFiftyPercent() {
        theOperatorClicks(270, 306);
    }

    @ParameterizedTest(name = "the radio at {0}x{1} is chosen")
    @CsvSource({"65, 373", "161, 373", "261, 373"})
    @DisplayName("each delta radio can be chosen")
    void eachRadioCanBeChosen(int across, int down) {
        theOperatorClicks(across, down);
    }

    @Test
    @DisplayName("the slider's knob is dragged along and let go")
    void theSliderIsDragged() {
        assertThat(theOperatorDoes(ScreenEventKind.DOWN, new ScreenEventDetail.At(25, 181)))
                .isEqualTo(NOTHING_WENT_WRONG);
        for (int across = 25; across <= 300; across += 25) {
            assertThat(theOperatorDoes(ScreenEventKind.MOVE, new ScreenEventDetail.At(across, 181)))
                    .as("dragging to %d", across)
                    .isEqualTo(NOTHING_WENT_WRONG);
        }
        assertThat(theOperatorDoes(ScreenEventKind.UP, new ScreenEventDetail.At(300, 181)))
                .isEqualTo(NOTHING_WENT_WRONG);
    }

    @ParameterizedTest(name = "the scroller is clicked at {0}x{1}")
    @CsvSource({
            "25, 108", "300, 108", "160, 108", "100, 108",
            "25, 207", "300, 207", "160, 207", "100, 207",
    })
    @DisplayName("both scrollers' arrows, knobs and tracks are clicked")
    void theScrollersAreClicked(int across, int down) {
        theOperatorClicks(across, down);
    }

    private List<ScreenEventDetail.At> theMiddlesOf(String style) {
        String found = interpreter.display(interpreter.run("""
                the-middles-of '%s""".formatted(style)));
        return Arrays.stream(found.replaceAll("[\\[\\]]", "").trim().split("\\s+"))
                .filter(pair -> !pair.isEmpty())
                .map(pair -> pair.split("x"))
                .map(axes -> new ScreenEventDetail.At(
                        (int) Math.round(Double.parseDouble(axes[0])),
                        (int) Math.round(Double.parseDouble(axes[1]))))
                .toList();
    }

    private void theOperatorDragsAlongFrom(ScreenEventDetail.At start) {
        assertThat(theOperatorDoes(ScreenEventKind.MOVE, start)).as("moving onto %s", start)
                .isEqualTo(NOTHING_WENT_WRONG);
        assertThat(theOperatorDoes(ScreenEventKind.DOWN, start)).as("pressing at %s", start)
                .isEqualTo(NOTHING_WENT_WRONG);
        ScreenEventDetail.At last = start;
        for (int moved = -60; moved <= 60; moved += 20) {
            last = new ScreenEventDetail.At(start.across() + moved, start.down());
            assertThat(theOperatorDoes(ScreenEventKind.MOVE, last)).as("dragging to %s", last)
                    .isEqualTo(NOTHING_WENT_WRONG);
        }
        assertThat(theOperatorDoes(ScreenEventKind.UP, last)).as("letting go at %s", last)
                .isEqualTo(NOTHING_WENT_WRONG);
    }

    @ParameterizedTest(name = "every {0} is found where the layout put it, pressed in its middle and dragged")
    @CsvSource({"scroller, 2", "slider, 1"})
    @DisplayName("each scroller and slider is dragged from its middle, found where the layout put it rather than at a guessed place")
    void eachSlidingFaceIsDraggedFromItsMiddle(String style, int howMany) {
        List<ScreenEventDetail.At> middles = theMiddlesOf(style);
        assertThat(middles).as("the %ss the layout made", style).hasSize(howMany);

        middles.forEach(this::theOperatorDragsAlongFrom);
    }

    private String theValueOf(String style, int index) {
        return interpreter.display(interpreter.run("""
                get-face the-faces-styled '%s %d""".formatted(style, index)));
    }

    private void theOperatorDrags(ScreenEventDetail.At from, ScreenEventDetail.At to) {
        assertThat(theOperatorDoes(ScreenEventKind.MOVE, from)).isEqualTo(NOTHING_WENT_WRONG);
        assertThat(theOperatorDoes(ScreenEventKind.DOWN, from)).isEqualTo(NOTHING_WENT_WRONG);
        assertThat(theOperatorDoes(ScreenEventKind.MOVE, to)).isEqualTo(NOTHING_WENT_WRONG);
        assertThat(theOperatorDoes(ScreenEventKind.UP, to)).isEqualTo(NOTHING_WENT_WRONG);
    }

    @Test
    @DisplayName("dragging the slider's knob to the right raises its value")
    void draggingTheSliderRaisesIt() {
        ScreenEventDetail.At knob = theMiddlesOf("slider").getFirst();
        String before = theValueOf("slider", 1);

        theOperatorDrags(knob, new ScreenEventDetail.At(knob.across() + 80, knob.down()));

        assertThat(theValueOf("slider", 1)).as("the slider's value, which was %s", before)
                .isNotEqualTo(before);
    }

    @Test
    @DisplayName("and the window is repainted, so the operator sees the knob move")
    void draggingTheSliderRepaintsTheWindow() {
        ScreenEventDetail.At knob = theMiddlesOf("slider").getFirst();
        int repaintsBefore = screen.whatWasRefreshed().size();

        theOperatorDrags(knob, new ScreenEventDetail.At(knob.across() + 80, knob.down()));

        assertThat(screen.whatWasRefreshed().size()).isGreaterThan(repaintsBefore);
        assertThat(screen.whatWasRefreshed().getLast().sharesStorageWith(theWindow())).isTrue();
    }

    @Test
    @DisplayName("dragging the scroller repaints the window too")
    void draggingTheScrollerRepaintsTheWindow() {
        ScreenEventDetail.At knob = theMiddlesOf("scroller").getFirst();
        int repaintsBefore = screen.whatWasRefreshed().size();

        theOperatorDrags(knob, new ScreenEventDetail.At(knob.across() + 60, knob.down()));

        assertThat(screen.whatWasRefreshed().size()).isGreaterThan(repaintsBefore);
    }

    @Test
    @DisplayName("and the scroller it is attached to follows it")
    void theAttachedScrollerFollowsTheSlider() {
        ScreenEventDetail.At knob = theMiddlesOf("slider").getFirst();

        theOperatorDrags(knob, new ScreenEventDetail.At(knob.across() + 80, knob.down()));

        assertThat(theScrollersValue()).isEqualTo(theValueOf("slider", 1));
    }

    @Test
    @DisplayName("dragging the first scroller's knob moves it, and the progress bar attached to it follows")
    void draggingTheScrollerMovesItAndTheProgressBar() {
        ScreenEventDetail.At knob = theMiddlesOf("scroller").getFirst();
        String before = theScrollersValue();

        theOperatorDrags(knob, new ScreenEventDetail.At(knob.across() + 60, knob.down()));

        assertThat(theScrollersValue()).as("the scroller's value, which was %s", before)
                .isNotEqualTo(before);
        assertThat(theValueOf("progress", 1)).isEqualTo(theScrollersValue());
    }

    private List<PaintInstruction.Writing> theLinesWrittenStartingWith(String opening) {
        ObjectValue dialect = (ObjectValue) interpreter.run("system/dialects/draw").value();
        return PaintList.ofTheScreen(screen.rootGob(), 1024, 768, dialect).instructions().stream()
                .filter(PaintInstruction.Writing.class::isInstance)
                .map(PaintInstruction.Writing.class::cast)
                .filter(line -> line.text().startsWith(opening))
                .toList();
    }

    @Test
    @DisplayName("each Set button's label is centred both ways and casts the 2x2 shadow")
    void eachButtonsLabelIsCentred() {
        List<PaintInstruction.Writing> labels = theLinesWrittenStartingWith("Set ");

        assertThat(labels).hasSize(6);
        assertThat(labels).allSatisfy(label -> {
            assertThat(label.layout().align()).isEqualTo(TextAlignment.CENTRE);
            assertThat(label.layout().valign()).isEqualTo(TextVerticalAlignment.MIDDLE);
            assertThat(label.layout().shadowAcross()).isEqualTo(2);
            assertThat(label.layout().shadowDown()).isEqualTo(2);
            assertThat(label.runs().getFirst().bold()).isTrue();
        });
    }

    @Test
    @DisplayName("the progress bar's track is dark, its 2010 transparency darkened with it and read as 3.22's opacity")
    void theProgressTrackIsDark() {
        assertThat(interpreter.display(interpreter.run("""
                track: get-facet the-faces-styled 'progress 1 'area-fill
                collect [foreach colour track [keep colour/4]]""")))
                .isEqualTo("[217 191 127]");
        assertThat(interpreter.display(interpreter.run("""
                first get-facet the-faces-styled 'progress 1 'area-fill""")))
                .isEqualTo("24.24.24.217");
    }

    @Test
    @DisplayName("and a colour of three numbers that the GUI scales stays solid")
    void aSolidColourStaysSolid() {
        assertThat(interpreter.display(interpreter.run("""
                bar: get-facet the-faces-styled 'progress 1 'bar-fill
                collect [foreach colour bar [keep length? colour]]""")))
                .isEqualTo("[3 3 3]");
    }

    @Test
    @DisplayName("each radio's label starts 18 across, clear of its circle")
    void eachRadiosLabelClearsItsCircle() {
        List<PaintInstruction.Writing> labels = theLinesWrittenStartingWith("Delta ");

        assertThat(labels).hasSize(3);
        assertThat(labels).allSatisfy(label ->
                assertThat(label.layout().originAcross()).isEqualTo(18));
    }

    @ParameterizedTest(name = "the pointer over a {0} is mapped to that {0}")
    @CsvSource({"scroller", "slider"})
    @DisplayName("the pointer over a sliding face is mapped to that face, so it is told the pointer is over it")
    void thePointerOverASlidingFaceIsMappedToIt(String style) {
        for (ScreenEventDetail.At middle : theMiddlesOf(style)) {
            String mapped = interpreter.display(interpreter.run("""
                    mapped: map-event make event! [type: 'move window: the-window offset: %dx%d]
                    select mapped/gob/data 'style""".formatted(middle.across(), middle.down())));

            assertThat(mapped).as("the face under %s", middle).isEqualTo(style);
        }
    }

    @Test
    @DisplayName("moving the pointer onto a scroller tells that scroller the pointer is over it")
    void movingOntoAScrollerTellsIt() {
        ScreenEventDetail.At middle = theMiddlesOf("scroller").getFirst();
        assertThat(theOperatorDoes(ScreenEventKind.MOVE, new ScreenEventDetail.At(1, 1)))
                .isEqualTo(NOTHING_WENT_WRONG);

        assertThat(theOperatorDoes(ScreenEventKind.MOVE, middle)).as("moving onto %s", middle)
                .isEqualTo(NOTHING_WENT_WRONG);

        assertThat(interpreter.display(interpreter.run("""
                mapped: map-event make event! [type: 'move window: the-window offset: %dx%d]
                mapped/gob/data/state/over""".formatted(middle.across(), middle.down()))))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the operator types and presses the keys that type nothing")
    void theOperatorUsesTheKeyboard() {
        for (char typed : "a1 ".toCharArray()) {
            assertThat(theOperatorDoes(ScreenEventKind.KEY, new ScreenEventDetail.Typed(typed)))
                    .isEqualTo(NOTHING_WENT_WRONG);
            assertThat(theOperatorDoes(ScreenEventKind.KEY_UP, new ScreenEventDetail.Typed(typed)))
                    .isEqualTo(NOTHING_WENT_WRONG);
        }
        for (String named : new String[] {"up", "down", "left", "right", "escape", "f1"}) {
            assertThat(theOperatorDoes(ScreenEventKind.CONTROL, new ScreenEventDetail.NamedKey(named)))
                    .isEqualTo(NOTHING_WENT_WRONG);
            assertThat(theOperatorDoes(ScreenEventKind.CONTROL_UP, new ScreenEventDetail.NamedKey(named)))
                    .isEqualTo(NOTHING_WENT_WRONG);
        }
    }

    @Test
    @DisplayName("closing the window ends the run with no window left")
    void closingEndsTheRun() {
        assertThat(theOperatorDoes(ScreenEventKind.CLOSE, new ScreenEventDetail.NothingMore()))
                .isEqualTo(NOTHING_WENT_WRONG);
        assertThat(interpreter.display(interpreter.run("tail? system/view/screen-gob")))
                .isEqualTo("#(true)");
    }
}
