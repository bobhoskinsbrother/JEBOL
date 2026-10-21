package org.jebol.application;

import com.sun.net.httpserver.HttpServer;
import org.jebol.adapter.host.FileSystemPort;
import org.jebol.adapter.host.JavaCertificateAuthority;
import org.jebol.adapter.host.JavaSocket;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.tls.LocalTlsServer;
import org.jebol.tls.LocalTlsServer.Identity;
import org.jebol.tls.TestCertificates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TheThruCacheFunctionsFromTheSourceTest {

    private static final String TLS_1_3 = "TLSv1.3";
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(60);
    private static final Instant FIXTURE_TIME = Instant.parse("2030-06-01T12:00:00Z");

    private final TestCertificates fixtures = new TestCertificates();

    private Identity anIdentityWith(String... chain) {
        return new Identity(fixtures.chain(chain),
                fixtures.readTheKey("localhost-rsa-key", "RSA"));
    }

    private Interpreter makeAnInterpreterTrustingTheTestRoot(Path root) {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts()
                        .withFiles(FileSystemPort.rootFileSystem(root))
                        .withNetwork(new JavaSocket())
                        .withCertificateAuthority(new JavaCertificateAuthority(
                                fixtures.trustOnly("test-root"),
                                Clock.fixed(FIXTURE_TIME, ZoneOffset.UTC))),
                OutputPort.discard(),
                Bounds.standard()
                        .withTimeLimit(HANDSHAKE_TIMEOUT)
                        .grant(HostService.NETWORK)
                        .grant(HostService.WEB)
                        .grant(HostService.CLOCK)
                        .grant(HostService.CERTIFICATES)
                        .grant(HostService.FILES)
                        .grant(HostService.WORKING_DIRECTORY));
        String settingUp = """
                system/options/data: %/data/
                make-dir/deep system/options/data
                make-dir/deep %/cache/
                put system/options 'thru-cache %/cache/""";
        ran(interpreter, settingUp);
        interpreter.followApplicationDataDirectory();
        return interpreter;
    }

    private void ran(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        if (!outcome.succeeded()) {
            throw new IllegalStateException(source + " raised: " + interpreter.display(outcome));
        }
    }

    private String answerWithTheCache(Interpreter interpreter, String source) {
        ran(interpreter, """
                make-dir/deep system/options/modules
                import 'thru-cache""");
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String urlFor(LocalTlsServer server, String path) {
        return "https://localhost:" + server.port() + path;
    }

    @Test
    @DisplayName("what the server sends is cached, and reading it back answers the same bytes")
    void cachesWhatTheServerSends(@TempDir Path root) throws Exception {
        try (LocalTlsServer server = new LocalTlsServer(
                anIdentityWith("localhost-rsa", "test-intermediate"), TLS_1_3)) {
            assertThat(answerWithTheCache(makeAnInterpreterTrustingTheTestRoot(root), """
                    url: %s
                    data: read-thru url
                    reduce [
                        binary? data
                        not none? exists? path-thru url
                        data = read/binary path-thru url
                        (to string! data) = {served over a checked connection}
                    ]""".formatted(urlFor(server, "/data.txt"))))
                    .isEqualTo("[#(true) #(true) #(true) #(true)]");
        }
    }

    @Test
    @DisplayName("read-thru/string answers the text the server sends")
    void answersTheTextWhenAsked(@TempDir Path root) throws Exception {
        try (LocalTlsServer server = new LocalTlsServer(
                anIdentityWith("localhost-rsa", "test-intermediate"), TLS_1_3)) {
            assertThat(answerWithTheCache(makeAnInterpreterTrustingTheTestRoot(root), """
                    s: read-thru/string %s
                    reduce [string? s s = {served over a checked connection}]"""
                    .formatted(urlFor(server, "/data.txt"))))
                    .isEqualTo("[#(true) #(true)]");
        }
    }

    @Test
    @DisplayName("load-thru loads the script the server sends")
    void loadsTheScriptTheServerSends(@TempDir Path root) throws Exception {
        try (LocalTlsServer server = new LocalTlsServer(
                anIdentityWith("localhost-rsa", "test-intermediate"), TLS_1_3,
                path -> "Rebol []\n42\n43\n")) {
            assertThat(answerWithTheCache(makeAnInterpreterTrustingTheTestRoot(root), """
                    block? load-thru %s""".formatted(urlFor(server, "/script.r3"))))
                    .isEqualTo("#(true)");
        }
    }

    @Test
    @DisplayName("do-thru runs the script the server sends")
    void runsTheScriptTheServerSends(@TempDir Path root) throws Exception {
        try (LocalTlsServer server = new LocalTlsServer(
                anIdentityWith("localhost-rsa", "test-intermediate"), TLS_1_3,
                path -> "Rebol []\n42\n43\n")) {
            assertThat(answerWithTheCache(makeAnInterpreterTrustingTheTestRoot(root), """
                    43 = do-thru %s""".formatted(urlFor(server, "/script.r3"))))
                    .isEqualTo("#(true)");
        }
    }

    @Test
    @DisplayName("delete-thru removes what was cached")
    void deletesWhatWasCached(@TempDir Path root) throws Exception {
        try (LocalTlsServer server = new LocalTlsServer(
                anIdentityWith("localhost-rsa", "test-intermediate"), TLS_1_3)) {
            assertThat(answerWithTheCache(makeAnInterpreterTrustingTheTestRoot(root), """
                    url: %s
                    read-thru url
                    delete-thru url
                    reduce [not exists-thru? url not exists? path-thru url]"""
                    .formatted(urlFor(server, "/data.txt"))))
                    .isEqualTo("[#(true) #(true)]");
        }
    }

    @Test
    @DisplayName("a server the host does not trust is refused, and nothing is cached")
    void refusesAnUntrustedServerAndCachesNothing(@TempDir Path root) throws Exception {
        try (LocalTlsServer server = new LocalTlsServer(
                anIdentityWith("from-a-stranger", "stranger-root"), TLS_1_3)) {
            assertThat(answerWithTheCache(makeAnInterpreterTrustingTheTestRoot(root), """
                    url: %s
                    failure: try [read-thru url]
                    reduce [failure/id not exists-thru? url]"""
                    .formatted(urlFor(server, "/data.txt"))))
                    .isEqualTo("[no-connect #(true)]");
        }
    }

    @Test
    @DisplayName("an error page is not stored, which is what the httpbin assertion checks")
    void doesNotStoreAnErrorPage(@TempDir Path root) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "gone".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, body.length);
            try (OutputStream response = exchange.getResponseBody()) {
                response.write(body);
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/missing";

            assertThat(answerWithTheCache(makeAnInterpreterTrustingTheTestRoot(root), """
                    url: %s
                    reduce [
                        to logic! try [binary? read-thru url]
                        not exists-thru? url
                    ]""".formatted(url)))
                    .isEqualTo("[#(true) #(true)]");
        } finally {
            server.stop(0);
        }
    }
}
