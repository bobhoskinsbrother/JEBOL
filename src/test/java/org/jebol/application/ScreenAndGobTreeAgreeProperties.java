package org.jebol.application;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.jebol.domain.host.HostService;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenAndGobTreeAgreeProperties {

    private static Interpreter withAScreen(RecordingScreen screen) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.WINDOWS)
                        .withWallClockLimit(Duration.ofSeconds(10)));
        interpreter.useScreen(screen);
        return interpreter;
    }

    @Property(tries = 40)
    void theyAgreeAfterAnyRunOfOpensAndCloses(
            @ForAll @IntRange(min = 0, max = 6) int opens,
            @ForAll @IntRange(min = 0, max = 6) int closes) {

        RecordingScreen screen = RecordingScreen.measuring(1024, 768);
        Interpreter interpreter = withAScreen(screen);

        interpreter.run("windows: copy []");
        for (int each = 0; each < opens; each++) {
            interpreter.run("append windows view/no-wait make gob! [size: 100x100]");
        }
        for (int each = 0; each < Math.min(closes, opens); each++) {
            interpreter.run("unview take windows");
        }

        int stillOpen = opens - Math.min(closes, opens);
        assertThat(screen.whatIsStandingOpen())
                .as("%d opened, %d closed", opens, closes)
                .hasSize(stillOpen);
        assertThat(interpreter.display(interpreter.run(
                "length? system/view/screen-gob")))
                .as("and the screen gob holds exactly the same number")
                .isEqualTo(String.valueOf(stillOpen));
    }
}
