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

    /**
     * A stand-in environment, so the test does not depend on the machine.
     *
     * <p>One per interpreter rather than one for the class. Shared, a name a
     * test set leaked into the next one and GET-ENV answered what its
     * neighbour had written -- which is the same reason the real port keeps
     * its overlay to itself instead of in a static.
     */
    private static EnvironmentPort madeUp() {
        return new EnvironmentPort() {

            private final Map<String, String> laidOver = new java.util.LinkedHashMap<>();

            @Override
            public String valueOf(String name) {
                return all().get(name);
            }

            @Override
            public Map<String, String> all() {
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
            public void nameHolds(String name, String value) {
                laidOver.put(name, value);
            }
        };
    }

    private static Interpreter reaching(boolean granted) {
        Bounds bounds = granted
                ? Bounds.standard().granting(HostService.ENVIRONMENT)
                : Bounds.standard();
        Interpreter interpreter = Interpreter.withBounds(bounds);
        interpreter.useEnvironment(madeUp());
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

    /**
     * These two asked for SET-ENV to be refused whatever the host, on the
     * ground that a JVM cannot change its own environment. That is true of
     * the process and beside the point: what a script means by setting a
     * variable is that GET-ENV answers it afterwards and a child sees it,
     * both of which a JVM can do and a real 3.22.5 does.
     *
     * <p>So the refusal is now about the grant, like every other reach
     * outside, and what SET-ENV does when granted is covered by
     * {@code EnvironmentWritingFromTheSourceTest}.
     */
    @Test
    @DisplayName("SET-ENV needs the grant, like the other two")
    void settingNeedsTheGrant() {
        assertThat(errorIdOf(reaching(false), "set-env \"HOME\" \"/x\""))
                .isEqualTo("no-service");
    }

    @Test
    @DisplayName("and with the grant it changes what GET-ENV answers")
    void withTheGrantItSets() {
        Interpreter interpreter = reaching(true);
        assertThat(answerTo(interpreter, """
                set-env "HOME" "/x"
                get-env "HOME\"""")).isEqualTo("\"/x\"");
    }
}
