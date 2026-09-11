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

/**
 * Which of a port's two storage fields belongs to the actor, and which to the
 * script.
 *
 * <p>{@code sysobj.reb} writes them side by side and says what each is for:
 * {@code state: internal state values (private)} and {@code extra:
 * user-defined storage of local data}. So a socket and a running cipher go in
 * STATE, and EXTRA is the script's to do as it likes with.
 *
 * <p>One actor uses EXTRA and does it knowingly: the checksum port keeps the
 * bytes it has been fed there, so a string written into that field comes back
 * with the digest's own bytes over the top. That is the C's own behaviour,
 * checked against it, and it is why the rule is about where a handle may not
 * be hidden rather than about what EXTRA may hold.
 *
 * <p>JEBOL had them the other way round, and Rebol's own TLS is the thing that
 * breaks. Its OPEN makes a TCP port for the connection and then writes
 * {@code port/extra: conn/extra: make TLS-context [...]}, keeping its whole
 * protocol state in the field the specification gives it -- the version, the
 * two sequence numbers, the handshake hashes, both ports. A socket kept in
 * EXTRA is overwritten by that one line, and the handshake writes its first
 * record to a port with no connection left.
 *
 * <p>Which also settles what "open" means: a port is open while its actor has
 * storage. {@code Awake_System} asks it that way, reading the state field and
 * checking {@code IS_HANDLE(state)}.
 */
class APortsStorageFromTheSourceTest {

    /** A network that replies with whatever it was given, for offline tests. */
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

    /**
     * The point of the split. A script writing its own object into EXTRA must
     * not disturb the connection, because that is what EXTRA is for and what
     * Rebol's own TLS does with it.
     */
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

    /**
     * Closing empties it, so OPEN? answers on the storage rather than on a
     * flag kept beside it.
     *
     * <p>Whether OPEN? is true in the first place is where JEBOL and the C
     * part company for a reason that has nothing to do with this field: the C
     * connects without waiting and answers false until the connect event
     * arrives, where a connection here is made before OPEN returns. The C
     * answers {@code [#(false) #(false) #(true)]} to the three questions
     * below, and the third of those is a socket it has not finished handing
     * back rather than a port that is still usable.
     */
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

    /**
     * What is in STATE, not whether it is truthy. Rebol's own TLS writes
     * {@code conn/state: port/parent/state} into a TCP port it has not opened
     * yet, handing it the HTTP protocol's own object, and then asks
     * {@code either open? conn [...] [open conn]} -- so a port that read that
     * object as "open" never made the connection at all.
     */
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
