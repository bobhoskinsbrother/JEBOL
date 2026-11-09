package org.jebol.application;

import org.jebol.adapter.host.JavaCertificateAuthority;
import org.jebol.adapter.host.JavaSecureTransport;
import org.jebol.adapter.host.JavaSocket;
import org.jebol.domain.host.HostService;
import org.jebol.tls.LegacyTlsServer;
import org.jebol.tls.LocalTlsServer;
import org.jebol.tls.LocalTlsServer.ClientCertificate;
import org.jebol.tls.LocalTlsServer.Identity;
import org.jebol.tls.LocalTlsServer.Settings;
import org.jebol.tls.LoopbackForTestNames;
import org.jebol.tls.TestCertificates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class QuirksOfTheHandshakeEndToEndTest {

    private static final Instant FIXTURE_TIME = Instant.parse("2030-06-01T12:00:00Z");
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(60);
    private static final String TLS_1_3 = "TLSv1.3";
    private static final String TLS_1_2 = "TLSv1.2";
    private static final String PAGE_WAS_READ = "#(true)";
    private static final String NO_CONNECT = "[Access no-connect]";

    private final TestCertificates fixtures = new TestCertificates();

    private final StringBuilder printed = new StringBuilder();

    private Identity identityWithTheRsaKey(String... chain) {
        return new Identity(fixtures.chain(chain), fixtures.readTheKey("localhost-rsa-key", "RSA"));
    }

    private Interpreter makeAnInterpreterTrusting(String... roots) {
        return Interpreter.createOn(
                AnInterpreter.standardPorts()
                        .withNetwork(new JavaSocket())
                        .withSecureTransport(new JavaSecureTransport())
                        .withCertificateAuthority(new JavaCertificateAuthority(
                                fixtures.trustOnly(roots),
                                Clock.fixed(FIXTURE_TIME, ZoneOffset.UTC))),
                printed::append,
                Bounds.standard()
                        .withTimeLimit(HANDSHAKE_TIMEOUT)
                        .grant(HostService.NETWORK)
                        .grant(HostService.WEB)
                        .grant(HostService.CLOCK)
                        .grant(HostService.CERTIFICATES));
    }

    private Interpreter makeAnInterpreterTrustingTheTestRoot() {
        return makeAnInterpreterTrusting("test-root");
    }

    private String run(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
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

    private String localhostUrl(int port) {
        return "https://localhost:" + port + "/";
    }

    @Nested
    @DisplayName("a chain sent as a bag rather than a list")
    class AChainSentAsABag {

        @ParameterizedTest(name = "over {0}")
        @ValueSource(strings = {TLS_1_3, TLS_1_2})
        @DisplayName("is read with its leaf sent twice, as münchen.de sends it, where a real 3.22.5 reads it too")
        void leafTwice(String protocol) throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(
                    identityWithTheRsaKey("localhost-rsa", "localhost-rsa", "test-intermediate"), protocol)) {
                assertThat(readPage(makeAnInterpreterTrustingTheTestRoot(), localhostUrl(server.port())))
                        .isEqualTo(PAGE_WAS_READ);
            }
        }

        @ParameterizedTest(name = "over {0}")
        @ValueSource(strings = {TLS_1_3, TLS_1_2})
        @DisplayName("is read with its authorities in reverse")
        void outOfOrder(String protocol) throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(
                    identityWithTheRsaKey("localhost-rsa", "test-root", "test-intermediate"), protocol)) {
                assertThat(readPage(makeAnInterpreterTrustingTheTestRoot(), localhostUrl(server.port())))
                        .isEqualTo(PAGE_WAS_READ);
            }
        }

        @Test
        @DisplayName("missing its intermediate is refused as no authority, and nothing is asked of the server")
        void incompleteChain() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(identityWithTheRsaKey("localhost-rsa"), TLS_1_3)) {
                assertThat(readError(makeAnInterpreterTrustingTheTestRoot(), localhostUrl(server.port())))
                        .isEqualTo(NO_CONNECT);
                server.awaitAllConnectionsClosed(HANDSHAKE_TIMEOUT);
                assertThat(server.requestsReceived()).isZero();
                assertThat(printed.toString()).contains("no trusted authority vouches for localhost");
            }
        }
    }

    @Nested
    @DisplayName("an expired chain through an authority the host does not trust")
    class ExpiredThroughAnOldRoot {

        @Test
        @DisplayName("is refused as expired, as OpenSSL says of badssl.com's, rather than as no authority")
        void saysExpired() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(
                    identityWithTheRsaKey("one-day", "test-intermediate", "test-root-cross-signed"), TLS_1_3)) {
                assertThat(readError(makeAnInterpreterTrusting("stranger-root"), localhostUrl(server.port())))
                        .isEqualTo(NO_CONNECT);
                assertThat(printed.toString()).contains("a certificate for localhost has expired");
                assertThat(server.requestsReceived()).isZero();
            }
        }
    }

    @Nested
    @DisplayName("the key exchanges and versions the JVM's own TLS takes")
    class KeyExchanges {

        @ParameterizedTest(name = "{0} with a {1}-bit group")
        @CsvSource({
                "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256, 1024",
                "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256, 2048",
        })
        @DisplayName("read a server offering only finite-field Diffie-Hellman, which a real 3.22.5 refuses")
        void readsDiffieHellman(String suite, int bits) throws Exception {
            try (LegacyTlsServer server = new LegacyTlsServer(TLS_1_2, suite, bits)) {
                assertThat(readPage(makeAnInterpreterTrustingTheTestRoot(), localhostUrl(server.port())))
                        .isEqualTo(PAGE_WAS_READ);
            }
        }

        @ParameterizedTest(name = "{0} over {1}")
        @CsvSource({
                "TLS_RSA_WITH_AES_128_GCM_SHA256,       TLSv1.2",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,    TLSv1",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,    TLSv1.1",
                "SSL_RSA_WITH_RC4_128_SHA,              TLSv1.2",
                "TLS_ECDHE_RSA_WITH_RC4_128_SHA,        TLSv1.2",
                "SSL_RSA_WITH_3DES_EDE_CBC_SHA,         TLSv1.2",
                "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA,   TLSv1.2",
                "TLS_RSA_WITH_NULL_SHA256,              TLSv1.2",
                "TLS_ECDHE_RSA_WITH_NULL_SHA,           TLSv1.2",
        })
        @DisplayName("refuse a server offering only RSA key exchange, an old version, RC4, triple DES or no cipher")
        void refusesWhatTheJvmDisables(String suite, String protocol) throws Exception {
            try (LegacyTlsServer server = new LegacyTlsServer(protocol, suite, 2048)) {
                assertThat(readError(makeAnInterpreterTrustingTheTestRoot(), localhostUrl(server.port())))
                        .isEqualTo(NO_CONNECT);
            }
        }
    }

    @Nested
    @DisplayName("a server that asks for a client certificate")
    class AskingForACertificate {

        @ParameterizedTest(name = "over {0}")
        @ValueSource(strings = {TLS_1_3, TLS_1_2})
        @DisplayName("is sent none and read, where a real 3.22.5 fails")
        void sentNoneAndRead(String protocol) throws Exception {
            Settings wanting = Settings.speaking(protocol).asking(ClientCertificate.WANTED);
            try (LocalTlsServer server = new LocalTlsServer(
                    identityWithTheRsaKey("localhost-rsa", "test-intermediate"), wanting)) {
                assertThat(readPage(makeAnInterpreterTrustingTheTestRoot(), localhostUrl(server.port())))
                        .isEqualTo(PAGE_WAS_READ);
            }
        }

        @Test
        @DisplayName("and insists ends the handshake, which is the server's choice")
        void insistsAndEnds() throws Exception {
            Settings needing = Settings.speaking(TLS_1_2).asking(ClientCertificate.NEEDED);
            try (LocalTlsServer server = new LocalTlsServer(
                    identityWithTheRsaKey("localhost-rsa", "test-intermediate"), needing)) {
                assertThat(readError(makeAnInterpreterTrustingTheTestRoot(), localhostUrl(server.port())))
                        .isEqualTo(NO_CONNECT);
                server.awaitAllConnectionsClosed(HANDSHAKE_TIMEOUT);
                assertThat(server.requestsReceived()).isZero();
            }
        }
    }

    @Nested
    @DisplayName("a certificate with an Ed25519 key")
    class Ed25519 {

        @Test
        @DisplayName("is checked and read")
        void checkedAndRead() throws Exception {
            Identity ed25519 = new Identity(fixtures.chain("localhost-ed25519", "test-intermediate"),
                    fixtures.readTheKey("localhost-ed25519-key", "Ed25519"));
            try (LocalTlsServer server = new LocalTlsServer(ed25519, TLS_1_3)) {
                assertThat(readPage(makeAnInterpreterTrustingTheTestRoot(), localhostUrl(server.port())))
                        .isEqualTo(PAGE_WAS_READ);
            }
        }

        @Test
        @DisplayName("presented with a key that is not its own is refused, since the signature is checked")
        void impostorRefused() throws Exception {
            Identity impostor = new Identity(fixtures.chain("localhost-ed25519", "test-intermediate"),
                    fixtures.readTheKey("impostor-ed25519-key", "Ed25519"));
            try (LocalTlsServer server = new LocalTlsServer(impostor, TLS_1_3)) {
                assertThat(readError(makeAnInterpreterTrustingTheTestRoot(), localhostUrl(server.port())))
                        .isEqualTo(NO_CONNECT);
                assertThat(server.requestsReceived()).isZero();
            }
        }
    }

    @Nested
    @DisplayName("a host written in Unicode")
    class AUnicodeHost {

        @ParameterizedTest(name = "{0} as {1}")
        @CsvSource({
                "bücher.test, xn--bcher-kva.test",
                "faß.test,    xn--fa-hia.test",
        })
        @DisplayName("is looked up, named to the server, sent as Host and checked by its ASCII name, as a browser converts it")
        void reachedByItsAsciiName(String written, String ascii) throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(
                    identityWithTheRsaKey("unicode-hosts", "test-intermediate"), TLS_1_3)) {
                String url = "https://" + written + ":" + server.port() + "/";

                assertThat(readPage(makeAnInterpreterTrustingTheTestRoot(), url)).isEqualTo(PAGE_WAS_READ);
                assertThat(LoopbackForTestNames.namesLookedUp()).contains(ascii).doesNotContain(written);
                assertThat(server.serverNamesSent()).containsExactly(ascii);
                assertThat(server.hostsAskedFor()).containsExactly(ascii + ":" + server.port());
            }
        }

        @Test
        @DisplayName("stays as written in the port's spec")
        void staysAsWritten() throws Exception {
            try (LocalTlsServer server = new LocalTlsServer(
                    identityWithTheRsaKey("unicode-hosts", "test-intermediate"), TLS_1_3)) {
                assertThat(run(makeAnInterpreterTrustingTheTestRoot(), """
                        secure: open tls://bücher.test:%d
                        wait [secure 30]
                        also secure/spec/host close secure""".formatted(server.port())))
                        .isEqualTo("\"bücher.test\"");
            }
        }

        @Test
        @DisplayName("that UTS #46 refuses is no host, and is never looked up")
        void refusedName() {
            assertThat(readError(makeAnInterpreterTrustingTheTestRoot(), "https://xn--ß.test:443/"))
                    .isEqualTo(NO_CONNECT);
            assertThat(LoopbackForTestNames.namesLookedUp()).noneMatch(name -> name.startsWith("xn--ß")
                    || name.startsWith("xn--ss"));
        }
    }

    @Nested
    @DisplayName("an 8192-bit RSA certificate")
    class LargeRsaKey {

        @Test
        @DisplayName("is read, where a real 3.22.5 hangs or dies")
        void read() throws Exception {
            Identity large = new Identity(fixtures.chain("localhost-rsa8192", "test-intermediate"),
                    fixtures.readTheKey("localhost-rsa8192-key", "RSA"));
            try (LocalTlsServer server = new LocalTlsServer(large, TLS_1_3)) {
                assertThat(readPage(makeAnInterpreterTrustingTheTestRoot(), localhostUrl(server.port())))
                        .isEqualTo(PAGE_WAS_READ);
            }
        }
    }
}
