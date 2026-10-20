package org.jebol.adapter.cli;

import org.jebol.tls.LocalTlsServer;
import org.jebol.tls.LocalTlsServer.Identity;
import org.jebol.tls.TestCertificates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class UntrustedServersAtTheCommandLineEndToEndTest {

    private static final String TLS_1_3 = "TLSv1.3";

    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(60);

    private final TestCertificates fixtures = new TestCertificates();

    private record Ran(int exitStatus, String printed, String reported) {
    }

    private Ran run(Path directory, String url) throws IOException {
        Path script = directory.resolve("reading.r3");
        Files.writeString(script, """
                Rebol []
                answer: try [read %s]
                print either error? answer [join {refused: } answer/id] [{read it}]
                """.formatted(url));
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        ByteArrayOutputStream reported = new ByteArrayOutputStream();
        int status = Repl.runCommandLine(new String[] {script.toString()},
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                new PrintStream(reported, true, StandardCharsets.UTF_8),
                directory.toString());
        return new Ran(status, printed.toString(StandardCharsets.UTF_8),
                reported.toString(StandardCharsets.UTF_8));
    }

    private Identity anIdentityWith(String... chain) {
        return new Identity(fixtures.chain(chain),
                fixtures.readTheKey("localhost-rsa-key", "RSA"));
    }

    private void refuses(LocalTlsServer server, Path directory) throws Exception {
        Ran ran = run(directory, "https://localhost:" + server.port() + "/");

        server.awaitAllConnectionsClosed(HANDSHAKE_TIMEOUT);
        assertThat(ran.printed())
                .contains("no trusted authority vouches for localhost")
                .endsWith("refused: no-connect\n");
        assertThat(ran.exitStatus()).isZero();
        assertThat(server.connectionsAccepted()).isPositive();
        assertThat(server.requestsReceived()).isZero();
    }

    @Test
    @DisplayName("a server whose certificate signed itself is refused, with the reason printed")
    void refusesASelfSignedServer(@TempDir Path directory) throws Exception {
        try (LocalTlsServer server = new LocalTlsServer(anIdentityWith("self-signed"), TLS_1_3)) {
            refuses(server, directory);
        }
    }

    @Test
    @DisplayName("a server chaining to a root nothing trusts is refused, with the reason printed")
    void refusesAServerWithAnUntrustedRoot(@TempDir Path directory) throws Exception {
        try (LocalTlsServer server = new LocalTlsServer(
                anIdentityWith("from-a-stranger", "stranger-root"), TLS_1_3)) {
            refuses(server, directory);
        }
    }
}
