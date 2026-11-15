package org.jebol.android;

import org.jebol.adapter.android.AndroidHost;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheCoreBootsOnArtTest {

    @Test
    @DisplayName("an interpreter boots on ART from the library the APK carries, and adds")
    void bootsAndAdds() {
        Interpreter interpreter = Interpreter.createOn(new AndroidHost().ports(), OutputPort.discard(), Bounds.standard());

        assertThat(interpreter.display(interpreter.run("1 + 2"))).isEqualTo("3");
    }

    @Test
    @DisplayName("the platform it answers is the one ART reports, read from the system properties")
    void answersArtsPlatform() {
        Interpreter interpreter = Interpreter.createOn(new AndroidHost().ports(), OutputPort.discard(), Bounds.standard());

        assertThat(interpreter.display(interpreter.run("form system/platform"))).isNotBlank();
    }
}
