package org.jebol.domain.eval;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What WAIT over a port does, which is the whole of how a protocol runs.
 *
 * <p>{@code Wait_Ports} in {@code c-port.c} loops: {@code if ((result =
 * Awake_System(ports, only)) &gt; 0) return TRUE;}. It does not come back after
 * the first event -- it keeps handing events to ports until one of them says to
 * stop. The system port's own AWAKE, written in {@code sys-ports.reb}, is what
 * decides: it calls {@code wake-up} on each port, adds the port to a wake list
 * when that answers true, and only then answers true itself.
 *
 * <p>JEBOL answered the port after the first event, and that is a difference
 * with a consequence rather than a detail. Rebol's own HTTP reads a response by
 * waiting: {@code read-sync-awake} answers false for {@code connect} and false
 * for {@code wrote}, because neither finishes a request, and true only when the
 * response is complete. A WAIT that returned after {@code connect} put
 * {@code sync-op} back at the top of its loop with a status code nobody had
 * set, where {@code state/info/status-code &gt;= 300} compares a none with an
 * integer and raises.
 *
 * <p>The network here is a fake, so every expectation is about the
 * interpreter rather than about a host on the internet. The canned replies
 * were taken from a real exchange first.
 */
class WaitingOnAPortFromTheSourceTest {

    /**
     * A network that says what it was told and replies with what it was given.
     *
     * <p>The seam the domain owns. A test that reached a real host would be
     * measuring somebody else's server, and would measure nothing at all on a
     * machine with no network.
     */
    private static final class ACannedServer implements NetworkPort {

        private final Deque<byte[]> replies = new ArrayDeque<>();
        private final List<String> whatItWasSent = new ArrayList<>();
        private final List<String> whatItWasAskedFor = new ArrayList<>();

        private ACannedServer willReply(String text) {
            replies.add(text.getBytes(StandardCharsets.ISO_8859_1));
            return this;
        }

        @Override
        public List<String> addressesFor(String hostName) {
            return List.of("203.0.113.1");
        }

        @Override
        public Connection connectTo(String hostName, int portNumber) {
            whatItWasAskedFor.add(hostName + ":" + portNumber);
            return new Connection() {

                private boolean open = true;

                @Override
                public byte[] read() {
                    return replies.isEmpty() ? new byte[0] : replies.removeFirst();
                }

                @Override
                public void write(byte[] bytes) {
                    whatItWasSent.add(new String(bytes, StandardCharsets.ISO_8859_1));
                }

                @Override
                public boolean isOpen() {
                    return open;
                }

                @Override
                public void close() {
                    open = false;
                }
            };
        }
    }

    private static Interpreter reaching(ACannedServer server) {
        Interpreter interpreter = Interpreter.withBounds(Bounds.standard()
                .granting(HostService.NETWORK)
                .granting(HostService.CLOCK));
        interpreter.useNetwork(server);
        return interpreter;
    }

    private static String answerFrom(ACannedServer server, String source) {
        Interpreter interpreter = reaching(server);
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String answerTo(String source) {
        return answerFrom(new ACannedServer(), source);
    }

    /**
     * Three things have happened to the connection by the time WAIT is
     * reached, so how many of them one call delivers is a question the test
     * can ask rather than infer.
     */
    private static String afterThreeThingsHappened(String awakeBody, String waiting) {
        return answerFrom(new ACannedServer().willReply("some bytes back"), """
                seen: copy []
                c: open tcp://example.com:80
                c/awake: func [event] [append seen event/type
                """
                + awakeBody
                + """
                ]
                write c "hello"
                read c
                """
                + waiting);
    }

    @Test
    @DisplayName("the wait ends where the awake function says true")
    void theWaitEndsWhereTheAwakeFunctionSaysTrue() {
        assertThat(afterThreeThingsHappened(
                "event/type = 'wrote",
                "reduce [port? wait [c 1] seen]"))
                .isEqualTo("[#(true) [connect wrote]]");
    }

    @Test
    @DisplayName("the wait keeps going while the awake function says false")
    void theWaitKeepsGoingWhileTheAwakeFunctionSaysFalse() {
        assertThat(afterThreeThingsHappened(
                "false",
                "reduce [port? wait [c 0] seen]"))
                .isEqualTo("[#(false) [connect wrote read]]");
    }

    @Test
    @DisplayName("a truthy answer that is not a logic does not end the wait")
    void aTruthyAnswerThatIsNotALogicDoesNotEndTheWait() {
        assertThat(afterThreeThingsHappened(
                "\"yes\"",
                "reduce [port? wait [c 0] seen]"))
                .isEqualTo("[#(false) [connect wrote read]]");
    }

    @Test
    @DisplayName("the things are reported in the order they happened, once each")
    void theThingsAreReportedInTheOrderTheyHappenedOnceEach() {
        assertThat(afterThreeThingsHappened(
                "false",
                """
                        wait [c 0]
                        wait [c 0]
                        seen"""))
                .isEqualTo("[connect wrote read]");
    }

    @Test
    @DisplayName("a port with no awake function ends the wait at once")
    void aPortWithNoAwakeFunctionEndsTheWaitAtOnce() {
        assertThat(answerTo("""
                c: open tcp://example.com:80
                c/awake: none
                port? wait [c 1]"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("waiting on a quiet port answers none")
    void waitingOnAQuietPortAnswersNone() {
        assertThat(afterThreeThingsHappened(
                "false",
                """
                        wait [c 0]
                        wait [c 0]"""))
                .isEqualTo("_");
    }

    @Test
    @DisplayName("the timeout is found wherever it sits in the block")
    void theTimeoutIsFoundWhereverItSitsInTheBlock() {
        assertThat(afterThreeThingsHappened(
                "false",
                """
                        wait [c 0]
                        reduce [wait [0 c] wait [0:00:00 c]]"""))
                .isEqualTo("[_ _]");
    }

    @Test
    @DisplayName("bytes that arrive are added to what the data already held")
    void bytesThatArriveAreAddedToWhatTheDataAlreadyHeld() {
        ACannedServer server = new ACannedServer().willReply("one").willReply("two");
        assertThat(answerFrom(server, """
                c: open tcp://example.com:80
                read c
                read c
                to string! c/data"""))
                .isEqualTo("\"onetwo\"");
    }

    @Test
    @DisplayName("a connection reports a close when nothing more arrives")
    void aConnectionReportsACloseWhenNothingMoreArrives() {
        assertThat(answerTo("""
                seen: copy []
                c: open tcp://example.com:80
                c/awake: func [event] [append seen event/type  event/type = 'close]
                read c
                reduce [port? wait [c 1] seen]"""))
                .isEqualTo("[#(true) [connect close]]");
    }

    @Test
    @DisplayName("waiting on something that is not a port answers none")
    void waitingOnSomethingThatIsNotAPortAnswersNone() {
        assertThat(answerTo("""
                wait [0]"""))
                .isEqualTo("_");
    }

    @Test
    @DisplayName("a whole request runs Rebol's own HTTP over the connection")
    void aWholeRequestRunsRebolsOwnHttpOverTheConnection() {
        ACannedServer server = new ACannedServer().willReply("""
                HTTP/1.1 200 OK\r
                Content-Type: text/plain\r
                Content-Length: 5\r
                \r
                hello""");
        assertThat(answerFrom(server, """
                read http://example.com"""))
                .isEqualTo("\"hello\"");
        assertThat(server.whatItWasAskedFor).containsExactly("example.com:80");
        assertThat(server.whatItWasSent.getFirst()).startsWith("GET / HTTP/1.1");
    }

    @Test
    @DisplayName("the request names the host it was addressed to")
    void theRequestNamesTheHostItWasAddressedTo() {
        ACannedServer server = new ACannedServer().willReply("""
                HTTP/1.1 200 OK\r
                Content-Length: 2\r
                \r
                hi""");
        answerFrom(server, "read http://example.com/some/path");
        assertThat(server.whatItWasSent.getFirst())
                .contains("GET /some/path HTTP/1.1")
                .contains("Host: example.com");
    }

    @Test
    @DisplayName("a status the server refuses with is raised")
    void aStatusTheServerRefusesWithIsRaised() {
        ACannedServer server = new ACannedServer().willReply("""
                HTTP/1.1 404 Not Found\r
                Content-Length: 0\r
                \r
                """);
        assertThat(answerFrom(server, """
                error? try [read http://example.com]"""))
                .isEqualTo("#(true)");
    }
}
