package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("network")
@Timeout(120)
class BadSslAtTheCommandLineEndToEndTest {

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
        int status = Repl.runTheCommandLine(new String[] {script.toString()},
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                new PrintStream(reported, true, StandardCharsets.UTF_8),
                directory.toString());
        return new Ran(status, printed.toString(StandardCharsets.UTF_8),
                reported.toString(StandardCharsets.UTF_8));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"https://badssl.com/", "https://ecc256.badssl.com/", "https://rsa2048.badssl.com/"})
    @DisplayName("a server every browser trusts is read, which a real 3.22.5 cannot do for the elliptic curve one")
    void trustedServer(String url, @TempDir Path directory) throws IOException {
        Ran ran = run(directory, url);

        assertThat(ran.printed()).endsWith("read it\n");
        assertThat(ran.reported()).isEmpty();
        assertThat(ran.exitStatus()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "https://expired.badssl.com/        | a certificate for expired.badssl.com has expired",
            "https://self-signed.badssl.com/    | no trusted authority vouches for self-signed.badssl.com",
            "https://untrusted-root.badssl.com/ | no trusted authority vouches for untrusted-root.badssl.com",
            "https://wrong.host.badssl.com/     | the certificate does not name wrong.host.badssl.com",
    })
    @DisplayName("a server every browser refuses is refused, with the reason printed, where a real 3.22.5 reads it")
    void untrustedServer(String url, String reason, @TempDir Path directory) throws IOException {
        Ran ran = run(directory, url);

        assertThat(ran.printed()).contains(reason).endsWith("refused: no-connect\n");
        assertThat(ran.exitStatus()).isZero();
    }
}
