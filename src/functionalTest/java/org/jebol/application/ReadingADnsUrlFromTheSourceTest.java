package org.jebol.application;

import org.jebol.adapter.host.JavaSockets;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.NetworkPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingADnsUrlFromTheSourceTest {

    private String answerTo(NetworkPort network, String source) {
        Interpreter interpreter = Interpreter.withBounds(Bounds.standard().grant(HostService.NETWORK));
        interpreter.useNetwork(network);
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private record AKnownDirectory(Map<String, String> addressesByName, String machine) implements NetworkPort {

        @Override
        public Optional<String> theFirstIpv4AddressOf(String hostName) {
            return Optional.ofNullable(addressesByName.get(hostName));
        }

        @Override
        public Optional<String> theNameAt(String ipv4Address) {
            return addressesByName.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(ipv4Address))
                    .map(Map.Entry::getKey)
                    .findFirst();
        }

        @Override
        public String thisMachinesName() {
            return machine;
        }

        @Override
        public Connection connectTo(String hostName, int portNumber) {
            throw new Refused("no-connect", "this directory carries no connections", hostName);
        }

        @Override
        public Datagrams bindTo(int portNumber) {
            throw new Refused("no-connect", "this directory carries no datagrams", String.valueOf(portNumber));
        }
    }

    private final NetworkPort directory = new AKnownDirectory(
            Map.of("example.test", "192.0.2.7"), "this-machine");

    @Nested
    @DisplayName("through a directory whose answers are known")
    class ThroughAKnownDirectory {

        @Test
        @DisplayName("a name answers its address as a tuple")
        void aNameAnswersATuple() {
            assertThat(answerTo(directory, "read dns://example.test")).isEqualTo("192.0.2.7");
            assertThat(answerTo(directory, "tuple? read dns://example.test")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an address answers its name as a string")
        void anAddressAnswersAString() {
            assertThat(answerTo(directory, "read dns://192.0.2.7")).isEqualTo("\"example.test\"");
        }

        @Test
        @DisplayName("no host answers this machine's name")
        void noHostAnswersThisMachine() {
            assertThat(answerTo(directory, "read dns://")).isEqualTo("\"this-machine\"");
        }

        @Test
        @DisplayName("a name nobody knows answers none")
        void anUnknownName() {
            assertThat(answerTo(directory, "read dns://nobody.test")).isEqualTo("_");
        }

        @Test
        @DisplayName("an address nobody knows answers none")
        void anUnknownAddress() {
            assertThat(answerTo(directory, "read dns://198.51.100.1")).isEqualTo("_");
        }

        @Test
        @DisplayName("an address of three parts answers none, having no fourth byte to look up")
        void threeParts() {
            assertThat(answerTo(directory, "read dns://1.2.3")).isEqualTo("_");
        }
    }

    @Nested
    @DisplayName("through the machine's own resolver")
    class ThroughTheMachine {

        @Test
        @DisplayName("localhost is 127.0.0.1, as r3 answers")
        void localhost() {
            assertThat(answerTo(new JavaSockets(), "read dns://localhost")).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("127.0.0.1 is named, as r3 answers with a string")
        void theLoopback() {
            assertThat(answerTo(new JavaSockets(), "string? read dns://127.0.0.1")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("this machine has a name")
        void thisMachine() {
            assertThat(answerTo(new JavaSockets(), "string? read dns://")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a name under .invalid, which no resolver answers, is none")
        void anInvalidName() {
            assertThat(answerTo(new JavaSockets(), "read dns://nonexistent.invalid")).isEqualTo("_");
        }
    }
}
