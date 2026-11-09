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
        return new JavaCertificateAuthority(fixtures.trustOnly("test-root"), Clock.fixed(now, ZoneOffset.UTC));
    }

    private ChainVerdict judge(String... chain) {
        return authorityAt(FIXTURE_TIME).judge(fixtures.readTheDers(chain));
    }

    private ChainVerdict expectMistrust(CertificateMistrust mistrust) {
        return new ChainVerdict.Mistrusted(mistrust);
    }

    private ChainVerdict expectTrustFor(Set<String> dnsNames, Set<String> ipAddresses) {
        return new ChainVerdict.Trusted(new VouchedNames(dnsNames, ipAddresses));
    }

    @Nested
    @DisplayName("a chain leading to a trusted root")
    class ATrustedChain {

        @Test
        @DisplayName("is trusted, with the names and addresses its first certificate vouches for")
        void trustsWithNames() {
            assertThat(judge("localhost-rsa", "test-intermediate"))
                    .isEqualTo(expectTrustFor(Set.of("localhost"), Set.of("127.0.0.1")));
        }

        @Test
        @DisplayName("is trusted with an elliptic curve key")
        void ellipticCurveLeaf() {
            assertThat(judge("localhost-ec", "test-intermediate"))
                    .isEqualTo(expectTrustFor(Set.of("localhost"), Set.of("127.0.0.1")));
        }

        @Test
        @DisplayName("is trusted with an Ed25519 key, since trusting the chain is not checking the handshake")
        void ed25519Leaf() {
            assertThat(judge("localhost-ed25519", "test-intermediate"))
                    .isEqualTo(expectTrustFor(Set.of("localhost"), Set.of("127.0.0.1")));
        }

        @Test
        @DisplayName("is trusted when it carries the root as well")
        void carriesTheRoot() {
            assertThat(judge("localhost-rsa", "test-intermediate", "test-root"))
                    .isEqualTo(expectTrustFor(Set.of("localhost"), Set.of("127.0.0.1")));
        }

        @Test
        @DisplayName("is trusted when it ends in a trusted root cross-signed by an authority nobody trusts, as a browser stops at the root")
        void crossSignedRoot() {
            assertThat(judge("localhost-rsa", "test-intermediate", "test-root-cross-signed"))
                    .isEqualTo(expectTrustFor(Set.of("localhost"), Set.of("127.0.0.1")));
        }

        @Test
        @DisplayName("is trusted naming nothing when its certificate has no alternative names, the common name being unread")
        void namesNothing() {
            assertThat(judge("common-name-only", "test-intermediate"))
                    .isEqualTo(expectTrustFor(Set.of(), Set.of()));
        }

        @Test
        @DisplayName("is trusted naming another host, since whether the names fit is not the authority's question")
        void namesAnotherHost() {
            assertThat(judge("other-host", "test-intermediate"))
                    .isEqualTo(expectTrustFor(Set.of("other.test"), Set.of()));
        }
    }

    @Nested
    @DisplayName("a chain sent as a bag, its leaf first, through which a path is built")
    class ABagOfCertificates {

        private final ChainVerdict trustedForLocalhost = expectTrustFor(Set.of("localhost"), Set.of("127.0.0.1"));

        @Test
        @DisplayName("is trusted with its authorities in reverse")
        void reversed() {
            assertThat(judge("localhost-rsa", "test-root", "test-intermediate")).isEqualTo(trustedForLocalhost);
        }

        @Test
        @DisplayName("is trusted with its leaf sent twice, as münchen.de sends it")
        void leafTwice() {
            assertThat(judge("localhost-rsa", "localhost-rsa", "test-intermediate")).isEqualTo(trustedForLocalhost);
        }

        @Test
        @DisplayName("is trusted carrying a certificate that belongs to nobody in it")
        void unrelatedExtra() {
            assertThat(judge("localhost-rsa", "stranger-root", "test-intermediate")).isEqualTo(trustedForLocalhost);
        }

        @ParameterizedTest(name = "{0} then {1}")
        @CsvSource({
                "test-intermediate-expired, test-intermediate",
                "test-intermediate, test-intermediate-expired",
        })
        @DisplayName("is trusted carrying an expired and a valid copy of one intermediate, in either order")
        void twoCopiesOfAnIntermediate(String first, String second) {
            assertThat(judge("localhost-rsa", first, second)).isEqualTo(trustedForLocalhost);
        }

        @Test
        @DisplayName("is trusted five authorities deep, as many as a path may hold")
        void fiveDeep() {
            assertThat(judge("five-deep", "deep-5", "deep-4", "deep-3", "deep-2", "deep-1"))
                    .isEqualTo(trustedForLocalhost);
        }

        @Test
        @DisplayName("is trusted five authorities deep sent in reverse")
        void fiveDeepReversed() {
            assertThat(judge("five-deep", "deep-1", "deep-2", "deep-3", "deep-4", "deep-5"))
                    .isEqualTo(trustedForLocalhost);
        }

        @Test
        @DisplayName("is trusted carrying bytes that are no certificate, which the path never uses")
        void unreadableExtra() {
            List<byte[]> chain = List.of(
                    fixtures.readTheDer("localhost-rsa"),
                    new byte[] {0x30, 0x03, 0x02, 0x01, 0x05},
                    fixtures.readTheDer("test-intermediate"));

            assertThat(authorityAt(FIXTURE_TIME).judge(chain)).isEqualTo(trustedForLocalhost);
        }

        @Test
        @DisplayName("is trusted when its path ends at an intermediate the host was given as an authority")
        void endsAtASuppliedIntermediate() {
            JavaCertificateAuthority trustingTheIntermediate = new JavaCertificateAuthority(
                    fixtures.trustOnly("test-intermediate"), Clock.fixed(FIXTURE_TIME, ZoneOffset.UTC));

            assertThat(trustingTheIntermediate.judge(fixtures.readTheDers("localhost-rsa")))
                    .isEqualTo(trustedForLocalhost);
        }

        @Test
        @DisplayName("is trusted as a leaf alone that the host was given as an authority")
        void aLeafTheHostTrusts() {
            JavaCertificateAuthority trustingTheLeaf = new JavaCertificateAuthority(
                    fixtures.trustOnly("self-signed"), Clock.fixed(FIXTURE_TIME, ZoneOffset.UTC));

            assertThat(trustingTheLeaf.judge(fixtures.readTheDers("self-signed")))
                    .isEqualTo(trustedForLocalhost);
        }
    }

    @Nested
    @DisplayName("the reason given when no path is trusted, the dates first")
    class TheReason {

        @Test
        @DisplayName("is expired for an expired leaf whose authority the host does not trust, as OpenSSL ends")
        void expiredLeafUnknownAuthority() {
            JavaCertificateAuthority trustingAStranger = new JavaCertificateAuthority(
                    fixtures.trustOnly("stranger-root"), Clock.fixed(FIXTURE_TIME, ZoneOffset.UTC));

            assertThat(trustingAStranger.judge(fixtures.readTheDers("one-day", "test-intermediate")))
                    .isEqualTo(expectMistrust(CertificateMistrust.EXPIRED));
        }

        @Test
        @DisplayName("is expired for an expired self-signed leaf")
        void expiredSelfSigned() {
            assertThat(authorityAt(Instant.parse("2040-01-01T00:00:00Z")).judge(fixtures.readTheDers("self-signed")))
                    .isEqualTo(expectMistrust(CertificateMistrust.EXPIRED));
        }

        @Test
        @DisplayName("is expired for a leaf in date whose only intermediate has expired")
        void expiredIntermediate() {
            assertThat(judge("through-an-expired-intermediate", "test-intermediate-expired"))
                    .isEqualTo(expectMistrust(CertificateMistrust.EXPIRED));
        }

        @Test
        @DisplayName("is not yet valid for a leaf that has not begun, whose authority the host does not trust")
        void notYetValidLeafUnknownAuthority() {
            JavaCertificateAuthority trustingAStranger = new JavaCertificateAuthority(
                    fixtures.trustOnly("stranger-root"), Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC));

            assertThat(trustingAStranger.judge(fixtures.readTheDers("one-day", "test-intermediate")))
                    .isEqualTo(expectMistrust(CertificateMistrust.NOT_YET_VALID));
        }

        @Test
        @DisplayName("is no authority for a leaf in date that no trusted root vouches for at any moment")
        void unknownAuthorityInDate() {
            assertThat(judge("from-a-stranger", "stranger-root"))
                    .isEqualTo(expectMistrust(CertificateMistrust.UNKNOWN_AUTHORITY));
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
        void fallsOutside(Instant now, CertificateMistrust mistrust) {
            assertThat(authorityAt(now).judge(fixtures.readTheDers("one-day", "test-intermediate")))
                    .isEqualTo(expectMistrust(mistrust));
        }

        @Test
        @DisplayName("an expired certificate under a cross-signed trusted root reads as expired, not as an unknown authority")
        void expiresUnderACrossSignedRoot() {
            assertThat(authorityAt(Instant.parse("2027-01-02T00:00:01Z"))
                    .judge(fixtures.readTheDers("one-day", "test-intermediate", "test-root-cross-signed")))
                    .isEqualTo(expectMistrust(CertificateMistrust.EXPIRED));
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
        void fallsInside(Instant now) {
            assertThat(authorityAt(now).judge(fixtures.readTheDers("one-day", "test-intermediate")))
                    .isEqualTo(expectTrustFor(Set.of("localhost"), Set.of("127.0.0.1")));
        }
    }

    @Nested
    @DisplayName("a chain that is not trusted")
    class AnUntrustedChain {

        @Test
        @DisplayName("signed by itself leads to no authority")
        void selfSigned() {
            assertThat(judge("self-signed")).isEqualTo(expectMistrust(CertificateMistrust.UNKNOWN_AUTHORITY));
        }

        @Test
        @DisplayName("ending at a root nobody trusts leads to no authority")
        void endsAtAnUntrustedRoot() {
            assertThat(judge("from-a-stranger", "stranger-root"))
                    .isEqualTo(expectMistrust(CertificateMistrust.UNKNOWN_AUTHORITY));
        }

        @Test
        @DisplayName("missing its intermediate leads to no authority")
        void missesAnIntermediate() {
            assertThat(judge("localhost-rsa")).isEqualTo(expectMistrust(CertificateMistrust.UNKNOWN_AUTHORITY));
        }

        @Test
        @DisplayName("whose first certificate is an authority rather than a leaf is broken")
        void leafNotFirst() {
            assertThat(judge("test-intermediate", "localhost-rsa"))
                    .isEqualTo(expectMistrust(CertificateMistrust.BROKEN_CHAIN));
        }

        @Test
        @DisplayName("carrying an intermediate that signed nothing in it leads to no authority, the intermediate being an unused extra")
        void wrongIntermediate() {
            assertThat(judge("from-a-stranger", "test-intermediate"))
                    .isEqualTo(expectMistrust(CertificateMistrust.UNKNOWN_AUTHORITY));
        }

        @Test
        @DisplayName("through a certificate with the intermediate's name and key that is no authority leads to no authority")
        void throughACertificateThatIsNoAuthority() {
            assertThat(judge("localhost-rsa", "not-an-authority"))
                    .isEqualTo(expectMistrust(CertificateMistrust.UNKNOWN_AUTHORITY));
        }

        @Test
        @DisplayName("through two authorities that signed each other, neither trusted, leads to no authority and ends")
        void throughACycle() {
            assertThat(judge("in-a-cycle", "cycle-a-by-b", "cycle-b"))
                    .isEqualTo(expectMistrust(CertificateMistrust.UNKNOWN_AUTHORITY));
        }

        @Test
        @DisplayName("six authorities deep, one more than a path may hold, leads to no authority")
        void sixDeep() {
            assertThat(judge("six-deep", "deep-6", "deep-5", "deep-4", "deep-3", "deep-2", "deep-1"))
                    .isEqualTo(expectMistrust(CertificateMistrust.UNKNOWN_AUTHORITY));
        }

        @Test
        @DisplayName("whose first certificate's signature was altered is broken")
        void carriesAnAlteredSignature() {
            byte[] altered = fixtures.readTheDer("localhost-rsa");
            altered[altered.length - 1] ^= 0x01;
            List<byte[]> chain = List.of(altered, fixtures.readTheDer("test-intermediate"));

            assertThat(authorityAt(FIXTURE_TIME).judge(chain))
                    .isEqualTo(expectMistrust(CertificateMistrust.BROKEN_CHAIN));
        }

        @Test
        @DisplayName("trusted by a different authority than the host's is not trusted, which the JDK's own store shows")
        void failsAgainstTheJdkStore() {
            assertThat(new JavaCertificateAuthority().judge(fixtures.readTheDers("localhost-rsa", "test-intermediate")))
                    .isEqualTo(expectMistrust(CertificateMistrust.UNKNOWN_AUTHORITY));
        }
    }

    @Nested
    @DisplayName("what is not a chain of certificates at all")
    class NotAChain {

        @Test
        @DisplayName("nothing is no certificate")
        void refusesNothing() {
            assertThat(authorityAt(FIXTURE_TIME).judge(List.of()))
                    .isEqualTo(expectMistrust(CertificateMistrust.NO_CERTIFICATE));
        }

        @Test
        @DisplayName("an empty certificate is broken")
        void refusesAnEmptyCertificate() {
            assertThat(authorityAt(FIXTURE_TIME).judge(List.of(new byte[0])))
                    .isEqualTo(expectMistrust(CertificateMistrust.BROKEN_CHAIN));
        }

        @Test
        @DisplayName("bytes that are no certificate are broken")
        void refusesNoise() {
            assertThat(authorityAt(FIXTURE_TIME)
                    .judge(List.of(new byte[] {0x30, 0x03, 0x02, 0x01, 0x05})))
                    .isEqualTo(expectMistrust(CertificateMistrust.BROKEN_CHAIN));
        }

        @Test
        @DisplayName("a certificate cut short is broken")
        void cutShort() {
            byte[] whole = fixtures.readTheDer("localhost-rsa");
            List<byte[]> chain = new ArrayList<>();
            chain.add(Arrays.copyOf(whole, whole.length / 2));
            chain.add(fixtures.readTheDer("test-intermediate"));

            assertThat(authorityAt(FIXTURE_TIME).judge(chain))
                    .isEqualTo(expectMistrust(CertificateMistrust.BROKEN_CHAIN));
        }
    }
}
