package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ABrowserHostFromTheSourceTest {

    private static Bounds aBrowsersBounds() {
        return Bounds.standard()
                .withoutAWallClockLimit()
                .grant(HostService.CONSOLE)
                .grant(HostService.FILES)
                .grant(HostService.WINDOWS)
                .grant(HostService.WEB)
                .grant(HostService.WORKING_DIRECTORY)
                .lacking(HostService.NETWORK)
                .lacking(HostService.PROCESSES)
                .lacking(HostService.ENVIRONMENT)
                .lacking(HostService.CERTIFICATES)
                .calls("a browser");
    }

    private static Interpreter aBrowser() {
        return Interpreter.withBounds(aBrowsersBounds());
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String theSentenceFor(Interpreter interpreter, String source) {
        return answerTo(interpreter, "e: try [" + source + "] mold e");
    }

    @Test
    @DisplayName("a service a browser has not got is refused as not present, with the browser named")
    void whatABrowserCannotDoIsNotPresent() {
        Interpreter browser = aBrowser();

        assertThat(theSentenceFor(browser, "call {ls}"))
                .contains("a browser does not allow processes, which is not present");
        assertThat(theSentenceFor(browser, "read tcp://127.0.0.1:9"))
                .contains("a browser does not allow network, which is not present");
    }

    @Test
    @DisplayName("and the id is no-service, so a script acts on it as any refusal")
    void theRefusedIdIsNoService() {
        assertThat(answerTo(aBrowser(),
                "e: try [call {ls}] either error? e [e/id] ['no-error]"))
                .isEqualTo("no-service");
    }

    @Test
    @DisplayName("DNS and UDP are refused the same way, before anything is attempted")
    void everyTransportABrowserHasNotIsRefused() {
        Interpreter browser = aBrowser();

        assertThat(answerTo(browser, "e: try [read dns://example.com] e/id"))
                .isEqualTo("no-service");
        assertThat(answerTo(browser, "e: try [open udp://127.0.0.1:9] e/id"))
                .isEqualTo("no-service");
    }

    @Test
    @DisplayName("a secure connection is refused for want of authorities, since the browser judges certificates itself")
    void aSecureConnectionIsRefusedAsNotPresent() {
        assertThat(theSentenceFor(aBrowser(), "open tls://example.com:443"))
                .contains("a browser does not allow certificates, which is not present");
    }

    @Test
    @DisplayName("a network port cannot be opened by hand either")
    void openingATcpPortIsRefused() {
        assertThat(answerTo(aBrowser(), "e: try [open tcp://127.0.0.1:9] e/id"))
                .isEqualTo("no-service");
    }

    @Test
    @DisplayName("a host that has the service and did not grant it says not granted instead")
    void aServiceMerelyNotGrantedIsADifferentSentence() {
        Interpreter host = Interpreter.withBounds(Bounds.standard());

        assertThat(theSentenceFor(host, "read tcp://127.0.0.1:9"))
                .contains("network is not granted")
                .doesNotContain("not present");
    }

    @Test
    @DisplayName("a host that lacks a service cannot also grant it")
    void grantingWhatIsLackingIsARefusalAtTheHost() {
        assertThatThrownBy(() -> Bounds.standard()
                .calls("a browser")
                .lacking(HostService.NETWORK)
                .grant(HostService.NETWORK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a browser cannot grant network");
    }

    @Test
    @DisplayName("a host names itself, and may not be nameless")
    void aHostNameIsRequired() {
        assertThatThrownBy(() -> Bounds.standard().calls(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names itself");
        assertThat(Bounds.standard().calls("a browser").hostName()).isEqualTo("a browser");
    }
}
