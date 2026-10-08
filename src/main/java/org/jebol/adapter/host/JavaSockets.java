package org.jebol.adapter.host;

import org.jebol.domain.host.NetworkPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.Arrays;
import java.util.List;

/** The network as the JDK already provides it, with nothing cached. */
public final class JavaSockets implements NetworkPort {

    private static final int LONGEST_WAIT_BEFORE_GIVING_UP_MILLISECONDS = 30_000;

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
                    LONGEST_WAIT_BEFORE_GIVING_UP_MILLISECONDS);
            socket.setSoTimeout(LONGEST_WAIT_BEFORE_GIVING_UP_MILLISECONDS);
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

    @Override
    public Datagrams bindTo(int portNumber) {
        try {
            DatagramSocket bound = new DatagramSocket(portNumber);
            bound.setSoTimeout(LONGEST_WAIT_BEFORE_GIVING_UP_MILLISECONDS);
            return new BoundDatagrams(bound);
        } catch (SocketException alreadyHeld) {
            throw new Refused("no-connect",
                    alreadyHeld.getMessage() == null
                            ? "that port number could not be bound"
                            : alreadyHeld.getMessage(),
                    String.valueOf(portNumber));
        }
    }

    private record BoundDatagrams(DatagramSocket socket) implements Datagrams {

        @Override
        public void sendTo(String hostName, int portNumber, byte[] bytes) {
            try {
                socket.send(new DatagramPacket(bytes, bytes.length,
                        InetAddress.getByName(hostName), portNumber));
            } catch (UnknownHostException noSuchHost) {
                throw new Refused("no-connect", "no host of that name", hostName);
            } catch (IOException refused) {
                throw new Refused("write-error",
                        refused.getMessage() == null
                                ? "the datagram could not be sent"
                                : refused.getMessage(),
                        hostName + ":" + portNumber);
            }
        }

        @Override
        public byte[] receive() {
            byte[] room = new byte[MOST_BYTES_AT_ONCE];
            DatagramPacket arrived = new DatagramPacket(room, room.length);
            try {
                socket.receive(arrived);
                return Arrays.copyOf(arrived.getData(), arrived.getLength());
            } catch (SocketTimeoutException nothingCame) {
                return new byte[0];
            } catch (IOException broken) {
                throw new Refused("read-error",
                        broken.getMessage() == null
                                ? "the datagram could not be read"
                                : broken.getMessage(),
                        String.valueOf(socket.getLocalPort()));
            }
        }

        @Override
        public boolean isOpen() {
            return !socket.isClosed();
        }

        @Override
        public void close() {
            socket.close();
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException theTidyingUpFailedAndTheCallerIsAlreadyBeingToldWhy) {
        }
    }

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
