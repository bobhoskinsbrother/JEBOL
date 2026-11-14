package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DrawRefusesEffectFromTheSourceTest {

    private static final String TRUE = "#(true)";

    private final RecordingScreen screen = RecordingScreen.measure(1024, 768);
    private final Interpreter interpreter = Interpreter.createOn(
            AnInterpreter.standardPorts().withScreen(screen),
            OutputPort.discard(),
            Bounds.standard().grant(HostService.WINDOWS));

    private String answerTo(String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String errorIdFrom(String source) {
        return answerTo("failed: try [" + source + "] either error? failed [failed/id] ['none]");
    }

    @Nested
    @DisplayName("SHOW refuses a draw block that uses effect, with feature-na")
    class Show {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "effect 0x0 50x50 [invert]",
                "effect 10x10 20x20 [gradient 0x1 0.0.150 0.0.50 blur]",
                "effect [grayscale]",
                "effect",
                "pen red box 1x1 20x20 effect 0x0 100x100 [invert tint 50] pen blue box 30x30 60x60"})
        @DisplayName("in the gob's own draw block, alone or with its arguments")
        void refusesTheGobsOwnBlock(String drawBlock) {
            assertThat(errorIdFrom("show make gob! [size: 100x100 draw: [" + drawBlock + "]]"))
                    .isEqualTo("feature-na");
        }

        @Test
        @DisplayName("and the screen is asked for nothing")
        void asksTheScreenForNothing() {
            errorIdFrom("show make gob! [size: 100x100 draw: [effect 0x0 50x50 [invert]]]");

            assertThat(screen.listWhatWasShown()).isEmpty();
        }

        @Test
        @DisplayName("inside a push block")
        void refusesInsidePush() {
            assertThat(errorIdFrom("""
                    show make gob! [size: 100x100 draw: [pen red push [box effect 0x0 5x5 [invert]]]]"""))
                    .isEqualTo("feature-na");
        }

        @Test
        @DisplayName("inside a push block inside a push block")
        void refusesInsidePushInsidePush() {
            assertThat(errorIdFrom("""
                    show make gob! [size: 100x100 draw: [push [push [effect 0x0 5x5 [invert]]]]]"""))
                    .isEqualTo("feature-na");
        }

        @Test
        @DisplayName("in a child's draw block, down the pane")
        void refusesInAChild() {
            assertThat(errorIdFrom("""
                    parent: make gob! [size: 100x100]
                    child: make gob! [size: 50x50]
                    grandchild: make gob! [size: 10x10 draw: [effect 0x0 5x5 [invert]]]
                    append child grandchild
                    append parent child
                    show parent""")).isEqualTo("feature-na");
            assertThat(screen.listWhatWasShown()).isEmpty();
        }

        @Test
        @DisplayName("in any gob of a block of gobs")
        void refusesInABlockOfGobs() {
            assertThat(errorIdFrom("""
                    fine: make gob! [size: 10x10 draw: [box]]
                    wrong: make gob! [size: 10x10 draw: [effect 0x0 5x5 [invert]]]
                    show reduce [fine wrong]""")).isEqualTo("feature-na");
        }

        @Test
        @DisplayName("and with none, in any window under the root")
        void refusesWithNoneUnderTheRoot() {
            assertThat(errorIdFrom("""
                    append system/view/screen-gob make gob! [size: 10x10 draw: [effect 0x0 5x5 [invert]]]
                    show none""")).isEqualTo("feature-na");
        }

        @Test
        @DisplayName("and showing the root walks every window under it")
        void refusesShowingTheRoot() {
            assertThat(errorIdFrom("""
                    append system/view/screen-gob make gob! [size: 10x10 draw: [effect 0x0 5x5 [invert]]]
                    show system/view/screen-gob""")).isEqualTo("feature-na");
            assertThat(screen.listWhatOpened()).isEmpty();
        }

        @Test
        @DisplayName("the error is the catalogue's own, feature not available")
        void saysFeatureNotAvailable() {
            assertThat(answerTo("""
                    failed: try [show make gob! [size: 10x10 draw: [effect 0x0 5x5 [invert]]]]
                    reduce [failed/type failed/id]""")).isEqualTo("[Internal feature-na]");
        }
    }

    @Nested
    @DisplayName("to image! of a gob refuses effect too")
    class ToImage {

        @Test
        @DisplayName("in the gob's own draw block")
        void refusesTheGobsOwnBlock() {
            assertThat(errorIdFrom("to image! make gob! [size: 10x10 draw: [effect 0x0 5x5 [invert]]]"))
                    .isEqualTo("feature-na");
        }

        @Test
        @DisplayName("and in a child's")
        void refusesInAChild() {
            assertThat(errorIdFrom("""
                    parent: make gob! [size: 10x10]
                    append parent make gob! [size: 5x5 draw: [push [effect 0x0 5x5 [invert]]]]
                    to image! parent""")).isEqualTo("feature-na");
        }
    }

    @Nested
    @DisplayName("what is not refused")
    class NotRefused {

        @Test
        @DisplayName("the word effect in a text block is a string's neighbour, not a command")
        void allowsTheWordInsideText() {
            assertThat(errorIdFrom("show make gob! [size: 100x100 draw: [pen red text 0x0 [effect]]]"))
                    .isEqualTo("none");
        }

        @Test
        @DisplayName("nor in a shape block")
        void allowsTheWordInsideShape() {
            assertThat(errorIdFrom("show make gob! [size: 100x100 draw: [shape [move 0x0 effect]]]"))
                    .isEqualTo("none");
        }

        @Test
        @DisplayName("a gob SHOW is not showing is not read")
        void leavesAGobNotShownAlone() {
            assertThat(errorIdFrom("""
                    elsewhere: make gob! [size: 10x10 draw: [effect 0x0 5x5 [invert]]]
                    show make gob! [size: 10x10 draw: [box]]""")).isEqualTo("none");
        }

        @Test
        @DisplayName("a gob whose content is an effect block shows, and keeps its block")
        void showsAnEffectGob() {
            assertThat(answerTo("""
                    g: make gob! [size: 40x40 effect: [invert]]
                    show g
                    g/effect""")).isEqualTo("[invert]");
            assertThat(screen.listWhatWasShown()).hasSize(1);
        }

        @Test
        @DisplayName("a draw block without effect still shows")
        void showsADrawBlockWithoutEffect() {
            assertThat(answerTo("""
                    gob? show make gob! [size: 10x10 draw: [pen red box 0x0 5x5 push [circle]]]"""))
                    .isEqualTo(TRUE);
            assertThat(screen.listWhatWasShown()).hasSize(1);
        }

        @Test
        @DisplayName("showing none with no effect anywhere answers none, as it always has")
        void showsNoneAsBefore() {
            assertThat(answerTo("none? show none")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("to image! of a draw block without effect is not refused here")
        void imagesWithoutEffectAreNotRefusedHere() {
            assertThat(errorIdFrom("to image! make gob! [size: 10x10 draw: [box]]"))
                    .isNotEqualTo("feature-na");
        }
    }
}
