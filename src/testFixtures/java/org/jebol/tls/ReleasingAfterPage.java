package org.jebol.tls;

import org.jebol.domain.host.NetworkPort;

import java.util.Optional;

public final class ReleasingAfterPage implements NetworkPort {

    private final NetworkPort network;
    private final LocalTlsServer server;

    public ReleasingAfterPage(NetworkPort network, LocalTlsServer server) {
        this.network = network;
        this.server = server;
    }

    @Override
    public Optional<String> resolveFirstIpv4Address(String hostName) {
        return network.resolveFirstIpv4Address(hostName);
    }

    @Override
    public Optional<String> resolveHostName(String ipv4Address) {
        return network.resolveHostName(ipv4Address);
    }

    @Override
    public String hostName() {
        return network.hostName();
    }

    @Override
    public Optional<String> asciiHostName(String hostName) {
        return network.asciiHostName(hostName);
    }

    @Override
    public boolean allowsCleartextTo(String hostName) {
        return network.allowsCleartextTo(hostName);
    }

    @Override
    public void lookUp(String hostName, Lookup answer) {
        network.lookUp(hostName, answer);
    }

    @Override
    public Connection connect(String address, int portNumber, ConnectionEvents events) {
        WatchedEvents watched = new WatchedEvents(events);
        Connection connection = network.connect(address, portNumber, watched);
        return new Connection() {

            @Override
            public void read() {
                if (watched.pageArrived) {
                    server.release();
                }
                connection.read();
            }

            @Override
            public void write(byte[] bytes) {
                connection.write(bytes);
            }

            @Override
            public void close() {
                connection.close();
            }

            @Override
            public Endpoints endpoints() {
                return connection.endpoints();
            }
        };
    }

    @Override
    public Listener listen(int portNumber, Accepting accepting) {
        return network.listen(portNumber, accepting);
    }

    @Override
    public Datagrams bindTo(int portNumber) {
        return network.bindTo(portNumber);
    }

    private final class WatchedEvents implements ConnectionEvents {

        private final ConnectionEvents events;
        private volatile boolean pageArrived;

        private WatchedEvents(ConnectionEvents events) {
            this.events = events;
        }

        @Override
        public void connected() {
            events.connected();
        }

        @Override
        public void arrived(byte[] bytes) {
            if (server.hasWrittenPage()) {
                pageArrived = true;
            }
            events.arrived(bytes);
        }

        @Override
        public void wrote() {
            events.wrote();
        }

        @Override
        public void closed() {
            events.closed();
        }

        @Override
        public void failed(String why) {
            events.failed(why);
        }
    }
}
