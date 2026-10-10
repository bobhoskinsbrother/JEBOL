package org.jebol.application;

import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventKind;
import org.jebol.domain.render.PaintInstruction;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class TheSurveyExampleTakesAnOperatorsAnswersEndToEndTest {

    private static final String NOTHING_WENT_WRONG = "fine";

    private static final String OPEN_THE_EXAMPLE = """
            change-dir %examples/r3-gui/
            do %gui-322.r
            the-layout: last load %survey.r
            gui-view: get bind 'view first find gui first [view:]
            the-faces-styled: func [style /local found walk] [
                found: copy []
                walk: func [gob at /local here] [
                    foreach sub any [gob/pane []] [
                        here: at + sub/offset
                        if all [object? sub/data style = select sub/data 'style] [
                            append found reduce [sub/data here + to pair! sub/size / 2]
                        ]
                        walk sub here
                    ]
                ]
                walk the-window 0x0
                found
            ]
            the-face: func [style index] [pick extract the-faces-styled style 2 index]
            the-middle-of: func [style index] [pick extract next the-faces-styled style 2 index]
            chosen?: func [style index] [true? get-face the-face style index]
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

    private String answerTo(String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private GobValue theWindow() {
        return screen.whatOpened().getFirst();
    }

    private String theOperatorDoes(ScreenEventKind kind, ScreenEventDetail detail) {
        screen.theOperatorDoes(kind, theWindow(), detail);
        return answerTo(TAKE_WHAT_IS_WAITING);
    }

    private ScreenEventDetail.At theMiddleOf(String style, int index) {
        int[] axes = Arrays.stream(answerTo("the-middle-of '%s %d".formatted(style, index)).split("x"))
                .mapToInt(axis -> (int) Math.round(Double.parseDouble(axis)))
                .toArray();
        return new ScreenEventDetail.At(axes[0], axes[1]);
    }

    private void theOperatorClicks(String style, int index) {
        ScreenEventDetail.At middle = theMiddleOf(style, index);
        assertThat(theOperatorDoes(ScreenEventKind.MOVE, middle)).as("moving onto %s %d", style, index)
                .isEqualTo(NOTHING_WENT_WRONG);
        assertThat(theOperatorDoes(ScreenEventKind.DOWN, middle)).as("pressing %s %d", style, index)
                .isEqualTo(NOTHING_WENT_WRONG);
        assertThat(theOperatorDoes(ScreenEventKind.UP, middle)).as("releasing %s %d", style, index)
                .isEqualTo(NOTHING_WENT_WRONG);
    }

    private void theOperatorTypes(String typed) {
        for (int character : typed.codePoints().toArray()) {
            assertThat(theOperatorDoes(ScreenEventKind.KEY, new ScreenEventDetail.Typed(character)))
                    .as("typing %s", Character.toString(character))
                    .isEqualTo(NOTHING_WENT_WRONG);
        }
    }

    @Test
    @DisplayName("the pointer moves over every part of the form, staying on one face as it goes, without the comparison of a face with itself running away")
    void thePointerMovesOverEverything() {
        for (int down = 0; down <= 440; down += 9) {
            for (int across = 0; across <= 360; across += 13) {
                assertThat(theOperatorDoes(ScreenEventKind.MOVE, new ScreenEventDetail.At(across, down)))
                        .as("hovering at %dx%d", across, down)
                        .isEqualTo(NOTHING_WENT_WRONG);
            }
        }
    }

    @Test
    @DisplayName("no radio is chosen when the form opens")
    void noRadioIsChosenAtFirst() {
        assertThat(answerTo("reduce [chosen? 'radio 1  chosen? 'radio 2  chosen? 'radio 3]"))
                .isEqualTo("[#(false) #(false) #(false)]");
    }

    @Test
    @DisplayName("an unchosen radio's dot is faint, its 2010 transparency of 200 read as 3.22's opacity of 55")
    void anUnchosenDotIsFaint() {
        assertThat(answerTo("second get-facet the-face 'radio 1 'led-colors")).isEqualTo("50.50.50.55");
    }

    @ParameterizedTest(name = "clicking radio {0} chooses it and only it")
    @ValueSource(ints = {1, 2, 3})
    @DisplayName("clicking a radio chooses it, and the others in its group stay unchosen")
    void clickingARadioChoosesOnlyIt(int chosen) {
        theOperatorClicks("radio", chosen);

        List<String> expected = List.of(1, 2, 3).stream()
                .map(index -> index == chosen ? "#(true)" : "#(false)").toList();
        assertThat(answerTo("reduce [chosen? 'radio 1  chosen? 'radio 2  chosen? 'radio 3]"))
                .isEqualTo("[" + String.join(" ", expected) + "]");
    }

    @Test
    @DisplayName("choosing a second radio unchooses the first")
    void aSecondChoiceReplacesTheFirst() {
        theOperatorClicks("radio", 1);
        theOperatorClicks("radio", 3);

        assertThat(answerTo("reduce [chosen? 'radio 1  chosen? 'radio 3]"))
                .isEqualTo("[#(false) #(true)]");
    }

    @Test
    @DisplayName("a check is clear when the form opens, and clicking it ticks it")
    void clickingACheckTicksIt() {
        assertThat(answerTo("chosen? 'check 1")).isEqualTo("#(false)");

        theOperatorClicks("check", 1);

        assertThat(answerTo("chosen? 'check 1")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("clicking the name field and typing puts what was typed in it")
    void typingIntoTheNameField() {
        theOperatorClicks("field", 1);
        theOperatorTypes("Ben");

        assertThat(answerTo("{Ben} = get-face the-face 'field 1")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("filling in the form and clicking Submit hands over the answers without anything going wrong")
    void submittingTheAnswers() {
        theOperatorClicks("field", 1);
        theOperatorTypes("Ben");
        theOperatorClicks("radio", 2);

        theOperatorClicks("button", 1);

        assertThat(answerTo("{Ben} = get-face the-face 'field 1")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("clicking Reset puts the form back as it opened: the name and comment empty, no radio chosen, no check ticked")
    void resettingTheForm() {
        theOperatorClicks("field", 1);
        theOperatorTypes("Ben");
        theOperatorClicks("area", 1);
        theOperatorTypes("yes");
        theOperatorClicks("radio", 2);
        theOperatorClicks("check", 1);

        theOperatorClicks("button", 2);

        assertThat(answerTo("""
                reduce [
                    empty? get-face the-face 'field 1
                    empty? get-face the-face 'area 1
                    chosen? 'radio 2
                    chosen? 'check 1
                ]""")).isEqualTo("[#(true) #(true) #(false) #(false)]");
    }

    @Test
    @DisplayName("clicking the name field puts a caret in it that the screen draws, and none before")
    void clickingTheFieldShowsACaret() {
        assertThat(theCaretsShown()).as("before any click").isZero();

        theOperatorClicks("field", 1);

        assertThat(theCaretsShown()).as("after clicking the name field").isEqualTo(1);
    }

    private long theCaretsShown() {
        ObjectValue dialect = (ObjectValue) interpreter.run("system/dialects/draw").value();
        return PaintList.ofTheScreen(screen.rootGob(), 1024, 768, dialect).instructions().stream()
                .filter(PaintInstruction.Writing.class::isInstance)
                .map(PaintInstruction.Writing.class::cast)
                .filter(written -> written.caret().isShown())
                .count();
    }

    @Test
    @DisplayName("clicking the comment area and typing puts what was typed in it")
    void typingIntoTheCommentArea() {
        theOperatorClicks("area", 1);
        theOperatorTypes("yes");

        assertThat(answerTo("{yes} = get-face the-face 'area 1")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a comment longer than the area is wide wraps onto more lines inside it, and a short one stays on one")
    void aLongCommentWraps() {
        theOperatorClicks("area", 1);
        theOperatorTypes("no");
        assertThat(answerTo(THE_ROOM_THE_COMMENT_TAKES)).as("a short comment").isEqualTo("[#(true) #(false)]");

        theOperatorTypes(" the quick brown fox jumps over the lazy dog and keeps on running");

        assertThat(answerTo(THE_ROOM_THE_COMMENT_TAKES)).as("a long comment").isEqualTo("[#(true) #(true)]");
    }

    private static final String THE_ROOM_THE_COMMENT_TAKES = """
            comment-face: the-face 'area 1
            written-in: none
            look-in: func [outer] [
                foreach shown any [outer/pane []] [
                    if block? shown/text [written-in: shown]
                    look-in shown
                ]
            ]
            look-in comment-face/gob
            room: size-text written-in
            reduce [room/x <= written-in/size/x  room/y > 12]""";
}
