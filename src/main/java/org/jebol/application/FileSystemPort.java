package org.jebol.application;

import org.jebol.domain.eval.FileInformation;
import org.jebol.domain.eval.FilePort;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A filesystem a script may reach, rooted at one directory.
 *
 * <p>The root is a boundary rather than a starting point. Every path resolves
 * beneath it: a leading slash counts from the root rather than from the
 * machine, and a {@code ..} with nothing above it is dropped rather than
 * refused, so a climbing path names something inside and finds it or does not.
 */
public final class FileSystemPort implements FilePort {

    private final Path root;
    private final boolean writable;

    private Path whereARelativePathCountsFromNow;

    private FileSystemPort(Path root, boolean writable) {
        this.root = root.toAbsolutePath().normalize();
        this.writable = writable;
        this.whereARelativePathCountsFromNow = this.root;
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
    public String workingDirectory() {
        return asADirectoryNameFromTheRoot(whereARelativePathCountsFromNow);
    }

    private String asADirectoryNameFromTheRoot(Path target) {
        String inside = root.relativize(target).toString()
                .replace(File.separatorChar, '/');
        return inside.isEmpty() ? "/" : "/" + inside + "/";
    }

    @Override
    public String hostPathOf(String path) {
        return within(path).toString();
    }

    @Override
    public void changeDirectory(String path) {
        Path target = within(path);
        if (!Files.isDirectory(target)) {
            throw new Denied("cannot-open", path + " is not a directory",
                    asADirectoryNameFromTheRoot(target));
        }
        whereARelativePathCountsFromNow = target;
    }

    @Override
    public void makeDirectory(String path, boolean andItsParents) {
        requireWritable();
        Path target = within(path);
        if (Files.exists(target) && !Files.isDirectory(target)) {
            throw new Denied("no-create", "cannot make a directory at " + path, path);
        }
        try {
            if (andItsParents) {
                Files.createDirectories(target);
            } else if (!Files.exists(target)) {
                Files.createDirectory(target);
            }
        } catch (IOException refused) {
            throw new Denied("cannot-open", "cannot make a directory at " + path, path);
        }
    }

    @Override
    public boolean delete(String path) {
        requireWritable();
        Path target = within(path);
        if (namesADirectory(path) && !Files.isDirectory(target)) {
            if (Files.exists(target)) {
                throw new Denied("no-delete", "cannot delete " + path, path);
            }
            return false;
        }
        try {
            return Files.deleteIfExists(target);
        } catch (IOException refused) {
            throw new Denied("no-delete", "cannot delete " + path, path);
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
            throw new Denied("cannot-open", "cannot read the directory at " + path, path);
        }
    }

    @Override
    public boolean isDirectory(String path) {
        return Files.isDirectory(within(path));
    }

    @Override
    public String canonicalPathOf(String path) {
        if (path.isEmpty()) {
            return null;
        }
        try {
            java.nio.file.Path real = within(path).toRealPath();
            java.nio.file.Path relative = root.toRealPath().relativize(real);
            String written = "/" + relative.toString().replace(
                    File.separatorChar, '/');
            return Files.isDirectory(real) ? dirized(written) : written;
        } catch (java.io.IOException | Denied cannotBeResolved) {
            return null;
        }
    }

    private static String dirized(String written) {
        return written.endsWith("/") ? written : written + "/";
    }

    @Override
    public java.util.Optional<FileInformation> informationAbout(String path) {
        java.util.Optional<Path> found = whatTheNameClaimsIsThere(path);
        if (found.isEmpty()) {
            return java.util.Optional.empty();
        }
        Path target = found.get();
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

    private String asRebolPath(Path target, boolean isDirectory) {
        String relative = whereARelativePathCountsFromNow.equals(target)
                ? ""
                : whereARelativePathCountsFromNow.relativize(target).toString();
        return isDirectory && !relative.endsWith("/") ? relative + "/" : relative;
    }

    private void requireWritable() {
        if (!writable) {
            throw new Denied("no-permission", "this port does not allow changes");
        }
    }

    @Override
    public byte[] readBytes(String path) {
        if (namesADirectory(path)) {
            throw new Denied("cannot-open", "cannot read " + path, path);
        }
        try {
            return Files.readAllBytes(within(path));
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
        return whatTheNameClaimsIsThere(path).isPresent();
    }

    private java.util.Optional<Path> whatTheNameClaimsIsThere(String path) {
        Path target = within(path);
        if (namesADirectory(path) ? Files.isDirectory(target) : Files.exists(target)) {
            return java.util.Optional.of(target);
        }
        return java.util.Optional.empty();
    }

    private static boolean namesADirectory(String path) {
        return path.endsWith("/");
    }

    private Path within(String path) {
        try {
            Deque<String> segments = new ArrayDeque<>();
            if (!path.startsWith("/")) {
                addEach(root.relativize(whereARelativePathCountsFromNow).toString()
                        .replace(File.separatorChar, '/'), segments);
            }
            addEach(path, segments);
            Path resolved = root;
            for (String segment : segments) {
                resolved = resolved.resolve(segment);
            }
            return resolved;
        } catch (InvalidPathException malformed) {
            throw new Denied("outside-root", path + " is not a path this port can resolve");
        }
    }

    private static void addEach(String path, Deque<String> segments) {
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                dropTheSegmentAboveOrStayAtTheRoot(segments);
            } else {
                segments.addLast(segment);
            }
        }
    }

    private static void dropTheSegmentAboveOrStayAtTheRoot(Deque<String> segments) {
        segments.pollLast();
    }
}
