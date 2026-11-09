package org.jebol.adapter.host;

import org.jebol.domain.host.NetworkPort;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** The network as the JDK already provides it, with nothing cached. */
public final class JavaSocket implements NetworkPort {

    private static final int TIMEOUT_IN_MILLISECONDS = 30_000;

    private static final int MAX_BYTES = 65_536;

    private static final int LAST_ASCII_PLUS_ONE = 0x80;

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
    public Optional<String> asciiHostName(String hostName) {
        return hostName.chars().allMatch(each -> each < LAST_ASCII_PLUS_ONE)
                ? Optional.of(hostName)
                : Uts46.toAsciiHostName(hostName);
    }

    @Override
    public void lookUp(String hostName, Lookup answer) {
        Thread.ofVirtual().start(() -> resolveFirstIpv4Address(hostName).ifPresentOrElse(
                answer::found, () -> answer.missing("no host of that name")));
    }

    @Override
    public Connection connect(String address, int portNumber, ConnectionEvents events) {
        SocketConnection connection = new SocketConnection(new Socket(), events);
        connection.connectTo(new InetSocketAddress(address, portNumber));
        return connection;
    }

    @Override
    public Listener listen(int portNumber, Accepting accepting) {
        try {
            ServerSocket listening = new ServerSocket(portNumber);
            Thread.ofVirtual().start(() -> acceptEachClient(listening, accepting));
            return new Listener() {

                @Override
                public int localPort() {
                    return listening.getLocalPort();
                }

                @Override
                public void close() {
                    try {
                        listening.close();
                    } catch (IOException ignored) {
                    }
                }
            };
        } catch (IOException cannotListen) {
            throw new Refused("no-connect",
                    cannotListen.getMessage() == null ? "that port number could not be listened on" : cannotListen.getMessage(),
                    String.valueOf(portNumber));
        }
    }

    private void acceptEachClient(ServerSocket listening, Accepting accepting) {
        while (!listening.isClosed()) {
            try {
                Socket client = listening.accept();
                SocketConnection accepted = new SocketConnection(client, null);
                accepted.startWorkingAsConnected(accepting.accepted(accepted));
            } catch (IOException closedOrFailed) {
                return;
            }
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
        private final ExecutorService connectingThenWriting = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
        private final ExecutorService reading = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
        private final CompletableFuture<Boolean> connected = new CompletableFuture<>();
        private volatile ConnectionEvents events;
        private volatile boolean closedByScript;

        private SocketConnection(Socket socket, ConnectionEvents events) {
            this.socket = socket;
            this.events = events;
        }

        private void connectTo(InetSocketAddress address) {
            connectingThenWriting.execute(() -> {
                try {
                    socket.connect(address, TIMEOUT_IN_MILLISECONDS);
                    connected.complete(true);
                    report(ConnectionEvents::connected);
                } catch (IOException refused) {
                    connected.complete(false);
                    report(happened -> happened.failed(explain(refused, "the connection failed")));
                }
            });
        }

        private void startWorkingAsConnected(ConnectionEvents accepted) {
            events = accepted;
            connected.complete(true);
        }

        @Override
        public void read() {
            reading.execute(() -> {
                if (!connected.join()) {
                    return;
                }
                try {
                    byte[] room = new byte[MAX_BYTES];
                    int arrived = socket.getInputStream().read(room);
                    if (arrived < 0) {
                        report(ConnectionEvents::closed);
                    } else {
                        byte[] bytes = Arrays.copyOf(room, arrived);
                        report(happened -> happened.arrived(bytes));
                    }
                } catch (IOException broken) {
                    report(happened -> happened.failed(explain(broken, "the connection broke while reading")));
                }
            });
        }

        @Override
        public void write(byte[] bytes) {
            byte[] sending = bytes.clone();
            connectingThenWriting.execute(() -> {
                if (!connected.join()) {
                    return;
                }
                try {
                    OutputStream output = socket.getOutputStream();
                    output.write(sending);
                    output.flush();
                    report(ConnectionEvents::wrote);
                } catch (IOException broken) {
                    report(happened -> happened.failed(explain(broken, "the connection broke while writing")));
                }
            });
        }

        @Override
        public void close() {
            closedByScript = true;
            JavaSocket.this.close(socket);
            connectingThenWriting.shutdownNow();
            reading.shutdownNow();
        }

        @Override
        public Endpoints endpoints() {
            return new Endpoints(
                    socket.getLocalAddress() == null ? "0.0.0.0" : socket.getLocalAddress().getHostAddress(),
                    Math.max(socket.getLocalPort(), 0),
                    socket.getInetAddress() == null ? "0.0.0.0" : socket.getInetAddress().getHostAddress(),
                    socket.getPort());
        }

        private void report(Consumer<ConnectionEvents> happened) {
            ConnectionEvents listening = events;
            if (!closedByScript && listening != null) {
                happened.accept(listening);
            }
        }

        private String explain(IOException failure, String otherwise) {
            return failure.getMessage() == null ? otherwise : failure.getMessage();
        }
    }
}
