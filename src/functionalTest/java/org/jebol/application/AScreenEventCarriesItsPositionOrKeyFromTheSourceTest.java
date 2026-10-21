package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;
import org.jebol.domain.value.GobValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AScreenEventCarriesItsPositionOrKeyFromTheSourceTest {

    private static final String A_WINDOW_AND_A_WATCHER = """
            view/no-wait make gob! [size: 100x100]
            seen: copy []
            handle-events [
                name: 'watcher
                priority: 90
                handler: func [event] [
                    unless event/type = 'close [
                        append/only seen reduce [event/type event/offset event/key]
                    ]
                    event
                ]
            ]
            """;

    private final RecordingScreen screen = RecordingScreen.measure(1024, 768);

    private final Interpreter interpreter = makeAnInterpreterOn(screen);

    private Interpreter makeAnInterpreterOn(RecordingScreen watched) {
        Interpreter made = Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(watched),
                OutputPort.discard(),
                Bounds.standard()
                        .grant(HostService.WINDOWS)
                        .withTimeLimit(Duration.ofSeconds(10)));
        made.defineNewWordsIn(A_WINDOW_AND_A_WATCHER);
        made.run(A_WINDOW_AND_A_WATCHER);
        return made;
    }

    private GobValue findTheWindow() {
        return screen.listWhatOpened().getFirst();
    }

    private String readWhatTheHandlerSawAfter(ScreenEventType kind, ScreenEventDetail detail) {
        screen.actAsTheOperator(kind, findTheWindow(), detail);
        screen.actAsTheOperator(ScreenEventType.CLOSE, findTheWindow());
        interpreter.run("do-events");
        return interpreter.display(interpreter.run("seen"));
    }

    @ParameterizedTest(name = "{0} at {1}x{2} reads event/offset {1}x{2}")
    @CsvSource({
            "DOWN, 10, 20, down",
            "UP, 10, 20, up",
            "MOVE, 10, 20, move",
            "RESIZE, 300, 200, resize",
            "OFFSET, 50, 60, offset",
    })
    @DisplayName("an event with a position or a size reads it back as event/offset")
    @Timeout(20)
    void deliversAPositionAsTheOffset(ScreenEventType kind, int across, int down, String spelt) {
        assertThat(readWhatTheHandlerSawAfter(kind, new ScreenEventDetail.At(across, down)))
                .isEqualTo("[[%s %dx%d _]]".formatted(spelt, across, down));
    }

    @ParameterizedTest(name = "a pointer at {0}x{1} is read back exactly")
    @CsvSource({"0, 0", "1, 1", "32767, 32767"})
    @DisplayName("an offset is read back at the edges of what it can hold")
    @Timeout(20)
    void coversTheEdgesOfAnOffset(int across, int down) {
        assertThat(readWhatTheHandlerSawAfter(ScreenEventType.DOWN, new ScreenEventDetail.At(across, down)))
                .isEqualTo("[[down %dx%d _]]".formatted(across, down));
    }

    @Test
    @DisplayName("a line wheel turn arrives as scroll-line and reads its amount from event/offset's down half")
    @Timeout(20)
    void deliversALineWheelTurnAsTheOffset() {
        assertThat(readWhatTheHandlerSawAfter(ScreenEventType.SCROLL_LINE,
                new ScreenEventDetail.Scrolled(10, 20, -30)))
                .isEqualTo("[[scroll-line 0x-30 _]]");
    }

    @Test
    @DisplayName("a page wheel turn arrives as scroll-page and reads its amount the same way")
    @Timeout(20)
    void deliversAPageWheelTurnAsTheOffset() {
        assertThat(readWhatTheHandlerSawAfter(ScreenEventType.SCROLL_PAGE,
                new ScreenEventDetail.Scrolled(10, 20, -10)))
                .isEqualTo("[[scroll-page 0x-10 _]]");
    }

    @Test
    @DisplayName("a typed character arrives as event/key")
    @Timeout(20)
    void deliversATypedCharacterAsTheKey() {
        assertThat(readWhatTheHandlerSawAfter(ScreenEventType.KEY, new ScreenEventDetail.Typed('a')))
                .isEqualTo("""
                        [[key _ #"a"]]""");
    }

    @ParameterizedTest(name = "{0} {1} arrives as the word {2}")
    @CsvSource({
            "CONTROL, control, page-up",
            "CONTROL, control, up",
            "CONTROL, control, page-down",
            "CONTROL_UP, control-up, escape",
            "CONTROL, control, f12",
            "CONTROL, control, begin",
    })
    @DisplayName("a key that types nothing arrives as a control event naming it from the catalogue")
    @Timeout(20)
    void deliversANamedKeyAsItsWord(ScreenEventType kind, String spelt, String name) {
        assertThat(readWhatTheHandlerSawAfter(kind, new ScreenEventDetail.NamedKey(name)))
                .isEqualTo("[[%s _ %s]]".formatted(spelt, name));
    }

    @Test
    @DisplayName("a key the catalogue does not name is reported with no key")
    @Timeout(20)
    void givesAnUnnamedKeyNoKey() {
        assertThat(readWhatTheHandlerSawAfter(ScreenEventType.CONTROL, new ScreenEventDetail.NamedKey("print-screen")))
                .isEqualTo("[[control _ _]]");
    }

    @Test
    @DisplayName("reading the key of a key event that holds no character is refused, not a host failure")
    void refusesAKeyThatIsNoCharacter() {
        assertThat(interpreter.display(interpreter.run("""
                e: try [k: make event! [type: 'key key: 'f12] k/key]
                e/id""")))
                .isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("an event the screen saw nothing more about reads neither offset nor key")
    @Timeout(20)
    void readsNeitherForAnythingElse() {
        assertThat(readWhatTheHandlerSawAfter(ScreenEventType.DOWN, new ScreenEventDetail.NothingMore()))
                .isEqualTo("[[down _ _]]");
    }
}
