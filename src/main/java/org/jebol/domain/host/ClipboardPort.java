package org.jebol.domain.host;

import java.util.Optional;

public interface ClipboardPort {

    Optional<String> read();

    boolean write(String text);

    final class Unreachable extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public Unreachable(String because) {
            super(because, null, false, false);
        }
    }

    static ClipboardPort none() {
        return new ClipboardPort() {

            @Override
            public Optional<String> read() {
                throw refuse();
            }

            @Override
            public boolean write(String text) {
                throw refuse();
            }

            private Unreachable refuse() {
                return new Unreachable(
                        "this interpreter was given no clipboard to reach");
            }
        };
    }
}
