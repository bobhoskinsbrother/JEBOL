package org.jebol.application;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenAndGobTreeAgreeProperties {

    private static Interpreter makeAnInterpreterWithAScreen(RecordingScreen screen) {
        return Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(screen),
                OutputPort.discard(),
                Bounds.standard()
                        .grant(HostService.WINDOWS)
                        .withTimeLimit(Duration.ofSeconds(10)));
    }

    @Property(tries = 40)
    void agreeAfterAnyRunOfOpensAndCloses(
            @ForAll @IntRange(min = 0, max = 6) int opens,
            @ForAll @IntRange(min = 0, max = 6) int closes) {

        RecordingScreen screen = RecordingScreen.measure(1024, 768);
        Interpreter interpreter = makeAnInterpreterWithAScreen(screen);

        interpreter.run("windows: copy []");
        for (int each = 0; each < opens; each++) {
            interpreter.run("append windows view/no-wait make gob! [size: 100x100]");
        }
        for (int each = 0; each < Math.min(closes, opens); each++) {
            interpreter.run("unview take windows");
        }

        int stillOpen = opens - Math.min(closes, opens);
        assertThat(screen.listWhatIsStandingOpen())
                .as("%d opened, %d closed", opens, closes)
                .hasSize(stillOpen);
        assertThat(interpreter.display(interpreter.run(
                "length? system/view/screen-gob")))
                .as("and the screen gob holds exactly the same number")
                .isEqualTo(String.valueOf(stillOpen));
    }
}
