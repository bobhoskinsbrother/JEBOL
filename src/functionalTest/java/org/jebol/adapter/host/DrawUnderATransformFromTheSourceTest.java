package org.jebol.adapter.host;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class DrawUnderATransformFromTheSourceTest {

    static String readTheScript() throws IOException {
        try (InputStream in = Objects.requireNonNull(
                DrawUnderATransformFromTheSourceTest.class.getResourceAsStream("/draw/under-a-transform.reb"))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("on the desktop, an image, a clip, a conic gradient and text all follow the transform as R3's AGG does")
    void everythingFollowsTheTransform() throws IOException {
        StringBuilder printed = new StringBuilder();
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(DesktopScreen.onThisMachine(new ClasspathFiles())),
                printed::append,
                Bounds.standard().grant(HostService.WINDOWS));
        String script = readTheScript();
        interpreter.defineNewWordsIn(script);
        interpreter.run(script);

        assertThat(printed.toString()).contains(
                "image under scale in an offset gob: 255.100.40.255",
                "clip under scale: 0.255.0.255 255.255.255.255 255.255.255.255",
                "conic under scale, both sides drawn: true true",
                "vectorial text is scaled, raster text is not: true",
                "raster text's offset goes through the transform: true",
                "done");
    }
}
