package org.jebol.domain.eval;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class APortsStorageFromTheSourceTest {

    private static final class ACannedServer implements NetworkPort {

        private final Deque<byte[]> replies = new ArrayDeque<>();

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
            return new Connection() {

                private boolean open = true;

                @Override
                public byte[] read() {
                    return replies.isEmpty() ? new byte[0] : replies.removeFirst();
                }

                @Override
                public void write(byte[] bytes) {
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

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.withBounds(Bounds.standard()
                .granting(HostService.NETWORK)
                .granting(HostService.CLOCK));
        interpreter.useNetwork(new ACannedServer().willReply("what came back"));
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a connection is the port's state, and its extra is left empty")
    void aConnectionIsThePortsStateAndItsExtraIsLeftEmpty() {
        assertThat(answerTo("""
                c: open tcp://example.com:80
                reduce [true? c/state  none? c/extra]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("and a script may write extra without disturbing the connection")
    void aScriptMayWriteExtraWithoutDisturbingTheConnection() {
        assertThat(answerTo("""
                c: open tcp://example.com:80
                c/extra: make object! [mine: "the script's own"]
                write c "a request"
                reduce [to string! read c  c/extra/mine]"""))
                .isEqualTo("""
                        ["what came back" "the script's own"]""");
    }

    @Test
    @DisplayName("a port is open while its actor has storage, and closing takes it away")
    void aPortIsOpenWhileItsActorHasStorage() {
        assertThat(answerTo("""
                c: open tcp://example.com:80
                was-open: open? c
                close c
                reduce [was-open  open? c  true? c/state]"""))
                .isEqualTo("[#(true) #(false) #(false)]");
    }

    @Test
    @DisplayName("and something else written into state does not make it open")
    void somethingElseWrittenIntoStateDoesNotMakeItOpen() {
        assertThat(answerTo("""
                c: make port! [scheme: 'tcp host: "example.com" port: 80]
                c/state: make object! [borrowed: "somebody else's"]
                open? c""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("and a cipher port does the same")
    void aCipherPortDoesTheSame() {
        assertThat(answerTo("""
                k: open [
                    scheme: 'crypt
                    algorithm: 'aes-128-cbc
                    key: #{000102030405060708090A0B0C0D0E0F}
                    iv: #{000102030405060708090A0B0C0D0E0F}
                    direction: 'encrypt
                ]
                k/extra: "the script's own"
                write k #{00010203040506070809000102030405}
                reduce [true? k/state  k/extra  binary? read k]"""))
                .isEqualTo("""
                        [#(true) "the script's own" #(true)]""");
    }
}
