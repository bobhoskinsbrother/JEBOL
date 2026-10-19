package org.jebol.application;

import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;
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

    private final RecordingScreen screen = RecordingScreen.measure(1024, 768);

    private Interpreter interpreter;

    @BeforeEach
    void openTheExample() {
        Bounds everything = Bounds.standard().withTimeLimit(Duration.ofSeconds(100));
        for (HostService service : HostService.values()) {
            everything = everything.grant(service);
        }
        interpreter = Interpreter.withBounds(everything);
        interpreter.useScreen(screen);
        interpreter.useEnvironment(new ProcessEnvironment());
        interpreter.useFileSystem(FileSystemPort.rootFileSystem(Path.of("src/testFixtures/resources").toAbsolutePath()));
        interpreter.defineFreshWordsIn(OPEN_THE_EXAMPLE);
        assertThat(interpreter.display(interpreter.run(OPEN_THE_EXAMPLE)))
                .as("the example opens its window")
                .startsWith("make gob!");
    }

    private String answerTo(String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private GobValue findTheWindow() {
        return screen.listWhatOpened().getFirst();
    }

    private String actAsTheOperator(ScreenEventType kind, ScreenEventDetail detail) {
        screen.actAsTheOperator(kind, findTheWindow(), detail);
        return answerTo(TAKE_WHAT_IS_WAITING);
    }

    private ScreenEventDetail.At findTheMiddleOf(String style, int index) {
        int[] axes = Arrays.stream(answerTo("the-middle-of '%s %d".formatted(style, index)).split("x"))
                .mapToInt(axis -> (int) Math.round(Double.parseDouble(axis)))
                .toArray();
        return new ScreenEventDetail.At(axes[0], axes[1]);
    }

    private void clickAsTheOperator(String style, int index) {
        ScreenEventDetail.At middle = findTheMiddleOf(style, index);
        assertThat(actAsTheOperator(ScreenEventType.MOVE, middle)).as("moving onto %s %d", style, index)
                .isEqualTo(NOTHING_WENT_WRONG);
        assertThat(actAsTheOperator(ScreenEventType.DOWN, middle)).as("pressing %s %d", style, index)
                .isEqualTo(NOTHING_WENT_WRONG);
        assertThat(actAsTheOperator(ScreenEventType.UP, middle)).as("releasing %s %d", style, index)
                .isEqualTo(NOTHING_WENT_WRONG);
    }

    private void typeAsTheOperator(String typed) {
        for (int character : typed.codePoints().toArray()) {
            assertThat(actAsTheOperator(ScreenEventType.KEY, new ScreenEventDetail.Typed(character)))
                    .as("typing %s", Character.toString(character))
                    .isEqualTo(NOTHING_WENT_WRONG);
        }
    }

    @Test
    @DisplayName("the pointer moves over every part of the form, staying on one face as it goes, without the comparison of a face with itself running away")
    void survivesThePointerMovingOverEverything() {
        for (int down = 0; down <= 440; down += 9) {
            for (int across = 0; across <= 360; across += 13) {
                assertThat(actAsTheOperator(ScreenEventType.MOVE, new ScreenEventDetail.At(across, down)))
                        .as("hovering at %dx%d", across, down)
                        .isEqualTo(NOTHING_WENT_WRONG);
            }
        }
    }

    @Test
    @DisplayName("no radio is chosen when the form opens")
    void choosesNoRadioAtFirst() {
        assertThat(answerTo("reduce [chosen? 'radio 1  chosen? 'radio 2  chosen? 'radio 3]"))
                .isEqualTo("[#(false) #(false) #(false)]");
    }

    @Test
    @DisplayName("an unchosen radio's dot is faint, its 2010 transparency of 200 read as 3.22's opacity of 55")
    void drawsAnUnchosenDotFaint() {
        assertThat(answerTo("second get-facet the-face 'radio 1 'led-colors")).isEqualTo("50.50.50.55");
    }

    @ParameterizedTest(name = "clicking radio {0} chooses it and only it")
    @ValueSource(ints = {1, 2, 3})
    @DisplayName("clicking a radio chooses it, and the others in its group stay unchosen")
    void choosesOnlyTheClickedRadio(int chosen) {
        clickAsTheOperator("radio", chosen);

        List<String> expected = List.of(1, 2, 3).stream()
                .map(index -> index == chosen ? "#(true)" : "#(false)").toList();
        assertThat(answerTo("reduce [chosen? 'radio 1  chosen? 'radio 2  chosen? 'radio 3]"))
                .isEqualTo("[" + String.join(" ", expected) + "]");
    }

    @Test
    @DisplayName("choosing a second radio unchooses the first")
    void replacesTheFirstChoiceWithTheSecond() {
        clickAsTheOperator("radio", 1);
        clickAsTheOperator("radio", 3);

        assertThat(answerTo("reduce [chosen? 'radio 1  chosen? 'radio 3]"))
                .isEqualTo("[#(false) #(true)]");
    }

    @Test
    @DisplayName("a check is clear when the form opens, and clicking it ticks it")
    void ticksAClickedCheck() {
        assertThat(answerTo("chosen? 'check 1")).isEqualTo("#(false)");

        clickAsTheOperator("check", 1);

        assertThat(answerTo("chosen? 'check 1")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("clicking the name field and typing puts what was typed in it")
    void typesIntoTheNameField() {
        clickAsTheOperator("field", 1);
        typeAsTheOperator("Ben");

        assertThat(answerTo("{Ben} = get-face the-face 'field 1")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("filling in the form and clicking Submit hands over the answers without anything going wrong")
    void submitsTheAnswers() {
        clickAsTheOperator("field", 1);
        typeAsTheOperator("Ben");
        clickAsTheOperator("radio", 2);

        clickAsTheOperator("button", 1);

        assertThat(answerTo("{Ben} = get-face the-face 'field 1")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("clicking Reset puts the form back as it opened: the name and comment empty, no radio chosen, no check ticked")
    void resetsTheForm() {
        clickAsTheOperator("field", 1);
        typeAsTheOperator("Ben");
        clickAsTheOperator("area", 1);
        typeAsTheOperator("yes");
        clickAsTheOperator("radio", 2);
        clickAsTheOperator("check", 1);

        clickAsTheOperator("button", 2);

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
    void showsACaretWhenTheFieldIsClicked() {
        assertThat(countTheCaretsShown()).as("before any click").isZero();

        clickAsTheOperator("field", 1);

        assertThat(countTheCaretsShown()).as("after clicking the name field").isEqualTo(1);
    }

    private long countTheCaretsShown() {
        ObjectValue dialect = (ObjectValue) interpreter.run("system/dialects/draw").value();
        return PaintList.ofScreen(screen.rootGob(), 1024, 768, dialect).instructions().stream()
                .filter(PaintInstruction.Writing.class::isInstance)
                .map(PaintInstruction.Writing.class::cast)
                .filter(text -> text.caret().isShown())
                .count();
    }

    @Test
    @DisplayName("clicking the comment area and typing puts what was typed in it")
    void typesIntoTheCommentArea() {
        clickAsTheOperator("area", 1);
        typeAsTheOperator("yes");

        assertThat(answerTo("{yes} = get-face the-face 'area 1")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a comment longer than the area is wide wraps onto more lines inside it, and a short one stays on one")
    void wrapsALongComment() {
        clickAsTheOperator("area", 1);
        typeAsTheOperator("no");
        assertThat(answerTo(THE_ROOM_THE_COMMENT_TAKES)).as("a short comment").isEqualTo("[#(true) #(false)]");

        typeAsTheOperator(" the quick brown fox jumps over the lazy dog and keeps on running");

        assertThat(answerTo(THE_ROOM_THE_COMMENT_TAKES)).as("a long comment").isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("the comment area is no wider than the panel that holds it, so the text inside it is not cut off")
    void keepsTheCommentAreaWithinItsPanel() {
        assertThat(answerTo("""
                find-styled: func [gob wanted /local found] [
                    either all [object? gob/data wanted = select gob/data 'style] [gob] [
                        foreach sub any [gob/pane []] [
                            if found: find-styled sub wanted [return found]
                        ]
                        none
                    ]
                ]
                area-gob: find-styled the-window 'area
                panel-gob: find-styled the-window 'panel
                (area-gob/offset/x + area-gob/size/x) <= panel-gob/size/x"""))
                .isEqualTo("#(true)");
    }

    private static final String THE_ROOM_THE_COMMENT_TAKES = """
            comment-face: the-face 'area 1
            text-in: none
            look-in: func [outer] [
                foreach shown any [outer/pane []] [
                    if block? shown/text [text-in: shown]
                    look-in shown
                ]
            ]
            look-in comment-face/gob
            room: size-text text-in
            reduce [room/x <= text-in/size/x  room/y > 12]""";
}
