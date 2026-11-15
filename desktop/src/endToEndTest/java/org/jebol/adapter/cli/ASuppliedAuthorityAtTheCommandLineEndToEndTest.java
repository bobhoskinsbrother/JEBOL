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
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
class ASuppliedAuthorityAtTheCommandLineEndToEndTest {

    private static final String TLS_1_3 = "TLSv1.3";

    private final TestCertificates fixtures = new TestCertificates();

    private record Ran(int exitStatus, String printed, String reported) {
    }

    private Ran run(Path directory, String url, Path cafile) throws Exception {
        Path script = directory.resolve("reading.r3");
        Files.writeString(script, """
                Rebol []
                answer: try [read %s]
                print either error? answer [{refused}] [{read it}]
                """.formatted(url));
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        ByteArrayOutputStream reported = new ByteArrayOutputStream();
        String[] arguments = cafile == null
                ? new String[] {script.toString()}
                : new String[] {Repl.CAFILE_SWITCH, cafile.toString(), script.toString()};
        int status = Repl.runCommandLine(arguments,
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                new PrintStream(reported, true, StandardCharsets.UTF_8),
                directory.toString());
        return new Ran(status, printed.toString(StandardCharsets.UTF_8),
                reported.toString(StandardCharsets.UTF_8));
    }

    private Path writeTheAuthority(Path directory) throws Exception {
        Path pem = directory.resolve("authorities.pem");
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(fixtures.readTheDer("test-root"));
        Files.writeString(pem, "-----BEGIN CERTIFICATE-----\n" + body
                + "\n-----END CERTIFICATE-----\n");
        return pem;
    }

    private Identity theLocalhostIdentity() {
        return new Identity(fixtures.chain("localhost-rsa", "test-intermediate"),
                fixtures.readTheKey("localhost-rsa-key", "RSA"));
    }

    @Test
    @DisplayName("an authority supplied with --cafile makes a local server readable")
    void suppliedAuthorityIsTrusted(@TempDir Path directory) throws Exception {
        Path cafile = writeTheAuthority(directory);
        try (LocalTlsServer server = new LocalTlsServer(
                theLocalhostIdentity(), TLS_1_3)) {
            Ran ran = run(directory, "https://localhost:" + server.port() + "/", cafile);

            assertThat(ran.printed()).endsWith("read it\n");
            assertThat(ran.reported()).isEmpty();
            assertThat(ran.exitStatus()).isZero();
            assertThat(server.requestsReceived()).isOne();
        }
    }

    @Test
    @DisplayName("without it the same server is refused, and nothing is requested")
    void withoutTheAuthorityItIsRefused(@TempDir Path directory) throws Exception {
        try (LocalTlsServer server = new LocalTlsServer(
                theLocalhostIdentity(), TLS_1_3)) {
            Ran ran = run(directory, "https://localhost:" + server.port() + "/", null);

            assertThat(ran.printed()).endsWith("refused\n");
            assertThat(ran.exitStatus()).isZero();
            assertThat(server.requestsReceived()).isZero();
        }
    }

    @Test
    @DisplayName("a bundle that is not there is refused with a message saying so")
    void refusesAMissingBundle(@TempDir Path directory) {
        assertThatThrownBy(() -> run(directory, "https://localhost:1/",
                directory.resolve("none.pem")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("none.pem");
    }
}
