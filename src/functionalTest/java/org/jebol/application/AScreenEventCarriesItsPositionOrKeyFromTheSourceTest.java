package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventKind;
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

    private final RecordingScreen screen = RecordingScreen.measuring(1024, 768);

    private final Interpreter interpreter = anInterpreterOn(screen);

    private Interpreter anInterpreterOn(RecordingScreen watched) {
        Interpreter made = Interpreter.withBounds(Bounds.standard()
                .granting(HostService.WINDOWS)
                .withWallClockLimit(Duration.ofSeconds(10)));
        made.useScreen(watched);
        made.defineFreshWordsIn(A_WINDOW_AND_A_WATCHER);
        made.run(A_WINDOW_AND_A_WATCHER);
        return made;
    }

    private GobValue theWindow() {
        return screen.whatOpened().getFirst();
    }

    private String whatTheHandlerSawAfter(ScreenEventKind kind, ScreenEventDetail detail) {
        screen.theOperatorDoes(kind, theWindow(), detail);
        screen.theOperatorDoes(ScreenEventKind.CLOSE, theWindow());
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
    void aPositionArrivesAsTheOffset(ScreenEventKind kind, int across, int down, String spelt) {
        assertThat(whatTheHandlerSawAfter(kind, new ScreenEventDetail.At(across, down)))
                .isEqualTo("[[%s %dx%d _]]".formatted(spelt, across, down));
    }

    @ParameterizedTest(name = "a pointer at {0}x{1} is read back exactly")
    @CsvSource({"0, 0", "1, 1", "32767, 32767"})
    @DisplayName("an offset is read back at the edges of what it can hold")
    @Timeout(20)
    void theEdgesOfAnOffset(int across, int down) {
        assertThat(whatTheHandlerSawAfter(ScreenEventKind.DOWN, new ScreenEventDetail.At(across, down)))
                .isEqualTo("[[down %dx%d _]]".formatted(across, down));
    }

    @Test
    @DisplayName("a typed character arrives as event/key")
    @Timeout(20)
    void aTypedCharacterArrivesAsTheKey() {
        assertThat(whatTheHandlerSawAfter(ScreenEventKind.KEY, new ScreenEventDetail.Typed('a')))
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
    void aNamedKeyArrivesAsItsWord(ScreenEventKind kind, String spelt, String name) {
        assertThat(whatTheHandlerSawAfter(kind, new ScreenEventDetail.NamedKey(name)))
                .isEqualTo("[[%s _ %s]]".formatted(spelt, name));
    }

    @Test
    @DisplayName("a key the catalogue does not name is reported with no key")
    @Timeout(20)
    void anUnnamedKeyHasNoKey() {
        assertThat(whatTheHandlerSawAfter(ScreenEventKind.CONTROL, new ScreenEventDetail.NamedKey("print-screen")))
                .isEqualTo("[[control _ _]]");
    }

    @Test
    @DisplayName("reading the key of a key event that holds no character is refused, not a host failure")
    void aKeyThatIsNoCharacterIsRefused() {
        assertThat(interpreter.display(interpreter.run("""
                e: try [k: make event! [type: 'key key: 'f12] k/key]
                e/id""")))
                .isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("an event the screen saw nothing more about reads neither offset nor key")
    @Timeout(20)
    void nothingMoreReadsNeither() {
        assertThat(whatTheHandlerSawAfter(ScreenEventKind.DOWN, new ScreenEventDetail.NothingMore()))
                .isEqualTo("[[down _ _]]");
    }
}
