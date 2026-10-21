package org.jebol.adapter.host;

import org.jebol.domain.host.NetworkPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.Arrays;
import java.util.Optional;

/** The network as the JDK already provides it, with nothing cached. */
public final class JavaSocket implements NetworkPort {

    private static final int TIMEOUT_IN_MILLISECONDS = 30_000;

    private static final int MAX_BYTES = 65_536;

    @Override
    public Optional<String> resolveFirstIpv4Address(String hostName) {
        try {
            return Arrays.stream(InetAddress.getAllByName(hostName))
                    .filter(Inet4Address.class::isInstance)
                    .map(InetAddress::getHostAddress)
                    .findFirst();
        } catch (UnknownHostException noSuchHost) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> resolveHostName(String ipv4Address) {
        try {
            String hostName = InetAddress.getByName(ipv4Address).getHostName();
            return hostName.equals(ipv4Address) ? Optional.empty() : Optional.of(hostName);
        } catch (UnknownHostException notAddress) {
            return Optional.empty();
        }
    }

    @Override
    public String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException unnamed) {
            return InetAddress.getLoopbackAddress().getHostName();
        }
    }

    @Override
    public Connection connectTo(String hostName, int portNumber) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(hostName, portNumber), TIMEOUT_IN_MILLISECONDS);
            socket.setSoTimeout(TIMEOUT_IN_MILLISECONDS);
            return new SocketConnection(socket);
        } catch (UnknownHostException noSuchHost) {
            close(socket);
            throw new Refused("no-connect", "no host of that name", hostName);
        } catch (SocketTimeoutException tookTooLong) {
            close(socket);
            throw new Refused("no-connect", "the host did not answer in time", hostName);
        } catch (IOException refused) {
            close(socket);
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
            bound.setSoTimeout(TIMEOUT_IN_MILLISECONDS);
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
            byte[] room = new byte[MAX_BYTES];
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

    private void close(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private final class SocketConnection implements Connection {

        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;

        private SocketConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.input = socket.getInputStream();
            this.output = socket.getOutputStream();
        }

        @Override
        public byte[] read() {
            try {
                byte[] room = new byte[MAX_BYTES];
                int arrived = input.read(room);
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
                output.write(bytes);
                output.flush();
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
            JavaSocket.this.close(socket);
        }

        private String describe() {
            return socket.getInetAddress() == null
                    ? "the connection"
                    : socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        }
    }
}
