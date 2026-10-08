package org.jebol.domain.host;

public interface ClipboardPort {

    String read();

    void write(String text);

    final class Unreachable extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public Unreachable(String because) {
            super(because, null, false, false);
        }
    }

    static ClipboardPort none() {
        return new ClipboardPort() {

            @Override
            public String read() {
                throw refuse();
            }

            @Override
            public void write(String text) {
                throw refuse();
            }

            private Unreachable refuse() {
                return new Unreachable(
                        "this interpreter was given no clipboard to reach");
            }
        };
    }
}
