package org.jebol.domain.eval;

import java.time.Instant;
import java.util.Optional;

/**
 * What the host can tell a script about one thing on its filesystem.
 *
 * <p>Six facts and no more, because this is the whole of what QUERY answers.
 * A port offering more would be offering something no script can ask for, and
 * a port offering less would leave QUERY with a field it could not fill.
 *
 * <p>Every fact but the name and the kind is optional, and a fact the host has
 * not got is none for that field alone. A host that knows a file's size and
 * not when it was last read is an ordinary host rather than a broken one, so
 * the absence is modelled here and not treated as a refusal. The refusal is
 * for the service: a script not granted the filesystem cannot ask at all.
 *
 * <p>Specified in {@code spec/embed.allium}.
 *
 * @param name the path, as the interpreter spells it
 * @param isDirectory what QUERY's {@code type} field reports as {@code dir}
 *     or {@code file}
 * @param size the byte count, and empty for a directory or a host that
 *     cannot say. Empty rather than zero, because zero is the real answer an
 *     empty file gives
 * @param modified when it last changed, which QUERY answers to both
 *     {@code modified} and {@code date}
 * @param accessed when it was last read
 * @param created when it came into being
 */
public record FileInformation(
        String name,
        boolean isDirectory,
        Optional<Long> size,
        Optional<Instant> modified,
        Optional<Instant> accessed,
        Optional<Instant> created) {

    public FileInformation {
        if (name == null) {
            throw new IllegalArgumentException("file information needs the name it is about");
        }
        if (size == null || modified == null || accessed == null || created == null) {
            throw new IllegalArgumentException(
                    "an absent fact is an empty optional, never null");
        }
    }

    /**
     * Information about a directory, whose size is absent rather than zero.
     *
     * <p>A directory has no byte count a script can use, and the C reports
     * none for it rather than the size of the entry on disk.
     */
    public static FileInformation directory(
            String name, Optional<Instant> modified,
            Optional<Instant> accessed, Optional<Instant> created) {

        return new FileInformation(name, true, Optional.empty(), modified, accessed, created);
    }

    public static FileInformation file(
            String name, long size, Optional<Instant> modified,
            Optional<Instant> accessed, Optional<Instant> created) {

        return new FileInformation(
                name, false, Optional.of(size), modified, accessed, created);
    }
}
