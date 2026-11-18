package org.jebol.android.unit;

import org.jebol.adapter.android.AndroidHostNames;
import org.jebol.domain.host.HostNameCases;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AndroidHostNamesTest {

    private final AndroidHostNames hostNames = new AndroidHostNames();

    @Test
    @DisplayName("a host name converts as a browser converts it, and one a domain cannot hold is refused, as on the desktop")
    void convertsHostNamesAsTheDesktopDoes() {
        assertThat(HostNameCases.disagreementsWith(hostNames)).isEmpty();
    }

    @Test
    @DisplayName("Unicode's conformance file converts as on the desktop, but for the cases docs/android-port.md lists")
    void convertsTheConformanceFileButForTheListedCases() {
        List<String> disagreements = HostNameCases.conformanceDisagreementsWith(hostNames::toAscii);

        assertThat(disagreements).containsExactlyInAnyOrderElementsOf(IcuDiffersFromUts46.CASES);
    }
}
