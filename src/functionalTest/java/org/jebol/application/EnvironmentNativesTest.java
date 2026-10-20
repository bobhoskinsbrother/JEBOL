package org.jebol.application;

import org.jebol.domain.host.EnvironmentPort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentNativesTest {

    private static EnvironmentPort makeUpAnEnvironment() {
        return new EnvironmentPort() {

            private final Map<String, String> laidOver = new java.util.LinkedHashMap<>();

            @Override
            public String valueOf(String name) {
                return readAll().get(name);
            }

            @Override
            public Map<String, String> readAll() {
                Map<String, String> everything = new java.util.LinkedHashMap<>(
                        Map.of("HOME", "/home/ben", "SHELL", "/bin/zsh"));
                laidOver.forEach((name, held) -> {
                    if (held == null) {
                        everything.remove(name);
                    } else {
                        everything.put(name, held);
                    }
                });
                return everything;
            }

            @Override
            public void assign(String name, String value) {
                laidOver.put(name, value);
            }
        };
    }

    private static Interpreter makeAnInterpreterReaching(boolean granted) {
        Bounds bounds = granted
                ? Bounds.standard().grant(HostService.ENVIRONMENT)
                : Bounds.standard();
        return Interpreter.createOn(
                Interpreter.standardPorts().withEnvironment(makeUpAnEnvironment()),
                OutputPort.discard(),
                bounds);
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdOf(Interpreter interpreter, String source) {
        return answerTo(interpreter,
                "e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("GET-ENV gives what a name holds")
    void readsOneName() {
        assertThat(answerTo(makeAnInterpreterReaching(true), "get-env \"HOME\"")).isEqualTo("\"/home/ben\"");
    }

    @Test
    @DisplayName("a name the host has not got answers none")
    void answersNoneForAMissingName() {
        assertThat(answerTo(makeAnInterpreterReaching(true), "none? get-env \"NOWHERE\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("LIST-ENV gives every name")
    void readsEveryName() {
        assertThat(answerTo(makeAnInterpreterReaching(true), "(select list-env \"SHELL\") = \"/bin/zsh\""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("without the grant both are refused")
    void needsTheGrant() {
        assertThat(readTheErrorIdOf(makeAnInterpreterReaching(false), "get-env \"HOME\"")).isEqualTo("no-service");
        assertThat(readTheErrorIdOf(makeAnInterpreterReaching(false), "list-env")).isEqualTo("no-service");
    }

    @Test
    @DisplayName("SET-ENV needs the grant, like the other two")
    void needsTheGrantToSet() {
        assertThat(readTheErrorIdOf(makeAnInterpreterReaching(false), "set-env \"HOME\" \"/x\""))
                .isEqualTo("no-service");
    }

    @Test
    @DisplayName("and with the grant it changes what GET-ENV answers")
    void setsWithTheGrant() {
        Interpreter interpreter = makeAnInterpreterReaching(true);
        assertThat(answerTo(interpreter, """
                set-env "HOME" "/x"
                get-env "HOME\"""")).isEqualTo("\"/x\"");
    }
}
