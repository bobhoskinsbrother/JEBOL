package org.jebol.domain.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateMistrustTest {

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(delimiter = '|', value = {
            "EXPIRED           | a certificate for example.test has expired",
            "NOT_YET_VALID     | a certificate for example.test is not valid yet",
            "UNKNOWN_AUTHORITY | no trusted authority vouches for example.test",
            "BROKEN_CHAIN      | the certificate chain for example.test is broken",
            "NO_CERTIFICATE    | example.test sent no certificate",
            "WRONG_HOST        | the certificate does not name example.test",
    })
    @DisplayName("each reason is said in words with the host it was about")
    void messages(CertificateMistrust mistrust, String expected) {
        assertThat(mistrust.explainTo("example.test")).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(CertificateMistrust.class)
    @DisplayName("no two reasons read alike")
    void distinctMessages(CertificateMistrust mistrust) {
        assertThat(Arrays.stream(CertificateMistrust.values())
                .filter(other -> other.explainTo("h").equals(mistrust.explainTo("h"))))
                .containsExactly(mistrust);
    }
}
