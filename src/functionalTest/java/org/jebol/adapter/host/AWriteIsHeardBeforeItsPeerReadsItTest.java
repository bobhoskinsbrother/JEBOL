package org.jebol.adapter.host;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.application.PlatformUnderTest;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
class AWriteIsHeardBeforeItsPeerReadsItTest {

    private static final Duration LINGER = Duration.ofMillis(300);

    private final StringBuilder printed = new StringBuilder();

    private static final int THE_SERVERS_ECHO = 2;

    private final AtomicInteger writesSoFar = new AtomicInteger();

    private void lingerAfterTheEcho() {
        if (writesSoFar.incrementAndGet() != THE_SERVERS_ECHO) {
            return;
        }
        try {
            Thread.sleep(LINGER.toMillis());
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = AWriteIsHeardBeforeItsPeerReadsItTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Interpreter interpreterWithSocket(JavaSocket network, Path root) {
        return Interpreter.createOn(
                AnInterpreter.standardPorts()
                        .withFiles(FileSystemPort.rootFileSystem(root))
                        .withNetwork(network),
                printed::append,
                Bounds.standard()
                        .withTimeLimit(Duration.ofSeconds(30))
                        .grant(HostService.NETWORK)
                        .grant(HostService.FILES));
    }

    @Test
    @DisplayName("a server's wrote reaches the script before its own client's read of those bytes, however late the writer reports")
    void theWroteComesFirst(@TempDir Path root) throws IOException {
        String script = readResource("/tls/listening/echo.r3");
        String whatRPrinted = readResource("/tls/listening/echo.txt");
        Interpreter interpreter = interpreterWithSocket(new JavaSocket(PlatformUnderTest.current().hostNames(), this::lingerAfterTheEcho), root);

        interpreter.defineNewWordsIn(script);
        interpreter.run(script);

        assertThat(printed.toString().strip()).isEqualTo(whatRPrinted.strip());
    }
}
