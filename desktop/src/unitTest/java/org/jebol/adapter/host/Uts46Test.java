package org.jebol.adapter.host;

import org.jebol.domain.host.HostNameCases;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Uts46Test {

    @Test
    @DisplayName("Unicode's conformance file is the one it says: version 16.0.0, with 6,389 cases")
    void theConformanceFileIsTheFileItSaysItIs() {
        assertThat(HostNameCases.readConformanceFile()).contains(HostNameCases.CONFORMANCE_VERSION);
        assertThat(HostNameCases.readConformanceCases()).hasSize(HostNameCases.CONFORMANCE_CASES);
    }

    @Test
    @DisplayName("every case of the conformance file converts without transitional processing, the checks a browser leaves off aside")
    void convertsEveryConformanceCase() {
        assertThat(HostNameCases.conformanceDisagreementsWith(Uts46::toAscii)).isEmpty();
    }

    @Test
    @DisplayName("a host name converts as a browser converts it, and one a domain cannot hold is refused")
    void convertsHostNamesAsABrowserDoes() {
        assertThat(HostNameCases.disagreementsWith(Uts46::toAsciiHostName)).isEmpty();
    }
}
