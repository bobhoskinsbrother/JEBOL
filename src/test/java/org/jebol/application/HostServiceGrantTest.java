package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A host grants each kind of service on its own, and nothing by default.
 *
 * <p>Specified in {@code spec/embed.allium}.
 *
 * <p>A script that asks for a service it has not been granted gets an
 * error that names the service. Silence is the failure this prevents: a
 * READ that quietly answers none reads as an empty file, and a script
 * cannot tell the two apart.
 */
class HostServiceGrantTest {

    private static String answerTo(Bounds bounds, String source) {
        Interpreter interpreter = Interpreter.withBounds(bounds);
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** The id of the error a snippet raises, or "no-error" if it raises none. */
    private static String errorIdOf(Bounds bounds, String source) {
        return answerTo(bounds, "e: try [" + source + "] "
                + "either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("the standard bounds grant nothing")
    void nothingIsGrantedByDefault() {
        for (HostService service : HostService.values()) {
            assertThat(Bounds.standard().grants(service))
                    .as("%s must not be granted by default", service)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("a grant adds one kind and leaves the others alone")
    void oneGrantAtATime() {
        Bounds bounds = Bounds.standard().granting(HostService.CLOCK);
        assertThat(bounds.grants(HostService.CLOCK)).isTrue();
        assertThat(bounds.grants(HostService.FILES)).isFalse();
    }

    @Test
    @DisplayName("two grants both hold")
    void grantsAccumulate() {
        Bounds bounds = Bounds.standard()
                .granting(HostService.CLOCK)
                .granting(HostService.FILES);
        assertThat(bounds.grants(HostService.CLOCK)).isTrue();
        assertThat(bounds.grants(HostService.FILES)).isTrue();
    }

    @Test
    @DisplayName("a grant makes new bounds and does not change the old ones")
    void theOldBoundsAreUnchanged() {
        Bounds first = Bounds.standard();
        first.granting(HostService.CLOCK);
        assertThat(first.grants(HostService.CLOCK))
                .as("granting must answer new bounds")
                .isFalse();
    }

    @Test
    @DisplayName("a script that asks for an ungranted service gets an error")
    void anUngrantedServiceRaises() {
        assertThat(errorIdOf(Bounds.standard(), "now")).isEqualTo("no-service");
    }

    @Test
    @DisplayName("the error names the service that was refused")
    void theErrorSaysWhich() {
        assertThat(answerTo(Bounds.standard(),
                "e: try [now] true? find form e/arg1 \"clock\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the same script works once the service is granted")
    void aGrantedServiceAnswers() {
        // The off point. Without it the test above would pass on a NOW
        // that never works at all.
        assertThat(answerTo(Bounds.standard().granting(HostService.CLOCK),
                "date? now")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the four natives that call C code are refused whatever is granted")
    void theExtensionPointsAreNeverAvailable() {
        // Nothing can offer these and no grant turns them on, thus they
        // are refused before the grant is looked at.
        Bounds everything = Bounds.standard();
        for (HostService service : HostService.values()) {
            everything = everything.granting(service);
        }
        for (String native0 : new String[] {
                "load-extension %a", "do-callback []", "do-commands []", "access-os 'pid"}) {
            assertThat(errorIdOf(everything, native0))
                    .as("%s must always be refused", native0)
                    .isEqualTo("no-service");
        }
    }
}
