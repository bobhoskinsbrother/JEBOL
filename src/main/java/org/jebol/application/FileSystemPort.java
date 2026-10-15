package org.jebol.application;

import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A filesystem a script may reach, rooted at one directory.
 *
 * <p>The root is a boundary rather than a starting point. Every path resolves
 * beneath it: a leading slash counts from the root rather than from the
 * machine, and a {@code ..} with nothing above it is dropped rather than
 * refused, so a climbing path names something inside and finds it or does not.
 */
public final class FileSystemPort implements FilePort {

    private static final int NO_SUCH_ENTRY = 2;

    private static final int NOT_A_DIRECTORY = 20;

    private final Path root;
    private final boolean writable;

    private Path whereARelativePathCountsFromNow;

    private final Set<String> hostFilesSeenAtTheirOwnPath = new HashSet<>();

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
    public void showsAtItsOwnPath(String hostFile) {
        hostFilesSeenAtTheirOwnPath.add(hostFile);
    }

    @Override
    public void changeDirectory(String path) {
        Path target = within(path);
        if (!Files.isDirectory(reached(path))) {
            throw new Denied("cannot-open", path + " is not a directory",
                    asADirectoryNameFromTheRoot(target),
                    Optional.of(Files.exists(target) ? -NOT_A_DIRECTORY : -NO_SUCH_ENTRY));
        }
        whereARelativePathCountsFromNow = target;
    }

    @Override
    public void makeDirectory(String path, boolean andItsParents) {
        requireWritable();
        Path target = theEntryItself(path);
        if (Files.exists(target) && !Files.isDirectory(target)) {
            throw new Denied("no-create", "cannot make a directory at " + path, path, Optional.empty());
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
        Path target = theEntryItself(path);
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
            Files.move(theEntryItself(from), theEntryItself(to));
        } catch (IOException refused) {
            throw new Denied("cannot-open", "cannot rename " + from);
        }
    }

    @Override
    public java.util.List<String> namesIn(String path) {
        Path target = reached(path);
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
        return whatTheNameClaimsIsThere(path).filter(Files::isDirectory).isPresent();
    }

    @Override
    public String canonicalPathOf(String path) {
        if (path.isEmpty()) {
            return null;
        }
        if (hostFilesSeenAtTheirOwnPath.contains(path)) {
            return path;
        }
        try {
            Path real = reached(path);
            return Files.exists(real) ? asALocationFromTheRoot(real, theNameWithoutASlash(path)) : null;
        } catch (Denied cannotBeResolved) {
            return null;
        }
    }

    @Override
    public String realLocationOf(String path) {
        if (hostFilesSeenAtTheirOwnPath.contains(path)) {
            return path;
        }
        return asALocationFromTheRoot(reached(path), path);
    }

    private String theNameWithoutASlash(String path) {
        return namesADirectory(path) ? path.substring(0, path.length() - 1) : path;
    }

    private String asALocationFromTheRoot(Path real, String asWritten) {
        String written = "/" + theRealRoot().relativize(real).toString().replace(File.separatorChar, '/');
        return namesADirectory(asWritten) || Files.isDirectory(real) ? dirized(written) : written;
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
            String name = asRebolPath(within(path), read.isDirectory());
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
            return Files.readAllBytes(reached(path));
        } catch (IOException unreadable) {
            throw new Denied("cannot-open", "cannot read " + path, path);
        }
    }

    @Override
    public void appendTo(String path, byte[] contents) {
        refuseWhenReadOnly();
        try {
            Files.write(reached(path), contents,
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
            Files.write(reached(path), contents);
        } catch (IOException unwritable) {
            throw new Denied("cannot-open", "cannot write " + path);
        }
    }

    @Override
    public void writeAt(String path, long position, byte[] contents) {
        refuseWhenReadOnly();
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(
                reached(path),
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
        Optional<Path> target = reachedFromInside(path);
        if (target.isPresent() && (namesADirectory(path)
                ? Files.isDirectory(target.get())
                : Files.exists(target.get()))) {
            return target;
        }
        return java.util.Optional.empty();
    }

    private Optional<Path> reachedFromInside(String path) {
        try {
            return Optional.of(reached(path));
        } catch (Denied refused) {
            if (refused.isASecurityRefusal()) {
                return Optional.empty();
            }
            throw refused;
        }
    }

    private static boolean namesADirectory(String path) {
        return path.endsWith("/");
    }

    private Path within(String path) {
        if (hostFilesSeenAtTheirOwnPath.contains(path)) {
            return Path.of(path);
        }
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

    private Path reached(String path) {
        if (hostFilesSeenAtTheirOwnPath.contains(path)) {
            return Path.of(path);
        }
        return reachedThrough(within(path), path);
    }

    private Path reachedThrough(Path lexical, String asWritten) {
        Path realRoot = theRealRoot();
        Path landed = new LinksFollowed(asWritten).from(realRoot, root.relativize(lexical));
        if (!landed.startsWith(realRoot)) {
            throw outsideTheRoot(asWritten);
        }
        return landed;
    }

    private Path theEntryItself(String path) {
        Path lexical = within(path);
        if (hostFilesSeenAtTheirOwnPath.contains(path) || lexical.equals(root)) {
            return reached(path);
        }
        return reachedThrough(lexical.getParent(), path).resolve(lexical.getFileName().toString());
    }

    private Path theRealRoot() {
        try {
            return root.toRealPath();
        } catch (IOException notThereYet) {
            return root;
        }
    }

    private Denied outsideTheRoot(String path) {
        return new Denied(Denied.SECURITY, path + " leads out of what this script may reach",
                path, Optional.empty());
    }

    private final class LinksFollowed {

        private static final int THE_MOST_LINKS_FOLLOWED = 40;

        private final String asWritten;

        private final Deque<String> namesStillToWalk = new ArrayDeque<>();

        private int linksFollowed;

        private LinksFollowed(String asWritten) {
            this.asWritten = asWritten;
        }

        private Path from(Path start, Path names) {
            putInFront(names);
            Path current = start;
            while (!namesStillToWalk.isEmpty()) {
                current = oneStepFrom(current, namesStillToWalk.removeFirst());
            }
            return withTheNamesTheMachineGivesIt(current);
        }

        private Path oneStepFrom(Path current, String name) {
            if (name.isEmpty() || name.equals(".")) {
                return current;
            }
            if (name.equals("..")) {
                return current.getParent() == null ? current : current.getParent();
            }
            Path next = current.resolve(name);
            if (!Files.isSymbolicLink(next)) {
                return next;
            }
            Path target = whereTheLinkPoints(next);
            putInFront(target);
            return target.isAbsolute() ? target.getRoot() : current;
        }

        private Path whereTheLinkPoints(Path link) {
            linksFollowed++;
            if (linksFollowed > THE_MOST_LINKS_FOLLOWED) {
                throw outsideTheRoot(asWritten);
            }
            try {
                return Files.readSymbolicLink(link);
            } catch (IOException unreadable) {
                throw outsideTheRoot(asWritten);
            }
        }

        private void putInFront(Path names) {
            for (int at = names.getNameCount() - 1; at >= 0; at--) {
                namesStillToWalk.addFirst(names.getName(at).toString());
            }
        }

        private Path withTheNamesTheMachineGivesIt(Path walked) {
            Path existing = walked;
            while (existing != null && !Files.exists(existing)) {
                existing = existing.getParent();
            }
            if (existing == null) {
                return walked;
            }
            try {
                return existing.toRealPath().resolve(existing.relativize(walked));
            } catch (IOException unresolvable) {
                return walked;
            }
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
