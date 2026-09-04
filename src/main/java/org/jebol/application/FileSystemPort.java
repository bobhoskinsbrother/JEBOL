package org.jebol.application;

import org.jebol.domain.eval.FileInformation;
import org.jebol.domain.eval.FilePort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

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

    /**
     * Where a relative path counts from now.
     *
     * <p>It starts at the root and moves when a script changes directory.
     * It can never leave the root, because every path goes through the
     * same test.
     */
    private Path here;

    private FileSystemPort(Path root, boolean writable) {
        this.root = root.toAbsolutePath().normalize();
        this.writable = writable;
        this.here = this.root;
    }

    /** A port allowing reading and writing beneath one directory. */
    public static FileSystemPort rootedAt(Path root) {
        return new FileSystemPort(root, true);
    }

    /** The same port, refusing writes. */
    public FileSystemPort readOnly() {
        return new FileSystemPort(root, false);
    }

    /**
     * The directory a relative path counts from.
     *
     * <p>It is the root, and it moves when a script changes directory. A
     * JVM cannot change the working directory of its own process, thus
     * keeping it here is the only way a script can have one at all. It
     * also means one interpreter cannot move another.
     *
     * <p>Reported from this port's own root, as {@link #canonicalPathOf} is,
     * so that a script granted a filesystem rooted at one directory does not
     * learn where that directory sits -- and so that the path it is handed
     * reaches the same place when it hands it back.
     */
    @Override
    public String workingDirectory() {
        String inside = root.relativize(here).toString()
                .replace(java.io.File.separatorChar, '/');
        return inside.isEmpty() ? "/" : "/" + inside + "/";
    }

    /**
     * Where a path this port serves actually sits on the machine.
     *
     * <p>The one place the root is spoken aloud, for the one caller that needs
     * it: a redirect being handed to a program CALL is about to run. That
     * program is outside the sandbox and would make nothing of a path that
     * counts from a root only this port knows.
     */
    @Override
    public String hostPathOf(String path) {
        return within(path).toString();
    }

    @Override
    public void changeDirectory(String path) {
        Path target = within(path);
        if (!Files.isDirectory(target)) {
            throw new Denied("cannot-open", path + " is not a directory");
        }
        here = target;
    }

    @Override
    public void makeDirectory(String path, boolean andItsParents) {
        requireWritable();
        Path target = within(path);
        try {
            if (andItsParents) {
                Files.createDirectories(target);
            } else if (!Files.exists(target)) {
                Files.createDirectory(target);
            }
        } catch (IOException refused) {
            throw new Denied("cannot-open", "cannot make a directory at " + path);
        }
    }

    @Override
    public void delete(String path) {
        requireWritable();
        try {
            Files.delete(within(path));
        } catch (IOException refused) {
            throw new Denied("cannot-open", "cannot delete " + path);
        }
    }

    @Override
    public void rename(String from, String to) {
        requireWritable();
        try {
            Files.move(within(from), within(to));
        } catch (IOException refused) {
            throw new Denied("cannot-open", "cannot rename " + from);
        }
    }

    @Override
    public java.util.List<String> namesIn(String path) {
        Path target = within(path);
        try (java.util.stream.Stream<Path> names = Files.list(target)) {
            return names.map(one -> Files.isDirectory(one)
                            ? one.getFileName() + "/"
                            : one.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            throw new Denied("cannot-open", "cannot read the directory at " + path);
        }
    }

    @Override
    public boolean isDirectory(String path) {
        return Files.isDirectory(within(path));
    }

    /**
     * The canonical absolute path, or null when nothing is there.
     *
     * <p>{@code toRealPath} resolves symbolic links and removes every
     * {@code .} and {@code ..}, which is what TO-REAL-FILE means by real. It
     * refuses a path that does not exist, and that refusal is the answer: null
     * here, none to the script.
     *
     * <p>The result is still reported inside this port's own root rather than
     * as a machine-wide path. A script granted a filesystem rooted at one
     * directory has no business learning where that directory sits.
     *
     * <p>A path outside that root is not resolvable either, and gets the same
     * answer rather than a refusal. Asking where something is is not reaching
     * for it, and none is both the honest reply and the one that tells the
     * asker nothing. Raising instead stopped Rebol's own SECURE at its first
     * line -- it resolves each path exception before storing it -- and took
     * two whole test files with it.
     */
    @Override
    public String canonicalPathOf(String path) {
        try {
            java.nio.file.Path real = within(path).toRealPath();
            java.nio.file.Path relative = root.toRealPath().relativize(real);
            return "/" + relative.toString().replace(
                    java.io.File.separatorChar, '/');
        } catch (java.io.IOException | Denied cannotBeResolved) {
            return null;
        }
    }

    /**
     * What this filesystem knows about one path, or empty when nothing is
     * there.
     *
     * <p>Read in one pass through {@link Files#readAttributes}, because asking
     * separately for the size and each timestamp would let the file change
     * between questions and answer about two different files.
     *
     * <p>A directory reports no size. Java would give the size of the
     * directory entry, which is a number about the filesystem rather than
     * about the directory, and Rebol reports none.
     */
    @Override
    public java.util.Optional<FileInformation> informationAbout(String path) {
        Path target = within(path);
        if (!Files.exists(target)) {
            return java.util.Optional.empty();
        }
        try {
            java.nio.file.attribute.BasicFileAttributes read = Files.readAttributes(
                    target, java.nio.file.attribute.BasicFileAttributes.class);
            String name = asRebolPath(target, read.isDirectory());
            java.util.Optional<java.time.Instant> modified =
                    java.util.Optional.of(read.lastModifiedTime().toInstant());
            java.util.Optional<java.time.Instant> accessed =
                    java.util.Optional.of(read.lastAccessTime().toInstant());
            java.util.Optional<java.time.Instant> created =
                    java.util.Optional.of(read.creationTime().toInstant());
            return java.util.Optional.of(read.isDirectory()
                    ? FileInformation.directory(name, modified, accessed, created)
                    : FileInformation.file(name, read.size(), modified, accessed, created));
        } catch (IOException unreadable) {
            throw new Denied("cannot-open", "cannot read the details of " + path);
        }
    }

    /**
     * The path as a script spells it: relative to where the script is, and
     * with a trailing slash on a directory.
     *
     * <p>Relative rather than absolute because the absolute form names the
     * host's disk layout, which is outside what the script was granted. A
     * script that could read the root's real path could learn where it is
     * confined, which the root exists to prevent.
     */
    private String asRebolPath(Path target, boolean isDirectory) {
        String relative = here.equals(target)
                ? ""
                : here.relativize(target).toString();
        return isDirectory && !relative.endsWith("/") ? relative + "/" : relative;
    }

    /** Refuses a change when this port was made read only. */
    private void requireWritable() {
        if (!writable) {
            throw new Denied("no-permission", "this port does not allow changes");
        }
    }

    @Override
    public byte[] readBytes(String path) {
        Path file = within(path);
        try {
            return Files.readAllBytes(file);
        } catch (IOException unreadable) {
            throw new Denied("cannot-open", "cannot read " + path, path);
        }
    }

    @Override
    public void appendTo(String path, byte[] contents) {
        refuseWhenReadOnly();
        try {
            Files.write(within(path), contents,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException unwritable) {
            throw new Denied("cannot-open", "cannot append to " + path);
        }
    }

    @Override
    public void write(String path, byte[] contents) {
        refuseWhenReadOnly();
        try {
            Files.write(within(path), contents);
        } catch (IOException unwritable) {
            throw new Denied("cannot-open", "cannot write " + path);
        }
    }

    @Override
    public void writeAt(String path, long position, byte[] contents) {
        refuseWhenReadOnly();
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(
                within(path),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(position);
            channel.write(java.nio.ByteBuffer.wrap(contents));
        } catch (IOException unwritable) {
            throw new Denied("cannot-open", "cannot write " + path);
        }
    }

    private void refuseWhenReadOnly() {
        if (!writable) {
            throw new Denied("read-only", "this filesystem may only be read");
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
     *
     * <p>A path beginning with a slash counts from this port's root and not
     * from the machine's. That is the vocabulary the port already speaks:
     * {@link #canonicalPathOf} hands a script back a path written with a
     * leading slash, and reading it again has to reach the file it named.
     * Resolving it against the machine instead made the round trip fail, and
     * made {@code cd %/} mean somewhere the script may not go rather than the
     * top of what it can see.
     */
    private Path within(String path) {
        try {
            Path resolved = path.startsWith("/")
                    ? root.resolve(path.substring(1)).normalize()
                    : here.resolve(path).toAbsolutePath().normalize();
            if (!resolved.startsWith(root)) {
                throw new Denied("outside-root", path + " is outside what this port allows");
            }
            return resolved;
        } catch (InvalidPathException malformed) {
            throw new Denied("outside-root", path + " is not a path this port can resolve");
        }
    }
}
