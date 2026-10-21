package org.jebol.application;

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

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ImportingAModuleOverHttpsFromTheSourceTest {

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
                make-dir/deep system/options/data""";
        ran(interpreter, settingUp);
        interpreter.followApplicationDataDirectory();
        ran(interpreter, "make-dir/deep system/options/modules");
        return interpreter;
    }

    private void ran(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        if (!outcome.succeeded()) {
            throw new IllegalStateException(source + " raised: " + interpreter.display(outcome));
        }
    }

    private String answerTo(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("an https module is imported, and the url its header needs is imported too")
    void importsTheUrlItsHeaderNeeds(@TempDir Path root) throws Exception {
        AtomicInteger port = new AtomicInteger();
        try (LocalTlsServer server = new LocalTlsServer(
                anIdentityWith("localhost-rsa", "test-intermediate"), TLS_1_3,
                path -> switch (path) {
                    case "/test-needs-url.reb" -> """
                            Rebol [type: module name: test-needs-url needs: https://localhost:%d/test-needs-url-value.reb]
                            export test-needs-url-result: (42 = test-needs-url-value)
                            """.formatted(port.get());
                    case "/test-needs-url-value.reb" -> """
                            Rebol [type: module name: test-needs-url-value]
                            export test-needs-url-value: 42
                            """;
                    default -> "Rebol []";
                })) {
            port.set(server.port());

            assertThat(answerTo(makeAnInterpreterTrustingTheTestRoot(root), """
                    m: import https://localhost:%d/test-needs-url.reb
                    reduce [module? m true? test-needs-url-result]""".formatted(port.get())))
                    .isEqualTo("[#(true) #(true)]");
        }
    }

    @Test
    @DisplayName("a module server the host does not trust is refused before any request")
    void refusesAnUntrustedServer(@TempDir Path root) throws Exception {
        try (LocalTlsServer server = new LocalTlsServer(
                anIdentityWith("from-a-stranger", "stranger-root"), TLS_1_3,
                path -> "Rebol [type: module name: refused]")) {
            assertThat(answerTo(makeAnInterpreterTrustingTheTestRoot(root), """
                    error? try [import https://localhost:%d/refused.reb]"""
                    .formatted(server.port())))
                    .isEqualTo("#(true)");
            assertThat(server.connectionsAccepted()).isPositive();
            assertThat(server.requestsReceived()).isZero();
        }
    }
}
