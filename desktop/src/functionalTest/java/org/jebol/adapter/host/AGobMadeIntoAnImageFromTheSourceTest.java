package org.jebol.adapter.host;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AGobMadeIntoAnImageFromTheSourceTest {

    private static String onTheDesktop(String source) {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(DesktopScreen.onThisMachine(new ClasspathFiles())),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.WINDOWS));
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String withNoScreen(String source) {
        Interpreter interpreter = AnInterpreter.withBounds(Bounds.standard());
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a coloured gob becomes an image of its size in that colour, opaque")
    void paintsTheColour() {
        assertThat(onTheDesktop("""
                shot: to image! make gob! [size: 3x2 color: 10.20.30]
                reduce [shot/size shot/1 shot/6 length? shot]""")).isEqualTo("[3x2 10.20.30.255 10.20.30.255 6]");
    }

    @Test
    @DisplayName("a draw block is painted into the image, and white is left where nothing is drawn, as R3's new image is white")
    void paintsTheDrawBlockOnWhite() {
        assertThat(onTheDesktop("""
                drawn: make gob! [size: 4x2]
                drawn/draw: [pen off fill-pen 255.0.0 box 0x0 2x2]
                shot: to image! drawn
                reduce [shot/1 shot/2 shot/3 shot/8]""")).isEqualTo("[255.0.0.255 255.0.0.255 255.255.255.255 255.255.255.255]");
    }

    @Test
    @DisplayName("a gob's pane is painted at each child's offset, and a child is clipped to its parent's size")
    void paintsThePaneClipped() {
        assertThat(onTheDesktop("""
                parent: make gob! [size: 4x4 color: 0.0.255]
                append parent make gob! [offset: 2x2 size: 8x8 color: 0.255.0]
                shot: to image! parent
                reduce [shot/size shot/1 pick shot 11 pick shot 16]""")).isEqualTo("[4x4 0.0.255.255 0.255.0.255 0.255.0.255]");
    }

    @Test
    @DisplayName("the gob's own offset is not where its image starts: the image is the gob at 0x0")
    void startsAtTheGobsOwnCorner() {
        assertThat(onTheDesktop("""
                shot: to image! make gob! [offset: 30x40 size: 2x2 color: 1.2.3]
                reduce [shot/size shot/1]""")).isEqualTo("[2x2 1.2.3.255]");
    }

    @Test
    @DisplayName("a gob of no size becomes an empty image")
    void anEmptyGobIsAnEmptyImage() {
        assertThat(onTheDesktop("""
                shot: to image! make gob! [size: 0x0 color: 1.2.3]
                reduce [shot/size length? shot]""")).isEqualTo("[0x0 0]");
    }

    @Test
    @DisplayName("text is written into the image in the carried font")
    void writesText() {
        assertThat(onTheDesktop("""
                labelled: make gob! [size: 80x30]
                labelled/text: compose [font (make object! [size: 20 color: 0.0.0]) "MMM"]
                shot: to image! labelled
                inked: 0
                repeat at length? shot [if 128 > first pick shot at [inked: inked + 1]]
                inked > 40""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("with no screen to paint on it is R3's refusal on a host that cannot compose, a bad-make-arg naming the gob")
    void refusesWithNoScreen() {
        assertThat(withNoScreen("""
                made: try [to image! make gob! [size: 2x2]]
                reduce [made/id made/arg1 type? made/arg2]""")).isEqualTo("[bad-make-arg #(image!) #(gob!)]");
    }
}
