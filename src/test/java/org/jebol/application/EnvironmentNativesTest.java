package org.jebol.application;

import org.jebol.domain.eval.EnvironmentPort;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GET-ENV and LIST-ENV, and why SET-ENV cannot exist.
 *
 * <p>Specified in {@code spec/embed.allium}.
 *
 * <p>The environment is read only. A JVM cannot change the environment of
 * its own process, thus SET-ENV has nothing to call. It says that no host
 * can offer it rather than that this host did not grant it, because the
 * first can change between runs and the second never does.
 */
class EnvironmentNativesTest {

    /** A stand-in environment, so the test does not depend on the machine. */
    private static final EnvironmentPort MADE_UP = new EnvironmentPort() {
        @Override
        public String valueOf(String name) {
            return all().get(name);
        }

        @Override
        public Map<String, String> all() {
            return Map.of("HOME", "/home/ben", "SHELL", "/bin/zsh");
        }
    };

    private static Interpreter reaching(boolean granted) {
        Bounds bounds = granted
                ? Bounds.standard().granting(HostService.ENVIRONMENT)
                : Bounds.standard();
        Interpreter interpreter = Interpreter.withBounds(bounds);
        interpreter.useEnvironment(MADE_UP);
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(Interpreter interpreter, String source) {
        return answerTo(interpreter,
                "e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("GET-ENV gives what a name holds")
    void oneNameCanBeRead() {
        assertThat(answerTo(reaching(true), "get-env \"HOME\"")).isEqualTo("\"/home/ben\"");
    }

    @Test
    @DisplayName("a name the host has not got answers none")
    void aMissingNameIsNone() {
        assertThat(answerTo(reaching(true), "none? get-env \"NOWHERE\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("LIST-ENV gives every name")
    void everyNameCanBeRead() {
        assertThat(answerTo(reaching(true), "(select list-env \"SHELL\") = \"/bin/zsh\""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("without the grant both are refused")
    void theGrantIsNeeded() {
        assertThat(errorIdOf(reaching(false), "get-env \"HOME\"")).isEqualTo("no-service");
        assertThat(errorIdOf(reaching(false), "list-env")).isEqualTo("no-service");
    }

    @Test
    @DisplayName("SET-ENV is refused even when the grant is given")
    void settingIsNeverPossible() {
        assertThat(errorIdOf(reaching(true), "set-env \"HOME\" \"/x\""))
                .isEqualTo("no-service");
    }

    @Test
    @DisplayName("the refusal says that no host can offer it")
    void theReasonIsNotThisHost() {
        assertThat(answerTo(reaching(true),
                "e: try [set-env \"HOME\" \"/x\"] true? find form e/arg1 \"not present\""))
                .isEqualTo("#(true)");
    }
}
