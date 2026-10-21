package org.jebol.domain.host;

import java.util.Set;

public interface DigestPort {

    Set<String> methods();

    RunningDigest start(String method);

    byte[] digestOf(String method, byte[] octets);

    byte[] digestWithAKey(String method, byte[] octets, byte[] key);

    interface RunningDigest {

        void add(byte[] octets, int from, int length);

        byte[] soFar();
    }

    static DigestPort none() {
        return new DigestPort() {

            @Override
            public Set<String> methods() {
                throw refuse();
            }

            @Override
            public RunningDigest start(String method) {
                throw refuse();
            }

            @Override
            public byte[] digestOf(String method, byte[] octets) {
                throw refuse();
            }

            @Override
            public byte[] digestWithAKey(String method, byte[] octets, byte[] key) {
                throw refuse();
            }

            private Refused refuse() {
                return new Refused("no-port", "this interpreter was given no way to hash");
            }
        };
    }

    final class Refused extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient String errorId;

        public Refused(String errorId, String because) {
            super(because, null, false, false);
            this.errorId = errorId;
        }

        public String errorId() {
            return errorId;
        }
    }
}
