package org.jebol.domain.host;

import java.util.Set;

public interface DigestPort {

    Set<String> methods();

    RunningDigest start(String method);

    byte[] digestOf(String method, byte[] octets);

    byte[] keyedDigestOf(String method, byte[] octets, byte[] key);

    interface RunningDigest {

        void add(byte[] octets, int from, int length);

        byte[] soFar();
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
