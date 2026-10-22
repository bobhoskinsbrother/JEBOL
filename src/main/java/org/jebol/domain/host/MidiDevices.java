package org.jebol.domain.host;

import java.util.List;

public interface MidiDevices {

    List<String> inputs();

    List<String> outputs();

    Connection connect(MidiRoute route);

    interface Connection {

        byte[] arrivedSinceLastRead();

        void send(byte[] message);

        void close();
    }

    final class Declined extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public Declined(String because) {
            super(because, null, false, false);
        }
    }

    final class Refused extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int devicesOwnNumber;

        public Refused(int devicesOwnNumber, String because) {
            super(because, null, false, false);
            this.devicesOwnNumber = devicesOwnNumber;
        }

        public int devicesOwnNumber() {
            return devicesOwnNumber;
        }
    }
}
