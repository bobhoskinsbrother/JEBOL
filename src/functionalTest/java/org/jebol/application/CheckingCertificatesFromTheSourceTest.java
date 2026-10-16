package org.jebol.application;

import org.jebol.adapter.host.JavaCertificateAuthority;
import org.jebol.domain.host.HostService;
import org.jebol.tls.TestCertificates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class CheckingCertificatesFromTheSourceTest {

    private static final Instant FIXTURE_TIME = Instant.parse("2030-06-01T12:00:00Z");

    private final TestCertificates fixtures = new TestCertificates();

    private final StringBuilder printed = new StringBuilder();

    private Interpreter granted() {
        Interpreter interpreter = grantedWithoutAuthority();
        interpreter.useCertificateAuthority(new JavaCertificateAuthority(
                fixtures.trusting("test-root"), Clock.fixed(FIXTURE_TIME, ZoneOffset.UTC)));
        return interpreter;
    }

    private Interpreter grantedWithoutAuthority() {
        return Interpreter.writingTo(printed::append, Bounds.standard().grant(HostService.CERTIFICATES));
    }

    private Interpreter ungranted() {
        return Interpreter.writingTo(printed::append, Bounds.standard());
    }

    private String print(Interpreter interpreter, String source) {
        String printing = "print " + source;
        interpreter.defineFreshWordsIn(printing);
        interpreter.run(printing);
        return printed.toString().strip();
    }

    private String outcome(Interpreter interpreter, String source) {
        return print(interpreter, """
                either error? failure: try [%s] [rejoin [failure/type { } failure/id { | } failure/arg1]] [
                    either none? :failure [{none}] [mold :failure]
                ]""".formatted(source));
    }

    private String binaryLiteral(String fixture) {
        return "#{" + HexFormat.of().formatHex(fixtures.der(fixture)) + "}";
    }

    private String chainOf(String... fixtureNames) {
        return "reduce [" + String.join(" ", Arrays.stream(fixtureNames).map(this::binaryLiteral).toList()) + "]";
    }

    private String trustedChain() {
        return chainOf("localhost-rsa", "test-intermediate");
    }

    @Nested
    @DisplayName("require-certificate-checks")
    class RequireCertificateChecks {

        @Test
        @DisplayName("answers none when the service is granted")
        void granted() {
            assertThat(outcome(CheckingCertificatesFromTheSourceTest.this.granted(), "require-certificate-checks"))
                    .isEqualTo("none");
        }

        @Test
        @DisplayName("is refused, naming the service, when it is not")
        void ungrantedIsRefused() {
            assertThat(outcome(ungranted(), "require-certificate-checks"))
                    .isEqualTo("Access no-service | certificates is not granted");
        }

        @Test
        @DisplayName("is refused as not present by a host that has no authorities to offer")
        void lackingIsNotPresent() {
            Interpreter browser = Interpreter.writingTo(printed::append, Bounds.standard()
                    .lacking(HostService.CERTIFICATES).calls("a browser"));
            assertThat(outcome(browser, "require-certificate-checks"))
                    .isEqualTo("Access no-service | a browser does not allow certificates, which is not present");
        }
    }

    @Nested
    @DisplayName("check-certificates")
    class CheckCertificates {

        @Test
        @DisplayName("answers none for a trusted chain that names the host")
        void trustedChainNamingHost() {
            assertThat(outcome(granted(), "check-certificates " + trustedChain() + " {localhost}"))
                    .isEqualTo("none");
        }

        @ParameterizedTest(name = "the host {0}")
        @ValueSource(strings = {"127.0.0.1", "{127.0.0.1}"})
        @DisplayName("takes the host as an address, as a url's spec holds one, or as text")
        void hostAsAddress(String host) {
            assertThat(outcome(granted(), "check-certificates " + trustedChain() + " " + host))
                    .isEqualTo("none");
        }

        @ParameterizedTest(name = "{0}, for {1}")
        @CsvSource(delimiter = '|', value = {
                "localhost-rsa test-intermediate | other.test | the certificate does not name other.test",
                "localhost-rsa test-intermediate | 127.0.0.2  | the certificate does not name 127.0.0.2",
                "self-signed                     | localhost  | no trusted authority vouches for localhost",
                "test-intermediate localhost-rsa | localhost  | the certificate chain for localhost is broken",
        })
        @DisplayName("raises a protocol error saying why a chain is not trusted")
        void untrustedRaises(String chain, String host, String reason) {
            assertThat(outcome(granted(), "check-certificates " + chainOf(chain.split(" ")) + " {" + host + "}"))
                    .isEqualTo("Access protocol | " + reason);
        }

        @Test
        @DisplayName("raises no certificate for an empty chain")
        void emptyChain() {
            assertThat(outcome(granted(), "check-certificates [] {localhost}"))
                    .isEqualTo("Access protocol | localhost sent no certificate");
        }

        @Test
        @DisplayName("raises a broken chain for bytes that are no certificate")
        void noise() {
            assertThat(outcome(granted(), "check-certificates [#{300302010500}] {localhost}"))
                    .isEqualTo("Access protocol | the certificate chain for localhost is broken");
        }

        @Test
        @DisplayName("judges with no authority as trusting nothing when the host supplied none")
        void noAuthoritySupplied() {
            assertThat(outcome(grantedWithoutAuthority(), "check-certificates " + trustedChain() + " {localhost}"))
                    .isEqualTo("Access protocol | no trusted authority vouches for localhost");
        }

        @Test
        @DisplayName("is refused, naming the service, when it is not granted")
        void ungrantedIsRefused() {
            assertThat(outcome(ungranted(), "check-certificates " + trustedChain() + " {localhost}"))
                    .isEqualTo("Access no-service | certificates is not granted");
        }

        @ParameterizedTest(name = "a chain of {0}")
        @ValueSource(strings = {"5", "{text}", "#{00}", "none", "1.5", "true"})
        @DisplayName("refuses a chain that is not a block")
        void chainOfWrongType(String chain) {
            assertThat(outcome(granted(), "check-certificates " + chain + " {localhost}"))
                    .startsWith("Script expect-arg | check-certificates");
        }

        @ParameterizedTest(name = "a host of {0}")
        @ValueSource(strings = {"5", "#{00}", "none", "1.5", "true", "[localhost]", "'localhost"})
        @DisplayName("refuses a host that is neither text nor an address")
        void hostOfWrongType(String host) {
            assertThat(outcome(granted(), "check-certificates [] " + host))
                    .startsWith("Script expect-arg | check-certificates");
        }

        @ParameterizedTest(name = "a block holding {0}")
        @ValueSource(strings = {"5", "{text}", "none", "[#{00}]"})
        @DisplayName("refuses a chain holding anything but binary")
        void chainItemOfWrongType(String item) {
            assertThat(outcome(granted(), "check-certificates reduce [" + item + "] {localhost}"))
                    .startsWith("Script invalid-arg | ");
        }
    }
}
