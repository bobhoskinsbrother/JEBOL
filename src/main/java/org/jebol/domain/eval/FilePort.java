package org.jebol.domain.eval;

/**
 * Where a script's reading and writing goes.
 *
 * <p>A port the domain owns and an adapter implements, so the evaluator never
 * touches a filesystem. There is no default implementation on purpose: a
 * script with no port reaches nothing, because a host that has not thought
 * about what a script may read has not decided that it may read everything.
 *
 * <p>Every method may throw {@link Denied}, which the boundary turns into an
 * ordinary {@code error!} a script could catch.
 */
public interface FilePort {

    /** The contents of a file, as text. */
    String read(String path);

    /** Replaces a file's contents. */
    void write(String path, String contents);

    /** Whether there is anything at that path. */
    boolean exists(String path);

    /** Why a port refused. Carries an error id the boundary reports. */
    final class Denied extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient String errorId;

        public Denied(String errorId, String because) {
            super(because, null, false, false);
            this.errorId = errorId;
        }

        public String errorId() {
            return errorId;
        }
    }

    /** A port that refuses everything, which is what a script gets by default. */
    static FilePort none() {
        return new FilePort() {
            @Override
            public String read(String path) {
                throw refuse();
            }

            @Override
            public void write(String path, String contents) {
                throw refuse();
            }

            @Override
            public boolean exists(String path) {
                throw refuse();
            }

            private Denied refuse() {
                return new Denied("no-port",
                        "this interpreter was given no filesystem to reach");
            }
        };
    }
}
