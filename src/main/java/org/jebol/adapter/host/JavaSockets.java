package org.jebol.adapter.host;

import org.jebol.domain.eval.NetworkPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.Arrays;
import java.util.List;

/**
 * The network as the JDK already provides it.
 *
 * <p>{@code java.net} rather than anything written here. Name resolution,
 * connecting, reading and writing are all long solved, and a socket layer of
 * JEBOL's own would be a worse copy that still had to sit behind this same
 * interface.
 *
 * <p>Nothing is cached. A name looked up twice is looked up twice, because the
 * answer can change between the two and a script that asked again wanted to
 * know.
 */
public final class JavaSockets implements NetworkPort {

    /**
     * How long to wait for a connection, and for bytes once connected.
     *
     * <p>A bounded wait rather than none, because a script blocking for ever
     * on a host that will never answer cannot be stopped by the interpreter's
     * own cancellation: it is inside a system call. Thirty seconds is longer
     * than any reasonable connection takes and short enough that a mistake
     * ends within a person's patience.
     */
    private static final int WAITING_MILLISECONDS = 30_000;

    /** What one read takes at most, so a huge response arrives in pieces. */
    private static final int MOST_BYTES_AT_ONCE = 65_536;

    @Override
    public List<String> addressesFor(String hostName) {
        try {
            return Arrays.stream(InetAddress.getAllByName(hostName))
                    .map(InetAddress::getHostAddress)
                    .toList();
        } catch (UnknownHostException noSuchHost) {
            return List.of();
        }
    }

    @Override
    public Connection connectTo(String hostName, int portNumber) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(hostName, portNumber),
                    WAITING_MILLISECONDS);
            socket.setSoTimeout(WAITING_MILLISECONDS);
            return new SocketConnection(socket);
        } catch (UnknownHostException noSuchHost) {
            closeQuietly(socket);
            throw new Refused("no-connect",
                    "no host of that name", hostName);
        } catch (SocketTimeoutException tookTooLong) {
            closeQuietly(socket);
            throw new Refused("no-connect",
                    "the host did not answer in time", hostName);
        } catch (IOException refused) {
            closeQuietly(socket);
            throw new Refused("no-connect",
                    refused.getMessage() == null ? "the connection failed"
                            : refused.getMessage(),
                    hostName + ":" + portNumber);
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException alreadyGone) {
            // Nothing useful to do: the caller is already being told why the
            // connection could not be made, and this is the tidying up.
        }
    }

    /**
     * One open socket, read and written as bytes.
     *
     * <p>The streams are held rather than fetched per call, because asking a
     * closed socket for its stream throws where reading a closed one answers
     * the end of input, and the second is the behaviour a script can act on.
     */
    private static final class SocketConnection implements Connection {

        private final Socket socket;
        private final InputStream incoming;
        private final OutputStream outgoing;

        private SocketConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.incoming = socket.getInputStream();
            this.outgoing = socket.getOutputStream();
        }

        @Override
        public byte[] read() {
            try {
                byte[] room = new byte[MOST_BYTES_AT_ONCE];
                int arrived = incoming.read(room);
                return arrived <= 0 ? new byte[0] : Arrays.copyOf(room, arrived);
            } catch (SocketTimeoutException nothingCame) {
                return new byte[0];
            } catch (IOException broken) {
                throw new Refused("no-connect",
                        "the connection broke while reading", describe());
            }
        }

        @Override
        public void write(byte[] bytes) {
            try {
                outgoing.write(bytes);
                outgoing.flush();
            } catch (IOException broken) {
                throw new Refused("no-connect",
                        "the connection broke while writing", describe());
            }
        }

        @Override
        public boolean isOpen() {
            return !socket.isClosed() && socket.isConnected();
        }

        @Override
        public void close() {
            closeQuietly(socket);
        }

        private String describe() {
            return socket.getInetAddress() == null
                    ? "the connection"
                    : socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        }
    }
}
