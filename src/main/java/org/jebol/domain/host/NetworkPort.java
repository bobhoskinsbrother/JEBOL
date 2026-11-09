package org.jebol.domain.host;

import java.util.Optional;

/**
 * Where a script's network reaches, and nowhere else.
 *
 * <p>A port the domain owns and an adapter implements, so the evaluator never
 * touches a socket. There is no default implementation on purpose, for the
 * same reason {@link FilePort} has none: a host that has not thought about
 * what a script may connect to has not decided that it may connect to
 * anything.
 *
 * <p>Two capabilities rather than one, because a host may reasonably want the
 * first without the second. Resolving a name is a lookup that leaks the name
 * and costs one round trip; a connection is a conversation that lasts and can
 * carry anything.
 */
public interface NetworkPort {

    /**
     * The first IPv4 address a name stands for, written with dots, or empty.
     *
     * <p>Empty rather than a thrown failure, because "there is no such host"
     * is a true answer a script has to act on and one it will meet often. The
     * refusal is for the service not being granted, and it happens before
     * this is reached.
     */
    Optional<String> resolveFirstIpv4Address(String hostName);

    /** The name an IPv4 address, written with dots, belongs to, or empty. */
    Optional<String> resolveHostName(String ipv4Address);

    /** What this machine calls itself. */
    String hostName();

    /**
     * The name a host written in Unicode is registered as, by UTS #46 without
     * transitional processing, or empty when UTS #46 refuses it. A name
     * already in ASCII answers itself.
     */
    Optional<String> asciiHostName(String hostName);

    /**
     * Starts looking a name up and returns at once. The answer arrives on a
     * thread of the host's, through the lookup given.
     */
    void lookUp(String hostName, Lookup answer);

    /**
     * Starts connecting to an address and returns at once. Everything that
     * happens to the connection arrives on threads of the host's, through the
     * events given, each once and in the order it happened.
     */
    Connection connect(String address, int portNumber, ConnectionEvents events);

    /**
     * Listens on a port number, zero asking for any free one, or refuses
     * saying why it could not. Each client that reaches it is handed to the
     * accepting given, which answers where that client's events go.
     */
    Listener listen(int portNumber, Accepting accepting);

    Datagrams bindTo(int portNumber);

    interface Datagrams {

        void sendTo(String hostName, int portNumber, byte[] bytes);

        byte[] receive();

        boolean isOpen();

        void close();
    }

    interface Lookup {

        void found(String ipv4Address);

        void missing(String why);
    }

    interface ConnectionEvents {

        void connected();

        void arrived(byte[] bytes);

        void wrote();

        void closed();

        void failed(String why);
    }

    /**
     * One connection, connecting or connected. Each call starts something and
     * returns at once: a read reports once, through the events, what arrived
     * or that the far end closed, and a write reports once that it has gone.
     */
    interface Connection {

        void read();

        void write(byte[] bytes);

        void close();

        Endpoints endpoints();
    }

    record Endpoints(String localAddress, int localPort, String remoteAddress, int remotePort) {
    }

    interface Accepting {

        ConnectionEvents accepted(Connection client);
    }

    interface Listener {

        int localPort();

        void close();
    }

    /** Why a connection could not be made. Carries an error id the boundary reports. */
    final class Refused extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient String errorId;
        private final transient String subject;

        public Refused(String errorId, String because, String subject) {
            super(because, null, false, false);
            this.errorId = errorId;
            this.subject = subject;
        }

        public String errorId() {
            return errorId;
        }

        public String subject() {
            return subject;
        }
    }

    /** A port that refuses everything, which is what a script gets by default. */
    static NetworkPort none() {
        return new NetworkPort() {

            @Override
            public Optional<String> resolveFirstIpv4Address(String hostName) {
                throw refuse(hostName);
            }

            @Override
            public Optional<String> resolveHostName(String ipv4Address) {
                throw refuse(ipv4Address);
            }

            @Override
            public String hostName() {
                throw refuse("");
            }

            @Override
            public Optional<String> asciiHostName(String hostName) {
                throw refuse(hostName);
            }

            @Override
            public void lookUp(String hostName, Lookup answer) {
                throw refuse(hostName);
            }

            @Override
            public Connection connect(String address, int portNumber, ConnectionEvents events) {
                throw refuse(address);
            }

            @Override
            public Listener listen(int portNumber, Accepting accepting) {
                throw refuse(String.valueOf(portNumber));
            }

            @Override
            public Datagrams bindTo(int portNumber) {
                throw refuse(String.valueOf(portNumber));
            }

            private Refused refuse(String about) {
                return new Refused("no-port",
                        "this interpreter was given no network to reach", about);
            }
        };
    }

    /** What a scheme's default port number is, where it has one. */
    static Optional<Integer> findWellKnownPortFor(String scheme) {
        return switch (scheme) {
            case "http" -> Optional.of(80);
            case "https", "tls" -> Optional.of(443);
            case "smtp" -> Optional.of(25);
            case "pop3" -> Optional.of(110);
            case "whois" -> Optional.of(43);
            case "daytime" -> Optional.of(13);
            default -> Optional.empty();
        };
    }
}
