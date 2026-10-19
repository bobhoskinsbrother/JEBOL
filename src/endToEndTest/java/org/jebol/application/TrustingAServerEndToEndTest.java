package org.jebol.application;

import org.jebol.adapter.host.JavaCertificateAuthority;
import org.jebol.adapter.host.JavaSocket;
import org.jebol.domain.host.HostService;
import org.jebol.tls.LocalTlsServer;
import org.jebol.tls.LocalTlsServer.Identity;
import org.jebol.tls.TestCertificates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class TrustingAServerEndToEndTest {

    private static final Instant FIXTURE_TIME = Instant.parse("2030-06-01T12:00:00Z");
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(60);
    private static final String TLS_1_3 = "TLSv1.3";
    private static final String TLS_1_2 = "TLSv1.2";
    private static final String PAGE_WAS_READ = "#(true)";
    private static final String NO_CONNECT = "[Access no-connect]";

    private final TestCertificates fixtures = new TestCertificates();

    private final StringBuilder printed = new StringBuilder();

    private Identity rsaIdentity() {
        return new Identity(fixtures.chain("localhost-rsa", "test-intermediate"),
                fixtures.readTheKey("localhost-rsa-key", "RSA"));
    }

    private Identity ecIdentity() {
        return new Identity(fixtures.chain("localhost-ec", "test-intermediate"),
                fixtures.readTheKey("localhost-ec-key", "EC"));
    }

    private Identity makeAnIdentityWithAnRsaKey(String... chain) {
        return new Identity(fixtures.chain(chain), fixtures.readTheKey("localhost-rsa-key", "RSA"));
    }

    private Bounds tlsBounds() {
        return Bounds.standard()
                .withTimeLimit(HANDSHAKE_TIMEOUT)
                .grant(HostService.NETWORK)
                .grant(HostService.WEB)
                .grant(HostService.CLOCK)
                .grant(HostService.CERTIFICATES);
    }

    private Interpreter makeAnInterpreterTrustingTheTestRoot(Instant now) {
        Interpreter interpreter = makeANetworkedInterpreter(tlsBounds());
        interpreter.useCertificateAuthority(new JavaCertificateAuthority(
                fixtures.trustOnly("test-root"), Clock.fixed(now, ZoneOffset.UTC)));
        return interpreter;
    }

    private Interpreter makeANetworkedInterpreter(Bounds bounds) {
        Interpreter interpreter = Interpreter.createWithOutput(printed::append, bounds);
        interpreter.useNetwork(new JavaSocket());
        return interpreter;
    }

    private String run(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String readPage(Interpreter interpreter, String url) {
        return run(interpreter, "{" + LocalTlsServer.PAGE_BODY + "} = to string! read " + url);
    }

    private String readError(Interpreter interpreter, String url) {
        return run(interpreter, """
                failure: try [read %s]
                either error? failure [reduce [failure/type failure/id]] [{read without complaint}]
                """.formatted(url));
    }

    private String localhostUrl(LocalTlsServer server) {
        return "https://localhost:" + server.port() + "/";
    }

    private String loopbackUrl(LocalTlsServer server) {
        return "https://127.0.0.1:" + server.port() + "/";
    }

    @Nested
    @DisplayName("a server whose chain an authority the host trusts vouches for, and which names the host asked for")
    class TrustedServer {

        @ParameterizedTest(name = "over {0}")
        @ValueSource(strings = {TLS_1_3, TLS_1_2})
        @DisplayName("is read, with an RSA certificate")
        void rsa(String protocol) throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(rsaIdentity(), protocol)) {
                assertThat(readPage(makeAnInterpreterTrustingTheTestRoot(FIXTURE_TIME), localhostUrl(server))).isEqualTo(PAGE_WAS_READ);
            }
        }

        @ParameterizedTest(name = "over {0}")
        @ValueSource(strings = {TLS_1_3, TLS_1_2})
        @DisplayName("is read, with an elliptic curve certificate, which a real 3.22.5 cannot do over TLS 1.2")
        void ellipticCurve(String protocol) throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(ecIdentity(), protocol)) {
                assertThat(readPage(makeAnInterpreterTrustingTheTestRoot(FIXTURE_TIME), localhostUrl(server))).isEqualTo(PAGE_WAS_READ);
            }
        }

        @Test
        @DisplayName("is read when the url names it by its address and the certificate names that address")
        void isReadByAddress() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(rsaIdentity(), TLS_1_3)) {
                assertThat(readPage(makeAnInterpreterTrustingTheTestRoot(FIXTURE_TIME), loopbackUrl(server))).isEqualTo(PAGE_WAS_READ);
            }
        }

        @Test
        @DisplayName("is read when the server also sends the root itself")
        void chainIncludingRoot() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(
                    makeAnIdentityWithAnRsaKey("localhost-rsa", "test-intermediate", "test-root"), TLS_1_3)) {
                assertThat(readPage(makeAnInterpreterTrustingTheTestRoot(FIXTURE_TIME), localhostUrl(server))).isEqualTo(PAGE_WAS_READ);
            }
        }
    }

    @Nested
    @DisplayName("a server that is not trusted ends the handshake before any request is written")
    class UntrustedServer {

        private void assertRefused(LocalTlsServer server, Interpreter interpreter, String url, String reason)
                throws InterruptedException {

            assertThat(readError(interpreter, url)).isEqualTo(NO_CONNECT);
            server.awaitAllConnectionsClosed(HANDSHAKE_TIMEOUT);
            assertThat(server.connectionsAccepted()).isPositive();
            assertThat(server.requestsReceived()).isZero();
            assertThat(printed.toString()).contains(reason);
        }

        @Test
        @DisplayName("when its certificate has expired")
        void isRefusedWhenExpired() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(makeAnIdentityWithAnRsaKey("one-day", "test-intermediate"), TLS_1_3)) {
                assertRefused(server, makeAnInterpreterTrustingTheTestRoot(Instant.parse("2027-01-02T00:00:01Z")),
                        localhostUrl(server), "a certificate for localhost has expired");
            }
        }

        @Test
        @DisplayName("when its certificate is not valid yet")
        void isRefusedWhenNotYetValid() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(makeAnIdentityWithAnRsaKey("one-day", "test-intermediate"), TLS_1_3)) {
                assertRefused(server, makeAnInterpreterTrustingTheTestRoot(Instant.parse("2026-12-31T23:59:59Z")),
                        localhostUrl(server), "a certificate for localhost is not valid yet");
            }
        }

        @Test
        @DisplayName("when its certificate signed itself")
        void selfSigned() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(makeAnIdentityWithAnRsaKey("self-signed"), TLS_1_3)) {
                assertRefused(server, makeAnInterpreterTrustingTheTestRoot(FIXTURE_TIME),
                        localhostUrl(server), "no trusted authority vouches for localhost");
            }
        }

        @Test
        @DisplayName("when its chain leads to a root the host does not trust")
        void isRefusedForAnUntrustedRoot() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(
                    makeAnIdentityWithAnRsaKey("from-a-stranger", "stranger-root"), TLS_1_3)) {
                assertRefused(server, makeAnInterpreterTrustingTheTestRoot(FIXTURE_TIME),
                        localhostUrl(server), "no trusted authority vouches for localhost");
            }
        }

        @Test
        @DisplayName("when its trusted certificate names another host")
        void wrongHost() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(makeAnIdentityWithAnRsaKey("other-host", "test-intermediate"), TLS_1_3)) {
                assertRefused(server, makeAnInterpreterTrustingTheTestRoot(FIXTURE_TIME),
                        localhostUrl(server), "the certificate does not name localhost");
            }
        }

        @Test
        @DisplayName("when the host is named only in the subject's common name, which is never read")
        void commonNameOnly() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(
                    makeAnIdentityWithAnRsaKey("common-name-only", "test-intermediate"), TLS_1_3)) {
                assertRefused(server, makeAnInterpreterTrustingTheTestRoot(FIXTURE_TIME),
                        localhostUrl(server), "the certificate does not name localhost");
            }
        }

        @Test
        @DisplayName("when the url names an address the certificate does not")
        void wrongAddress() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(makeAnIdentityWithAnRsaKey("other-host", "test-intermediate"), TLS_1_3)) {
                assertRefused(server, makeAnInterpreterTrustingTheTestRoot(FIXTURE_TIME),
                        loopbackUrl(server), "the certificate does not name 127.0.0.1");
            }
        }

        @Test
        @DisplayName("when the chain leaves out the intermediate authority")
        void isRefusedWithoutTheIntermediate() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(makeAnIdentityWithAnRsaKey("localhost-rsa"), TLS_1_3)) {
                assertRefused(server, makeAnInterpreterTrustingTheTestRoot(FIXTURE_TIME),
                        localhostUrl(server), "no trusted authority vouches for localhost");
            }
        }

        @Test
        @DisplayName("when the host granted certificates and supplied no authority, which trusts nothing")
        void isRefusedWithNoAuthoritySupplied() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(rsaIdentity(), TLS_1_3)) {
                assertRefused(server, makeANetworkedInterpreter(tlsBounds()),
                        localhostUrl(server), "no trusted authority vouches for localhost");
            }
        }
    }

    @Nested
    @DisplayName("a server sending a real chain whose key it does not hold, as anyone in between could")
    class Impostor {

        private void assertRefused(Identity identity, String protocol) throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(identity, protocol)) {
                assertThat(readError(makeAnInterpreterTrustingTheTestRoot(FIXTURE_TIME), localhostUrl(server))).isEqualTo(NO_CONNECT);
                server.awaitAllConnectionsClosed(HANDSHAKE_TIMEOUT);
                assertThat(server.requestsReceived()).isZero();
            }
        }

        @ParameterizedTest(name = "over {0}")
        @ValueSource(strings = {TLS_1_3, TLS_1_2})
        @DisplayName("is refused, with an RSA certificate")
        void rsa(String protocol) throws Exception {
            assertRefused(new Identity(
                    fixtures.chain("localhost-rsa", "test-intermediate"),
                    fixtures.readTheKey("impostor-rsa-key", "RSA")), protocol);
        }

        @ParameterizedTest(name = "over {0}")
        @ValueSource(strings = {TLS_1_3, TLS_1_2})
        @DisplayName("is refused, with an elliptic curve certificate")
        void ellipticCurve(String protocol) throws Exception {
            assertRefused(new Identity(
                    fixtures.chain("localhost-ec", "test-intermediate"),
                    fixtures.readTheKey("impostor-ec-key", "EC")), protocol);
        }

        @Test
        @DisplayName("is refused even holding the key, when it signs with Ed25519, which nothing here can check")
        void uncheckableSignature() throws Exception {
            assertRefused(new Identity(
                    fixtures.chain("localhost-ed25519", "test-intermediate"),
                    fixtures.readTheKey("localhost-ed25519-key", "Ed25519")), TLS_1_3);
        }
    }

    @Nested
    @DisplayName("the certificates service")
    class CertificatesService {

        @Test
        @DisplayName("is not granted by the standard bounds, so a secure connection is refused before it is opened")
        void isNotGrantedByDefault() throws Exception {
            Bounds withoutCertificates = Bounds.standard()
                    .withTimeLimit(HANDSHAKE_TIMEOUT)
                    .grant(HostService.NETWORK)
                    .grant(HostService.WEB)
                    .grant(HostService.CLOCK);
            try (LocalTlsServer server = new LocalTlsServer(rsaIdentity(), TLS_1_3)) {
                Interpreter interpreter = makeANetworkedInterpreter(withoutCertificates);
                interpreter.useCertificateAuthority(new JavaCertificateAuthority(
                        fixtures.trustOnly("test-root"), Clock.fixed(FIXTURE_TIME, ZoneOffset.UTC)));

                assertThat(readError(interpreter, localhostUrl(server))).isEqualTo("[Access no-service]");
                assertThat(run(interpreter, """
                        failure: try [read %s]
                        {certificates is not granted} = form failure/arg1""".formatted(localhostUrl(server))))
                        .isEqualTo("#(true)");
                assertThat(server.connectionsAccepted()).isZero();
            }
        }

        @Test
        @DisplayName("is not asked for by a server listening for connections, which is sent no certificate to judge")
        void isNotAskedForByAListeningServer() {
            Interpreter interpreter = makeANetworkedInterpreter(
                    Bounds.standard().grant(HostService.NETWORK).grant(HostService.CLOCK));
            assertThat(run(interpreter, """
                    listening: open tls://:0
                    close listening/state/tcp-port
                    port? listening""")).isEqualTo("#(true)");
        }
    }
}
