package org.jebol.application;

import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.domain.host.ConsolePort;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.GobScreen;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.host.ScreenEvent;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;
import org.jebol.domain.host.ScreenMetric;
import org.jebol.domain.host.WindowPort;
import org.jebol.domain.render.TextMeasurement;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.GobStorage;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
class TheScreenFormFromTheSourceTest {

    private static final int ENTER = 13;
    private static final int BACKSPACE = 8;
    private static final int ESCAPE = 27;

    private final AnOperatorAtTheScreen operator = new AnOperatorAtTheScreen();
    private final AWindowPortThatCounts windows = new AWindowPortThatCounts();

    private Interpreter onTheScreen(ConsolePort console) {
        return Interpreter.createOn(
                AnInterpreter.standardPorts()
                        .withScreen(operator)
                        .withWindows(windows)
                        .withConsole(console),
                OutputPort.discard(),
                Bounds.standard().withoutTimeLimit()
                        .grant(HostService.WINDOWS)
                        .grant(HostService.CONSOLE));
    }

    private String answer(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        return outcome.succeeded() ? Molder.form(outcome.value()) : interpreter.display(outcome);
    }

    @Test
    @DisplayName("REQUEST-PASSWORD on a screen asks through the form, and Enter answers what was typed")
    void enterAnswersWhatWasTyped() {
        operator.types("s3cret").presses(ENTER);

        assertThat(answer(onTheScreen(new AConsoleTypedOnTheScreen()), "request-password"))
                .isEqualTo("s3cret");
        assertThat(windows.passwordsAsked()).isZero();
    }

    @Test
    @DisplayName("the form never shows what is typed into it, only one dot for each character")
    void theFormMasksWhatIsTyped() {
        operator.types("s3cret").presses(ENTER);

        answer(onTheScreen(new AConsoleTypedOnTheScreen()), "request-password");

        assertThat(operator.everyTextShown()).noneMatch(shown -> shown.contains("s3") || shown.contains("cret"));
        assertThat(operator.everyTextShown()).contains("••••••");
    }

    @Test
    @DisplayName("Backspace takes the last character away, and on an empty field takes nothing")
    void backspaceTakesTheLastAway() {
        operator.presses(BACKSPACE).types("abc").presses(BACKSPACE).types("d").presses(ENTER);

        assertThat(answer(onTheScreen(new AConsoleTypedOnTheScreen()), "request-password"))
                .isEqualTo("abd");
    }

    @Test
    @DisplayName("Enter on an empty field answers empty text, which is not the same as declining")
    void enterOnNothingAnswersEmptyText() {
        operator.presses(ENTER);

        assertThat(answer(onTheScreen(new AConsoleTypedOnTheScreen()), """
                answered: request-password
                mold reduce [string? answered empty? answered]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("Escape declines, and declining answers none")
    void escapeAnswersNone() {
        operator.types("half").presses(ESCAPE);

        assertThat(answer(onTheScreen(new AConsoleTypedOnTheScreen()), "none? request-password"))
                .isEqualTo("true");
    }

    @Test
    @DisplayName("closing the form's window declines too")
    void closingAnswersNone() {
        operator.types("half").closesTheWindow();

        assertThat(answer(onTheScreen(new AConsoleTypedOnTheScreen()), "none? request-password"))
                .isEqualTo("true");
    }

    @Test
    @DisplayName("a script that never called VIEW gets the form in a window of its own, and the window goes when it answers")
    void theFormTakesAWindowOfItsOwn() {
        operator.types("x").presses(ENTER);

        answer(onTheScreen(new AConsoleTypedOnTheScreen()), "request-password");

        assertThat(operator.windowsOpened()).isEqualTo(1);
        assertThat(operator.windowsShowing()).isZero();
    }

    @Test
    @DisplayName("the form is modal: a handler of the script's own hears nothing while it is open")
    void theFormIsModal() {
        operator.types("x").presses(ENTER);

        assertThat(answer(onTheScreen(new AConsoleTypedOnTheScreen()), """
                heard: copy []
                handle-events [name: 'the-scripts-own priority: 90 handler: func [event] [append heard event/type event]]
                answered: request-password
                mold reduce [answered length? heard]""")).isEqualTo("""
                ["x" 0]""");
    }

    @Test
    @DisplayName("characters beyond ASCII are typed as themselves")
    void typesBeyondAscii() {
        operator.types("café漢").presses(ENTER);

        assertThat(answer(onTheScreen(new AConsoleTypedOnTheScreen()), "request-password"))
                .isEqualTo("café漢");
    }

    @Test
    @DisplayName("INPUT on a console typed on the screen reads its line through the same form, shown as typed")
    void inputReadsThroughTheForm() {
        operator.types("a line").presses(ENTER);

        assertThat(answer(onTheScreen(new AConsoleTypedOnTheScreen()), "input")).isEqualTo("a line");
        assertThat(operator.everyTextShown()).contains("a line");
    }

    @Test
    @DisplayName("ASK heads the line form with its question, as a terminal shows the prompt on the line being typed")
    void askHeadsTheFormWithItsQuestion() {
        operator.types("Ada").presses(ENTER);

        assertThat(answer(onTheScreen(new AConsoleTypedOnTheScreen()), "ask {Your name? }")).isEqualTo("Ada");
        assertThat(operator.everyTextShown()).contains("Your name?").doesNotContain("Type a line");
        assertThat(operator.typingHeadings()).containsExactly("Your name?");
        assertThat(operator.isTypingHeld()).isFalse();
    }

    @Test
    @DisplayName("the heading is only what was printed since the last line ended, however many pieces it came in")
    void theHeadingIsTheUnfinishedLine() {
        operator.presses(ENTER);

        answer(onTheScreen(new AConsoleTypedOnTheScreen()), """
                prin {first line^/second }
                prin {and third? }
                input""");

        assertThat(operator.everyTextShown()).contains("second and third?").doesNotContain("first line");
    }

    @Test
    @DisplayName("with nothing printed since a line ended, the form asks for a line")
    void anEndedLineLeavesTheDefaultHeading() {
        operator.presses(ENTER);

        answer(onTheScreen(new AConsoleTypedOnTheScreen()), """
                print {done}
                input""");

        assertThat(operator.everyTextShown()).contains("Type a line").doesNotContain("done");
    }

    @Test
    @DisplayName("the password form is headed Password whatever was printed before it")
    void thePasswordFormKeepsItsHeading() {
        operator.presses(ENTER);

        answer(onTheScreen(new AConsoleTypedOnTheScreen()), """
                prin {Secret? }
                request-password""");

        assertThat(operator.everyTextShown()).contains("Password").doesNotContain("Secret?");
        assertThat(operator.typingHeadings()).containsExactly("Password");
    }

    @Test
    @DisplayName("INPUT declined through the form answers none")
    void inputDeclinedIsNone() {
        operator.presses(ESCAPE);

        assertThat(answer(onTheScreen(new AConsoleTypedOnTheScreen()), "none? input")).isEqualTo("true");
    }

    @Test
    @DisplayName("a console of the terminal's own still reads its own line, and no form is shown")
    void aTerminalReadsItsOwnLine() {
        assertThat(answer(onTheScreen(new AConsoleThatReads("from the terminal")), "input"))
                .isEqualTo("from the terminal");
        assertThat(operator.windowsOpened()).isZero();
    }

    @Test
    @DisplayName("with no display behind the screen the password is asked of the window port, as a host without JEBOL's screen does")
    void withNoDisplayThePortIsAsked() {
        operator.goesDark();

        assertThat(answer(onTheScreen(new AConsoleTypedOnTheScreen()), "request-password"))
                .isEqualTo("from the port");
        assertThat(windows.passwordsAsked()).isEqualTo(1);
        assertThat(operator.windowsOpened()).isZero();
    }

    private static final class AnOperatorAtTheScreen extends GobScreen {

        private final Deque<OperatorAction> toDo = new ArrayDeque<>();
        private final List<GobValue> showing = new ArrayList<>();
        private final List<String> shown = new ArrayList<>();
        private final TextMeasurement measurement = DefaultFont.readFrom(new ClasspathFiles()).measurement();
        private boolean lit = true;
        private int opened;
        private final List<String> typingHeadings = new ArrayList<>();
        private boolean typingHeld;

        AnOperatorAtTheScreen types(String text) {
            text.codePoints().forEach(this::presses);
            return this;
        }

        AnOperatorAtTheScreen presses(int codepoint) {
            toDo.add(new OperatorAction(ScreenEventType.KEY, new ScreenEventDetail.Typed(codepoint)));
            return this;
        }

        AnOperatorAtTheScreen closesTheWindow() {
            toDo.add(new OperatorAction(ScreenEventType.CLOSE, new ScreenEventDetail.NothingMore()));
            return this;
        }

        List<String> typingHeadings() {
            return typingHeadings;
        }

        boolean isTypingHeld() {
            return typingHeld;
        }

        @Override
        public void takeTyping(String heading) {
            typingHeadings.add(heading);
            typingHeld = true;
        }

        @Override
        public void releaseTyping() {
            typingHeld = false;
        }

        void goesDark() {
            lit = false;
        }

        List<String> everyTextShown() {
            return shown;
        }

        int windowsOpened() {
            return opened;
        }

        int windowsShowing() {
            return showing.size();
        }

        @Override
        public boolean hasDisplay() {
            return lit;
        }

        @Override
        public PairValue measure(ScreenMetric metric, int display) {
            return metric == ScreenMetric.SCREENS ? PairValue.of(1, 1) : PairValue.of(800, 600);
        }

        @Override
        public int displayCount() {
            return 1;
        }

        @Override
        public TextMeasurement textMeasurement() {
            return measurement;
        }

        @Override
        public Optional<ScreenEvent> acceptNextEvent() {
            Optional<ScreenEvent> queuedAlready = super.acceptNextEvent();
            if (queuedAlready.isPresent() || showing.isEmpty() || toDo.isEmpty()) {
                return queuedAlready;
            }
            OperatorAction next = toDo.poll();
            return Optional.of(new ScreenEvent(next.type(), showing.getLast(), next.detail()));
        }

        @Override
        protected Denied refuseWithNoService() {
            return new Denied("no-service", "this test's screen has gone dark");
        }

        @Override
        protected List<GobValue> gobWithWindow() {
            return List.copyOf(showing);
        }

        @Override
        protected void openWindowFor(GobValue gob) {
            opened++;
            showing.add(gob);
            recordTextIn(gob.storage());
        }

        @Override
        protected void repaintWindowFor(GobValue gob) {
            recordTextIn(gob.storage());
        }

        @Override
        protected void closeWindowFor(GobValue gob) {
            showing.removeIf(gob::sharesStorageWith);
        }

        private void recordTextIn(GobStorage storage) {
            for (GobStorage.Content kind : List.of(GobStorage.Content.TEXT, GobStorage.Content.STRING)) {
                recordText(storage.readContentIfKind(kind));
            }
            for (Value child : storage.pane()) {
                if (child instanceof GobValue inner) {
                    recordTextIn(inner.storage());
                }
            }
        }

        private void recordText(Value content) {
            if (content instanceof AnyStringValue text) {
                shown.add(text.text());
            } else if (content instanceof AnyBlockValue rich) {
                rich.remaining().forEach(this::recordText);
            }
        }
    }

    private record OperatorAction(ScreenEventType type, ScreenEventDetail detail) {
    }

    private static final class AWindowPortThatCounts implements WindowPort {

        private int passwords;

        int passwordsAsked() {
            return passwords;
        }

        @Override
        public boolean browse(String target) {
            return true;
        }

        @Override
        public List<String> chooseFiles(boolean forSaving, boolean allowingMany, Optional<String> suggestedName,
                Optional<String> title, List<String> filterPairs) {
            return List.of();
        }

        @Override
        public Optional<String> chooseDirectory(Optional<String> startingAt, Optional<String> title) {
            return Optional.empty();
        }

        @Override
        public Optional<int[]> chooseColour(Optional<int[]> suggested) {
            return Optional.empty();
        }

        @Override
        public Optional<String> askForPassword() {
            passwords++;
            return Optional.of("from the port");
        }
    }

    private static final class AConsoleTypedOnTheScreen implements ConsolePort {

        @Override
        public String readLine() {
            throw new FilePort.Denied("no-port", "this console is typed on the screen");
        }

        @Override
        public String readHiddenLine() {
            throw new FilePort.Denied("no-port", "this console is typed on the screen");
        }

        @Override
        public boolean isTypedOnScreen() {
            return true;
        }
    }

    private record AConsoleThatReads(String line) implements ConsolePort {

        @Override
        public String readLine() {
            return line;
        }

        @Override
        public String readHiddenLine() {
            return line;
        }
    }
}
