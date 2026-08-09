package org.jebol.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.jebol.domain.eval.FilePort;

/**
 * A filesystem a script may reach, rooted at one directory.
 *
 * <p>The root is a boundary rather than a starting point. A path that
 * resolves outside it is refused, whether it got there by climbing with
 * {@code ..} or by being absolute in the first place, because a script that
 * can name {@code /etc/passwd} has the whole disk regardless of where it was
 * told to start.
 */
public final class FileSystemPort implements FilePort {

    private final Path root;
    private final boolean writable;

    private FileSystemPort(Path root, boolean writable) {
        this.root = root.toAbsolutePath().normalize();
        this.writable = writable;
    }

    /** A port allowing reading and writing beneath one directory. */
    public static FileSystemPort rootedAt(Path root) {
        return new FileSystemPort(root, true);
    }

    /** The same port, refusing writes. */
    public FileSystemPort readOnly() {
        return new FileSystemPort(root, false);
    }

    @Override
    public String read(String path) {
        Path file = within(path);
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new Denied("cannot-open", "cannot read " + path);
        }
    }

    @Override
    public void write(String path, String contents) {
        if (!writable) {
            throw new Denied("read-only", "this filesystem may only be read");
        }
        Path file = within(path);
        try {
            Files.writeString(file, contents, StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new Denied("cannot-open", "cannot write " + path);
        }
    }

    @Override
    public boolean exists(String path) {
        return Files.exists(within(path));
    }

    /**
     * The path a script named, resolved beneath the root, or a refusal.
     *
     * <p>Checked after normalising, because {@code a/../../b} only looks like
     * it stays inside until the dots are worked out.
     */
    private Path within(String path) {
        try {
            Path resolved = root.resolve(path).toAbsolutePath().normalize();
            if (!resolved.startsWith(root)) {
                throw new Denied("outside-root", path + " is outside what this port allows");
            }
            return resolved;
        } catch (InvalidPathException malformed) {
            throw new Denied("outside-root", path + " is not a path this port can resolve");
        }
    }
}
