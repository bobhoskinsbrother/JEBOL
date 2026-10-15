package org.jebol.adapter.host;

import org.jebol.domain.host.CertificateMistrust;
import org.jebol.domain.host.ChainVerdict;
import org.jebol.domain.host.VouchedNames;
import org.jebol.tls.TestCertificates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JavaCertificateAuthorityTest {

    private static final Instant FIXTURE_TIME = Instant.parse("2030-06-01T12:00:00Z");

    private final TestCertificates fixtures = new TestCertificates();

    private JavaCertificateAuthority authorityAt(Instant now) {
        return new JavaCertificateAuthority(fixtures.trusting("test-root"), Clock.fixed(now, ZoneOffset.UTC));
    }

    private ChainVerdict judge(String... chain) {
        return authorityAt(FIXTURE_TIME).judge(fixtures.ders(chain));
    }

    private ChainVerdict mistrusted(CertificateMistrust mistrust) {
        return new ChainVerdict.Mistrusted(mistrust);
    }

    private ChainVerdict trustedFor(Set<String> dnsNames, Set<String> ipAddresses) {
        return new ChainVerdict.Trusted(new VouchedNames(dnsNames, ipAddresses));
    }

    @Nested
    @DisplayName("a chain leading to a trusted root")
    class ATrustedChain {

        @Test
        @DisplayName("is trusted, with the names and addresses its first certificate vouches for")
        void trustedWithNames() {
            assertThat(judge("localhost-rsa", "test-intermediate"))
                    .isEqualTo(trustedFor(Set.of("localhost"), Set.of("127.0.0.1")));
        }

        @Test
        @DisplayName("is trusted with an elliptic curve key")
        void ellipticCurveLeaf() {
            assertThat(judge("localhost-ec", "test-intermediate"))
                    .isEqualTo(trustedFor(Set.of("localhost"), Set.of("127.0.0.1")));
        }

        @Test
        @DisplayName("is trusted with an Ed25519 key, since trusting the chain is not checking the handshake")
        void ed25519Leaf() {
            assertThat(judge("localhost-ed25519", "test-intermediate"))
                    .isEqualTo(trustedFor(Set.of("localhost"), Set.of("127.0.0.1")));
        }

        @Test
        @DisplayName("is trusted when it carries the root as well")
        void carryingTheRoot() {
            assertThat(judge("localhost-rsa", "test-intermediate", "test-root"))
                    .isEqualTo(trustedFor(Set.of("localhost"), Set.of("127.0.0.1")));
        }

        @Test
        @DisplayName("is trusted when it ends in a trusted root cross-signed by an authority nobody trusts, as a browser stops at the root")
        void crossSignedRoot() {
            assertThat(judge("localhost-rsa", "test-intermediate", "test-root-cross-signed"))
                    .isEqualTo(trustedFor(Set.of("localhost"), Set.of("127.0.0.1")));
        }

        @Test
        @DisplayName("is trusted naming nothing when its certificate has no alternative names, the common name being unread")
        void namingNothing() {
            assertThat(judge("common-name-only", "test-intermediate"))
                    .isEqualTo(trustedFor(Set.of(), Set.of()));
        }

        @Test
        @DisplayName("is trusted naming another host, since whether the names fit is not the authority's question")
        void namingAnotherHost() {
            assertThat(judge("other-host", "test-intermediate"))
                    .isEqualTo(trustedFor(Set.of("other.test"), Set.of()));
        }
    }

    @Nested
    @DisplayName("the moment of judgement, either side of a certificate's one day")
    class TheMoment {

        @ParameterizedTest(name = "at {0}: {1}")
        @CsvSource({
                "2026-12-31T23:59:59Z, NOT_YET_VALID",
                "2027-01-02T00:00:01Z, EXPIRED",
                "2040-01-01T00:00:00Z, EXPIRED",
                "2020-01-01T00:00:00Z, NOT_YET_VALID",
        })
        @DisplayName("outside it the chain is not trusted, and says which side")
        void outside(Instant now, CertificateMistrust mistrust) {
            assertThat(authorityAt(now).judge(fixtures.ders("one-day", "test-intermediate")))
                    .isEqualTo(mistrusted(mistrust));
        }

        @Test
        @DisplayName("an expired certificate under a cross-signed trusted root reads as expired, not as an unknown authority")
        void expiredUnderCrossSignedRoot() {
            assertThat(authorityAt(Instant.parse("2027-01-02T00:00:01Z"))
                    .judge(fixtures.ders("one-day", "test-intermediate", "test-root-cross-signed")))
                    .isEqualTo(mistrusted(CertificateMistrust.EXPIRED));
        }

        @ParameterizedTest(name = "at {0}")
        @CsvSource({
                "2027-01-01T00:00:00Z",
                "2027-01-01T00:00:01Z",
                "2027-01-01T12:00:00Z",
                "2027-01-01T23:59:59Z",
                "2027-01-02T00:00:00Z",
        })
        @DisplayName("from its first second to its last, both included, the chain is trusted")
        void inside(Instant now) {
            assertThat(authorityAt(now).judge(fixtures.ders("one-day", "test-intermediate")))
                    .isEqualTo(trustedFor(Set.of("localhost"), Set.of("127.0.0.1")));
        }
    }

    @Nested
    @DisplayName("a chain that is not trusted")
    class AnUntrustedChain {

        @Test
        @DisplayName("signed by itself leads to no authority")
        void selfSigned() {
            assertThat(judge("self-signed")).isEqualTo(mistrusted(CertificateMistrust.UNKNOWN_AUTHORITY));
        }

        @Test
        @DisplayName("ending at a root nobody trusts leads to no authority")
        void untrustedRoot() {
            assertThat(judge("from-a-stranger", "stranger-root"))
                    .isEqualTo(mistrusted(CertificateMistrust.UNKNOWN_AUTHORITY));
        }

        @Test
        @DisplayName("missing its intermediate leads to no authority")
        void missingIntermediate() {
            assertThat(judge("localhost-rsa")).isEqualTo(mistrusted(CertificateMistrust.UNKNOWN_AUTHORITY));
        }

        @Test
        @DisplayName("sent out of order is broken")
        void outOfOrder() {
            assertThat(judge("test-intermediate", "localhost-rsa"))
                    .isEqualTo(mistrusted(CertificateMistrust.BROKEN_CHAIN));
        }

        @Test
        @DisplayName("with an intermediate that did not sign the certificate before it is broken")
        void wrongIntermediate() {
            assertThat(judge("from-a-stranger", "test-intermediate"))
                    .isEqualTo(mistrusted(CertificateMistrust.BROKEN_CHAIN));
        }

        @Test
        @DisplayName("whose first certificate's signature was altered is broken")
        void alteredSignature() {
            byte[] altered = fixtures.der("localhost-rsa");
            altered[altered.length - 1] ^= 0x01;
            List<byte[]> chain = List.of(altered, fixtures.der("test-intermediate"));

            assertThat(authorityAt(FIXTURE_TIME).judge(chain))
                    .isEqualTo(mistrusted(CertificateMistrust.BROKEN_CHAIN));
        }

        @Test
        @DisplayName("trusted by a different authority than the host's is not trusted, which the JDK's own store shows")
        void jdkStoreRejectsTestRoot() {
            assertThat(new JavaCertificateAuthority().judge(fixtures.ders("localhost-rsa", "test-intermediate")))
                    .isEqualTo(mistrusted(CertificateMistrust.UNKNOWN_AUTHORITY));
        }
    }

    @Nested
    @DisplayName("what is not a chain of certificates at all")
    class NotAChain {

        @Test
        @DisplayName("nothing is no certificate")
        void nothing() {
            assertThat(authorityAt(FIXTURE_TIME).judge(List.of()))
                    .isEqualTo(mistrusted(CertificateMistrust.NO_CERTIFICATE));
        }

        @Test
        @DisplayName("an empty certificate is broken")
        void emptyCertificate() {
            assertThat(authorityAt(FIXTURE_TIME).judge(List.of(new byte[0])))
                    .isEqualTo(mistrusted(CertificateMistrust.BROKEN_CHAIN));
        }

        @Test
        @DisplayName("bytes that are no certificate are broken")
        void noise() {
            assertThat(authorityAt(FIXTURE_TIME)
                    .judge(List.of(new byte[] {0x30, 0x03, 0x02, 0x01, 0x05})))
                    .isEqualTo(mistrusted(CertificateMistrust.BROKEN_CHAIN));
        }

        @Test
        @DisplayName("a certificate cut short is broken")
        void cutShort() {
            byte[] whole = fixtures.der("localhost-rsa");
            List<byte[]> chain = new ArrayList<>();
            chain.add(Arrays.copyOf(whole, whole.length / 2));
            chain.add(fixtures.der("test-intermediate"));

            assertThat(authorityAt(FIXTURE_TIME).judge(chain))
                    .isEqualTo(mistrusted(CertificateMistrust.BROKEN_CHAIN));
        }
    }
}
