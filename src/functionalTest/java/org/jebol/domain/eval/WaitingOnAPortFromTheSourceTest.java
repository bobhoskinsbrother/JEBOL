package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.tcp.ACannedPeer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WaitingOnAPortFromTheSourceTest {

    private static final String SCENARIO_ARROW = " -> ";

    private static Interpreter reaching(ACannedPeer peer, StringBuilder printed) {
        return Interpreter.createOn(
                AnInterpreter.standardPorts().withNetwork(peer),
                printed::append,
                Bounds.standard()
                        .withTimeLimit(Duration.ofSeconds(30))
                        .grant(HostService.NETWORK)
                        .grant(HostService.CLOCK));
    }

    private static String answerFrom(ACannedPeer peer, String source) {
        Interpreter interpreter = reaching(peer, new StringBuilder());
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = WaitingOnAPortFromTheSourceTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static Stream<Arguments> whatARealRebolAnswered() throws IOException {
        return readResource("/tcp/waiting.txt").lines()
                .filter(line -> line.contains(SCENARIO_ARROW))
                .map(line -> Arguments.of(line.substring(0, line.indexOf(SCENARIO_ARROW)), line));
    }

    private static ACannedPeer playingThePeerOf(String scenario) {
        return switch (scenario) {
            case "bytes-add-to-the-data" -> new ACannedPeer().willReply("one").willReply("two").closingOnceAllIsSaid();
            case "a-close-when-nothing-more-arrives" -> new ACannedPeer().closingOnceAllIsSaid();
            default -> new ACannedPeer().willReply("some bytes back");
        };
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("whatARealRebolAnswered")
    @DisplayName("a tcp port reports what a real 3.22.5 reported, scenario by scenario, from Rebol's own probe")
    void reportsAsARealRebolDoes(String scenario, String whatRebolPrinted) throws IOException {
        StringBuilder printed = new StringBuilder();
        Interpreter interpreter = reaching(playingThePeerOf(scenario), printed);
        String source = "system/script/args: {" + scenario + " 80}\n" + readResource("/tcp/waiting.r3");

        interpreter.defineNewWordsIn(source);
        interpreter.run(source);

        assertThat(printed.toString().strip()).isEqualTo(whatRebolPrinted);
    }

    @Test
    @DisplayName("the probe's transcript holds every scenario the probe runs")
    void theTranscriptIsWhole() throws IOException {
        List<String> answered = whatARealRebolAnswered().map(arguments -> (String) arguments.get()[0]).toList();

        assertThat(answered).hasSize(13);
        assertThat(readResource("/tcp/waiting.r3")).contains(answered.stream().map(name -> name + " [").toList());
    }

    @Test
    @DisplayName("waiting on something that is not a port answers none")
    void waitingOnSomethingThatIsNotAPortAnswersNone() {
        assertThat(answerFrom(new ACannedPeer(), "wait [0]")).isEqualTo("_");
    }

    @Test
    @DisplayName("a whole request runs Rebol's own HTTP over the connection, through the lookup R3 makes first")
    void aWholeRequestRunsRebolsOwnHttpOverTheConnection() {
        ACannedPeer peer = new ACannedPeer().willReply("""
                HTTP/1.1 200 OK\r
                Content-Type: text/plain\r
                Content-Length: 5\r
                \r
                hello""");
        assertThat(answerFrom(peer, "read http://example.com")).isEqualTo("\"hello\"");
        assertThat(peer.namesLookedUp()).containsExactly("example.com");
        assertThat(peer.addressesReached()).containsExactly(ACannedPeer.ITS_ADDRESS + ":80");
        assertThat(peer.whatItWasSent().getFirst()).startsWith("GET / HTTP/1.1");
    }

    @Test
    @DisplayName("the request names the host it was addressed to")
    void theRequestNamesTheHostItWasAddressedTo() {
        ACannedPeer peer = new ACannedPeer().willReply("""
                HTTP/1.1 200 OK\r
                Content-Length: 2\r
                \r
                hi""");
        answerFrom(peer, "read http://example.com/some/path");
        assertThat(peer.whatItWasSent().getFirst())
                .contains("GET /some/path HTTP/1.1")
                .contains("Host: example.com");
    }

    @Test
    @DisplayName("a status the server refuses with is raised")
    void aStatusTheServerRefusesWithIsRaised() {
        ACannedPeer peer = new ACannedPeer().willReply("""
                HTTP/1.1 404 Not Found\r
                Content-Length: 0\r
                \r
                """);
        assertThat(answerFrom(peer, "error? try [read http://example.com]")).isEqualTo("#(true)");
    }
}
