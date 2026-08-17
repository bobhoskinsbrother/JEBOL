package org.jebol.domain.eval;

import java.util.List;
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
     * The addresses a name stands for, or an empty list.
     *
     * <p>Empty rather than a thrown failure, because "there is no such host"
     * is a true answer a script has to act on and one it will meet often. The
     * refusal is for the service not being granted, and it happens before
     * this is reached.
     */
    List<String> addressesFor(String hostName);

    /**
     * A connection to a host and port, or a refusal saying why it could not
     * be made.
     *
     * <p>Unlike a name lookup, a connection that was refused, timed out or
     * was reset is a failure of the thing the script asked for rather than an
     * answer to it.
     */
    Connection connectTo(String hostName, int portNumber);

    /** One open connection, which a script drives through an ordinary port. */
    interface Connection {

        /**
         * The bytes that have arrived, waiting until some have.
         *
         * <p>An empty answer means the other end has finished and closed,
         * which is how a reader knows to stop rather than waiting for ever.
         */
        byte[] read();

        void write(byte[] bytes);

        boolean isOpen();

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
            public List<String> addressesFor(String hostName) {
                throw refuse(hostName);
            }

            @Override
            public Connection connectTo(String hostName, int portNumber) {
                throw refuse(hostName);
            }

            private Refused refuse(String about) {
                return new Refused("no-port",
                        "this interpreter was given no network to reach", about);
            }
        };
    }

    /** What a scheme's default port number is, where it has one. */
    static Optional<Integer> wellKnownPortFor(String scheme) {
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
