package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HostServiceGrantTest {

    private static String answerTo(Bounds bounds, String source) {
        Interpreter interpreter = Interpreter.withBounds(bounds);
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdOf(Bounds bounds, String source) {
        return answerTo(bounds, "e: try [" + source + "] "
                + "either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("the standard bounds grant nothing")
    void grantsNothingByDefault() {
        for (HostService service : HostService.values()) {
            assertThat(Bounds.standard().grants(service))
                    .as("%s must not be granted by default", service)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("a grant adds one type and leaves the others alone")
    void grantsOneAtATime() {
        Bounds bounds = Bounds.standard().grant(HostService.CLOCK);
        assertThat(bounds.grants(HostService.CLOCK)).isTrue();
        assertThat(bounds.grants(HostService.FILES)).isFalse();
    }

    @Test
    @DisplayName("two grants both hold")
    void grantsAccumulate() {
        Bounds bounds = Bounds.standard()
                .grant(HostService.CLOCK)
                .grant(HostService.FILES);
        assertThat(bounds.grants(HostService.CLOCK)).isTrue();
        assertThat(bounds.grants(HostService.FILES)).isTrue();
    }

    @Test
    @DisplayName("a grant makes new bounds and does not change the old ones")
    void leavesTheOldBoundsUnchanged() {
        Bounds first = Bounds.standard();
        first.grant(HostService.CLOCK);
        assertThat(first.grants(HostService.CLOCK))
                .as("granting must answer new bounds")
                .isFalse();
    }

    @Test
    @DisplayName("a script that asks for an ungranted service gets an error")
    void raisesForAnUngrantedService() {
        assertThat(readTheErrorIdOf(Bounds.standard(), "now")).isEqualTo("no-service");
    }

    @Test
    @DisplayName("the error names the service that was refused")
    void saysWhichInTheError() {
        assertThat(answerTo(Bounds.standard(),
                "e: try [now] true? find form e/arg1 \"clock\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the same script works once the service is granted")
    void answersAGrantedService() {
        assertThat(answerTo(Bounds.standard().grant(HostService.CLOCK),
                "date? now")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("loading native code from a file is refused whatever is granted, as never portable")
    void neverOffersNativeCode() {
        assertThat(readTheErrorIdOf(grantEverything(), "load-extension %a")).isEqualTo("no-extension");
    }

    @Test
    @DisplayName("but ACCESS-OS is not one of them, and answers the process id")
    void accessOsIsNotAnExtensionPoint() {
        assertThat(answerTo(grantEverything(), "integer? access-os 'pid"))
                .as("this test used to demand a refusal here, which recorded the "
                        + "stub rather than Rebol: ACCESS-OS loads no C of its own, "
                        + "and n-io.c answers a number for the field the JVM knows")
                .isEqualTo("#(true)");
    }

    private static Bounds grantEverything() {
        Bounds everything = Bounds.standard();
        for (HostService service : HostService.values()) {
            everything = everything.grant(service);
        }
        return everything;
    }
}
