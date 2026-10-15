package org.jebol.adapter.cli;

import org.jebol.tls.LocalTlsServer;
import org.jebol.tls.LocalTlsServer.Identity;
import org.jebol.tls.TestCertificates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class SecureConnectionsAtTheCommandLineEndToEndTest {

    private final TestCertificates fixtures = new TestCertificates();

    @Test
    @DisplayName("the command line trusts the JDK's authorities, so a server only a test authority vouches for is refused before any request")
    void refusesServerTheJdkDoesNotTrust(@TempDir Path directory) throws Exception {
        Identity genuineButUnknown = new Identity(
                fixtures.chain("localhost-rsa", "test-intermediate"),
                fixtures.key("localhost-rsa-key", "RSA"));
        try (LocalTlsServer server = new LocalTlsServer(genuineButUnknown, "TLSv1.3")) {
            Path script = directory.resolve("reading.r3");
            Files.writeString(script, """
                    Rebol []
                    answer: try [read https://localhost:%d/]
                    print either error? answer [join {refused: } answer/id] [{read it}]
                    """.formatted(server.port()));
            ByteArrayOutputStream printed = new ByteArrayOutputStream();
            ByteArrayOutputStream reported = new ByteArrayOutputStream();

            int status = Repl.runTheCommandLine(new String[] {script.toString()},
                    new PrintStream(printed, true, StandardCharsets.UTF_8),
                    new PrintStream(reported, true, StandardCharsets.UTF_8),
                    directory.toString());

            server.awaitAllConnectionsClosed(Duration.ofSeconds(60));
            assertThat(printed.toString(StandardCharsets.UTF_8))
                    .contains("no trusted authority vouches for localhost")
                    .endsWith("refused: no-connect\n");
            assertThat(server.connectionsAccepted()).isPositive();
            assertThat(server.requestsReceived()).isZero();
            assertThat(status).isZero();
        }
    }
}
