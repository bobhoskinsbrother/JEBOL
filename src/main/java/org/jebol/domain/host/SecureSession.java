package org.jebol.domain.host;

import java.util.Optional;

public interface SecureSession {

    Exchange begin();

    Exchange receive(byte[] fromNetwork);

    Exchange send(byte[] plaintext);

    Exchange close();

    enum State { HANDSHAKING, OPEN, CLOSED }

    sealed interface Failure {

        record Mistrusted(CertificateMistrust why) implements Failure {
        }

        record Refused(String why) implements Failure {
        }
    }

    record Exchange(byte[] toNetwork, byte[] arrived, State state, Optional<Failure> failure) {

        public static Exchange of(byte[] toNetwork, byte[] arrived, State state) {
            return new Exchange(toNetwork, arrived, state, Optional.empty());
        }

        public static Exchange endedBy(Failure failure, byte[] toNetwork) {
            return new Exchange(toNetwork, new byte[0], State.CLOSED, Optional.of(failure));
        }
    }
}
