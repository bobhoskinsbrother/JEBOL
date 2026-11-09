package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class APortsStorageFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.createOn(AnInterpreter.standardPorts(), OutputPort.discard(), Bounds.standard());
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a cipher port keeps its working in state and leaves extra to the script")
    void behavesTheSameOnACipherPort() {
        assertThat(answerTo("""
                k: open [
                    scheme: 'crypt
                    algorithm: 'aes-128-cbc
                    key: #{000102030405060708090A0B0C0D0E0F}
                    iv: #{000102030405060708090A0B0C0D0E0F}
                    direction: 'encrypt
                ]
                k/extra: "the script's own"
                write k #{00010203040506070809000102030405}
                reduce [true? k/state  k/extra  binary? read k]"""))
                .isEqualTo("""
                        [#(true) "the script's own" #(true)]""");
    }
}
