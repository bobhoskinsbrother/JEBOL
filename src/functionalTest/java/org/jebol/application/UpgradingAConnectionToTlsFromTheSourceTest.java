package org.jebol.application;

import org.jebol.adapter.host.JavaCertificateAuthority;
import org.jebol.adapter.host.JavaSocket;
import org.jebol.domain.host.HostService;
import org.jebol.tls.LocalSmtpServer;
import org.jebol.tls.LocalTlsServer.Identity;
import org.jebol.tls.TestCertificates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class UpgradingAConnectionToTlsFromTheSourceTest {

    private static final Instant FIXTURE_TIME = Instant.parse("2030-06-01T12:00:00Z");
    private static final Duration TIME_LIMIT = Duration.ofSeconds(60);

    private final TestCertificates fixtures = new TestCertificates();

    private final StringBuilder printed = new StringBuilder();

    private Interpreter makeAnInterpreterTrustingTheTestRoot() {
        return Interpreter.createOn(
                AnInterpreter.standardPorts()
                        .withNetwork(new JavaSocket())
                        .withCertificateAuthority(new JavaCertificateAuthority(
                                fixtures.trustOnly("test-root"), Clock.fixed(FIXTURE_TIME, ZoneOffset.UTC))),
                printed::append,
                Bounds.standard()
                        .withTimeLimit(TIME_LIMIT)
                        .grant(HostService.NETWORK)
                        .grant(HostService.CLOCK)
                        .grant(HostService.CERTIFICATES));
    }

    private String sendMailThrough(LocalSmtpServer server) {
        String source = """
                outcome: try [
                    write [scheme: 'smtp host: "localhost" port: %d ehlo: "jebol.test" user: "me" pass: "secret"] [
                        from: me@jebol.test
                        to: you@jebol.test
                        message: {Subject: upgraded^/^/sent inside TLS}
                    ]
                ]
                either error? outcome [reduce [outcome/type outcome/id outcome/arg1 outcome/arg2 outcome/arg3]] [{sent}]""".formatted(server.port());
        Interpreter interpreter = makeAnInterpreterTrustingTheTestRoot();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("SMTP's STARTTLS upgrades the live connection, and everything after it goes inside TLS")
    void upgradesSmtp() throws Exception {
        Identity trusted = new Identity(fixtures.chain("localhost-rsa", "test-intermediate"),
                fixtures.readTheKey("localhost-rsa-key", "RSA"));
        try (LocalSmtpServer server = new LocalSmtpServer(trusted)) {
            assertThat(sendMailThrough(server)).isEqualTo("\"sent\"");
            server.awaitConversationEnd();
            assertThat(server.commandsInTheClear()).containsExactly("EHLO jebol.test", "STARTTLS");
            assertThat(server.commandsInsideTls()).containsExactly(
                    "EHLO jebol.test", "AUTH PLAIN bWUAbWUAc2VjcmV0", "MAIL FROM: <me@jebol.test>",
                    "RCPT TO: <you@jebol.test>", "DATA", "QUIT");
            assertThat(server.message()).contains("sent inside TLS");
        }
    }

    @Test
    @DisplayName("an upgrade to a server the host does not trust ends there, and no mail is sent")
    void refusesAnUntrustedUpgrade() throws Exception {
        Identity untrusted = new Identity(fixtures.chain("self-signed"), fixtures.readTheKey("localhost-rsa-key", "RSA"));
        try (LocalSmtpServer server = new LocalSmtpServer(untrusted)) {
            assertThat(sendMailThrough(server)).isNotEqualTo("\"sent\"");
            assertThat(server.commandsInsideTls()).isEmpty();
            assertThat(server.message()).isEmpty();
            assertThat(printed.toString()).contains("no trusted authority vouches for localhost");
        }
    }
}
