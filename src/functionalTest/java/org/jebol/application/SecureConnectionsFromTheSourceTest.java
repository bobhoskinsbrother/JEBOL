package org.jebol.application;

import org.jebol.adapter.host.JavaCertificateAuthority;
import org.jebol.adapter.host.JavaSocket;
import org.jebol.domain.host.CertificateAuthority;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.SecureTransport;
import org.jebol.domain.host.ZeroTrustAuthority;
import org.jebol.tls.LocalTlsServer;
import org.jebol.tls.LocalTlsServer.Identity;
import org.jebol.tls.TestCertificates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class SecureConnectionsFromTheSourceTest {

    private static final Instant FIXTURE_TIME = Instant.parse("2030-06-01T12:00:00Z");
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(60);
    private static final String TLS_1_3 = "TLSv1.3";

    private final TestCertificates fixtures = new TestCertificates();

    private final StringBuilder printed = new StringBuilder();

    private Bounds grantingEverythingSecure() {
        return Bounds.standard()
                .withTimeLimit(HANDSHAKE_TIMEOUT)
                .grant(HostService.NETWORK)
                .grant(HostService.WEB)
                .grant(HostService.CLOCK)
                .grant(HostService.CERTIFICATES);
    }

    private CertificateAuthority trustingTheTestRoot() {
        return new JavaCertificateAuthority(fixtures.trustOnly("test-root"), Clock.fixed(FIXTURE_TIME, ZoneOffset.UTC));
    }

    private Interpreter makeAnInterpreter(CertificateAuthority authority, Bounds bounds) {
        return Interpreter.createOn(
                AnInterpreter.standardPorts().withNetwork(new JavaSocket()).withCertificateAuthority(authority),
                printed::append,
                bounds);
    }

    private Interpreter makeAnInterpreterTrustingTheTestRoot() {
        return makeAnInterpreter(trustingTheTestRoot(), grantingEverythingSecure());
    }

    private Identity identityWithTheRsaKey(String... chain) {
        return new Identity(fixtures.chain(chain), fixtures.readTheKey("localhost-rsa-key", "RSA"));
    }

    private String run(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String openAndSayWhatHappened(Interpreter interpreter, String host, int port) {
        String shown = run(interpreter, """
                happened: copy []
                reason: none
                body: copy {}
                outcome: try [
                    secure: open tls://%s:%d
                    secure/awake: func [event] [
                        append happened event/type
                        switch event/type [
                            connect [write event/port {GET / HTTP/1.0^M^/^M^/}]
                            wrote [read event/port]
                            read [read event/port]
                            error [reason: form event/port/extra/error/arg1 return true]
                            close [body: to string! event/port/data return true]
                        ]
                        false
                    ]
                    wait [secure 30]
                    close secure
                ]
                either error? outcome [rejoin [outcome/type { } outcome/id { | } form outcome/arg1]] [
                    rejoin [form happened { | } any [reason find/tail body {^M^/^M^/} body]]
                ]""".formatted(host, port));
        return shown.substring(1, shown.length() - 1);
    }

    @Nested
    @DisplayName("opening a secure connection")
    class Opening {

        @Test
        @DisplayName("asks for the certificates service before a byte is sent, and is refused without it")
        void refusedWithoutTheGrant() throws Exception {
            Bounds withoutCertificates = Bounds.standard()
                    .withTimeLimit(HANDSHAKE_TIMEOUT)
                    .grant(HostService.NETWORK)
                    .grant(HostService.CLOCK);
            try (LocalTlsServer server = new LocalTlsServer(identityWithTheRsaKey("localhost-rsa", "test-intermediate"), TLS_1_3)) {
                assertThat(openAndSayWhatHappened(makeAnInterpreter(trustingTheTestRoot(), withoutCertificates),
                        "localhost", server.port()))
                        .isEqualTo("Access no-service | certificates is not granted");
                assertThat(server.connectionsAccepted()).isZero();
            }
        }

        @Test
        @DisplayName("is refused as not present by a host that has no authorities to offer")
        void refusedWhereUnavailable() {
            Bounds asABrowser = Bounds.standard()
                    .withTimeLimit(HANDSHAKE_TIMEOUT)
                    .grant(HostService.NETWORK)
                    .grant(HostService.CLOCK)
                    .markUnavailable(HostService.CERTIFICATES)
                    .calls("a browser");
            assertThat(openAndSayWhatHappened(makeAnInterpreter(trustingTheTestRoot(), asABrowser), "localhost", 443))
                    .isEqualTo("Access no-service | a browser does not allow certificates, which is not present");
        }

        @Test
        @DisplayName("is refused by a host that supplied no TLS of its own, before a byte is sent")
        void refusedWithoutATransport() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(identityWithTheRsaKey("localhost-rsa", "test-intermediate"), TLS_1_3)) {
                Interpreter withoutTransport = Interpreter.createOn(
                        AnInterpreter.standardPorts()
                                .withNetwork(new JavaSocket())
                                .withCertificateAuthority(trustingTheTestRoot())
                                .withSecureTransport(SecureTransport.none()),
                        printed::append,
                        grantingEverythingSecure());

                assertThat(openAndSayWhatHappened(withoutTransport, "localhost", server.port()))
                        .startsWith("Access no-port | ");
                assertThat(server.connectionsAccepted()).isZero();
            }
        }
    }

    @Nested
    @DisplayName("a server the host trusts")
    class ATrustedServer {

        @Test
        @DisplayName("connects, takes what the script writes and hands back what it answers, as R3's events say")
        void connectsAndConverses() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(identityWithTheRsaKey("localhost-rsa", "test-intermediate"), TLS_1_3)) {
                String happened = openAndSayWhatHappened(makeAnInterpreterTrustingTheTestRoot(), "localhost", server.port());

                assertThat(happened).startsWith("connect wrote read ").endsWith("close | " + LocalTlsServer.PAGE_BODY);
                assertThat(server.requestsReceived()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("keeps the fields R3's protocols read: the connection under it and the port over it")
        void keepsRsFields() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(identityWithTheRsaKey("localhost-rsa", "test-intermediate"), TLS_1_3)) {
                assertThat(run(makeAnInterpreterTrustingTheTestRoot(), """
                        secure: open tls://localhost:%d
                        wait [secure 30]
                        also reduce [
                            secure/extra/tcp-port/scheme/name
                            same? secure secure/extra/tls-port
                            same? secure secure/extra/tcp-port/extra/tls-port
                        ] close secure""".formatted(server.port())))
                        .isEqualTo("[tcp #(true) #(true)]");
            }
        }
    }

    @Nested
    @DisplayName("a server the host does not trust")
    class AnUntrustedServer {

        @ParameterizedTest(name = "{0}, for {1}")
        @CsvSource(delimiter = '|', value = {
                "other-host test-intermediate        | localhost | the certificate does not name localhost",
                "other-host test-intermediate        | 127.0.0.1 | the certificate does not name 127.0.0.1",
                "self-signed                         | localhost | no trusted authority vouches for localhost",
                "test-intermediate localhost-rsa     | localhost | the certificate chain for localhost is broken",
                "one-day test-intermediate           | localhost | a certificate for localhost has expired",
        })
        @DisplayName("ends the handshake with an error event saying why, and nothing the script meant to write is sent")
        void endsSayingWhy(String chain, String host, String reason) throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(identityWithTheRsaKey(chain.split(" ")), TLS_1_3)) {
                assertThat(openAndSayWhatHappened(makeAnInterpreterTrustingTheTestRoot(), host, server.port()))
                        .isEqualTo("error | " + reason);
                server.awaitAllConnectionsClosed(HANDSHAKE_TIMEOUT);
                assertThat(server.requestsReceived()).isZero();
            }
        }

        @Test
        @DisplayName("is no authority's when the host supplied none, as trusting nothing")
        void trustsNothingWithoutAnAuthority() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(identityWithTheRsaKey("localhost-rsa", "test-intermediate"), TLS_1_3)) {
                assertThat(openAndSayWhatHappened(makeAnInterpreter(new ZeroTrustAuthority(), grantingEverythingSecure()),
                        "localhost", server.port()))
                        .isEqualTo("error | no trusted authority vouches for localhost");
            }
        }

        @Test
        @DisplayName("read over HTTP fails as R3 reports every failed handshake, could not connect")
        void failsOverHttpAsNoConnect() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(identityWithTheRsaKey("self-signed"), TLS_1_3)) {
                assertThat(run(makeAnInterpreterTrustingTheTestRoot(), """
                        failure: try [read https://localhost:%d/]
                        reduce [failure/type failure/id]""".formatted(server.port())))
                        .isEqualTo("[Access no-connect]");
                assertThat(server.requestsReceived()).isZero();
            }
        }
    }
}
