package org.jebol.application;

import org.jebol.adapter.host.JavaSocket;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.NetworkPort;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingADnsUrlFromTheSourceTest {

    private String answerTo(NetworkPort network, String source) {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withNetwork(network),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.NETWORK));
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private record AKnownDirectory(Map<String, String> addressesByName, String machine) implements NetworkPort {

        @Override
        public Optional<String> resolveTheFirstIpv4Address(String hostName) {
            return Optional.ofNullable(addressesByName.get(hostName));
        }

        @Override
        public Optional<String> resolveHostName(String ipv4Address) {
            return addressesByName.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(ipv4Address))
                    .map(Map.Entry::getKey)
                    .findFirst();
        }

        @Override
        public String hostName() {
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
        void answersANamesAddressAsATuple() {
            assertThat(answerTo(directory, "read dns://example.test")).isEqualTo("192.0.2.7");
            assertThat(answerTo(directory, "tuple? read dns://example.test")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an address answers its name as a string")
        void answersAnAddressesNameAsAString() {
            assertThat(answerTo(directory, "read dns://192.0.2.7")).isEqualTo("\"example.test\"");
        }

        @Test
        @DisplayName("no host answers this machine's name")
        void answersThisMachinesNameForNoHost() {
            assertThat(answerTo(directory, "read dns://")).isEqualTo("\"this-machine\"");
        }

        @Test
        @DisplayName("a name nobody knows answers none")
        void answersNoneForAnUnknownName() {
            assertThat(answerTo(directory, "read dns://nobody.test")).isEqualTo("_");
        }

        @Test
        @DisplayName("an address nobody knows answers none")
        void answersNoneForAnUnknownAddress() {
            assertThat(answerTo(directory, "read dns://198.51.100.1")).isEqualTo("_");
        }

        @Test
        @DisplayName("an address of three parts answers none, having no fourth byte to look up")
        void answersNoneForThreeParts() {
            assertThat(answerTo(directory, "read dns://1.2.3")).isEqualTo("_");
        }
    }

    @Nested
    @DisplayName("through the machine's own resolver")
    class ThroughTheMachine {

        @Test
        @DisplayName("localhost is 127.0.0.1, as r3 answers")
        void localhost() {
            assertThat(answerTo(new JavaSocket(), "read dns://localhost")).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("127.0.0.1 is named, as r3 answers with a string")
        void resolvesTheLoopback() {
            assertThat(answerTo(new JavaSocket(), "string? read dns://127.0.0.1")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("this machine has a name")
        void namesThisMachine() {
            assertThat(answerTo(new JavaSocket(), "string? read dns://")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a name under .invalid, which no resolver answers, is none")
        void answersNoneForAnInvalidName() {
            assertThat(answerTo(new JavaSocket(), "read dns://nonexistent.invalid")).isEqualTo("_");
        }
    }
}
