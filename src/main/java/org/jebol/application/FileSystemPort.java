package org.jebol.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.jebol.domain.eval.FileInformation;
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
     */
    @Override
    public String workingDirectory() {
        return here.toString().endsWith("/") ? here.toString() : here + "/";
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
            // Resolved against where the script is now and not against
            // the root, so CHANGE-DIR means something. The test is still
            // against the root, thus moving cannot widen what is reachable.
            Path resolved = here.resolve(path).toAbsolutePath().normalize();
            if (!resolved.startsWith(root)) {
                throw new Denied("outside-root", path + " is outside what this port allows");
            }
            return resolved;
        } catch (InvalidPathException malformed) {
            throw new Denied("outside-root", path + " is not a path this port can resolve");
        }
    }
}
