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

    /** The directory a relative path counts from, with a slash at its end. */
    String workingDirectory();

    /**
     * Moves to another directory.
     *
     * <p>The directory belongs to the port and not to the Java process. A
     * host runs many interpreters at once and each must be able to sit in
     * a different directory, thus this must not move any other.
     */
    void changeDirectory(String path);

    /** Makes a directory, and says nothing if there is one there already. */
    void makeDirectory(String path, boolean andItsParents);

    /** Removes a file or an empty directory. */
    void delete(String path);

    /** Gives a file another name. */
    void rename(String from, String to);

    /** The names in a directory, each on its own. */
    java.util.List<String> namesIn(String path);

    /** Whether the path names a directory rather than a file. */
    boolean isDirectory(String path);

    /**
     * What the host knows about one thing on its filesystem, or empty when
     * there is nothing there.
     *
     * <p>Empty rather than a refusal, because "there is nothing there" is an
     * answer a script has to be able to act on: Rebol's own DELETE-DIR leans
     * on it. The refusal is for the service, and it happens before this is
     * reached.
     *
     * <p>What QUERY answers, and the only thing that answers it. SIZE? and
     * MODIFIED? are one line each over QUERY rather than separate crossings
     * of the boundary, so there is one place for the host to be asked and one
     * place for it to be wrong.
     */
    java.util.Optional<FileInformation> informationAbout(String path);

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
            public String workingDirectory() {
                throw refuse();
            }

            @Override
            public void changeDirectory(String path) {
                throw refuse();
            }

            @Override
            public void makeDirectory(String path, boolean andItsParents) {
                throw refuse();
            }

            @Override
            public void delete(String path) {
                throw refuse();
            }

            @Override
            public void rename(String from, String to) {
                throw refuse();
            }

            @Override
            public java.util.List<String> namesIn(String path) {
                throw refuse();
            }

            @Override
            public boolean isDirectory(String path) {
                throw refuse();
            }

            @Override
            public java.util.Optional<FileInformation> informationAbout(String path) {
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
