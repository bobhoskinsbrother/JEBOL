package org.jebol.adapter.host;

import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class FileSystemPort implements FilePort {

    private static final int NO_SUCH_ENTRY = 2;
    private static final int NOT_DIRECTORY = 20;
    private final Path root;
    private final boolean writable;
    private Path relativePathRoot;

    private final Set<String> hostFilesOutsideRoot = new HashSet<>();

    private FileSystemPort(Path root, boolean writable) {
        this.root = root.toAbsolutePath().normalize();
        this.writable = writable;
        this.relativePathRoot = this.root;
    }

    public static FileSystemPort rootFileSystem(Path root) {
        return new FileSystemPort(root, true);
    }

    public FileSystemPort readOnlyFileSystem() {
        return new FileSystemPort(root, false);
    }

    @Override
    public String workingDirectory() {
        return workingDirectory(relativePathRoot);
    }

    private String workingDirectory(Path target) {
        String relativePath = withForwardSlashes(root.relativize(target));
        return relativePath.isEmpty() ? "/" : "/" + relativePath + "/";
    }

    @Override
    public String hostPath(String path) {
        return resolve(path).beforeLinks().toString();
    }

    @Override
    public void acceptFileFromOutsideRoot(String hostFile) {
        hostFilesOutsideRoot.add(hostFile);
    }

    @Override
    public void changeDirectory(String path) {
        ResolvedPath target = resolve(path);
        if (!Files.isDirectory(target.afterLinks())) {
            throw new Denied("cannot-open", path + " is not a directory",
                    workingDirectory(target.beforeLinks()),
                    Optional.of(Files.exists(target.beforeLinks()) ? -NOT_DIRECTORY : -NO_SUCH_ENTRY));
        }
        relativePathRoot = target.beforeLinks();
    }

    @Override
    public void makeDirectory(String path, boolean createAllParents) {
        refuseIfReadOnly();
        Path target = resolve(path).entry();
        if (Files.exists(target) && !Files.isDirectory(target)) {
            throw new Denied("no-create", "cannot make a directory at " + path, path, Optional.empty());
        }
        try {
            if (createAllParents) {
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
        refuseIfReadOnly();
        Path target = resolve(path).entry();
        if (hasTrailingSlash(path) && !Files.isDirectory(target)) {
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
        refuseIfReadOnly();
        try {
            Files.move(resolve(from).entry(), resolve(to).entry());
        } catch (IOException refused) {
            throw new Denied("cannot-open", "cannot rename " + from);
        }
    }

    @Override
    public List<String> listFileNames(String path) {
        Path target = resolve(path).afterLinks();
        try (Stream<Path> names = Files.list(target)) {
            return names.map(filePath -> Files.isDirectory(filePath)
                            ? filePath.getFileName() + "/"
                            : filePath.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            throw new Denied("cannot-open", "cannot read the directory at " + path, path);
        }
    }

    @Override
    public boolean isDirectory(String path) {
        return findExisting(path).filter(Files::isDirectory).isPresent();
    }

    @Override
    public Value resolvedFile(String path) {
        if (path.isEmpty()) {
            return NoneValue.none();
        }
        try {
            return resolve(path).resolvedFile();
        } catch (Denied cannotBeResolved) {
            return NoneValue.none();
        }
    }

    @Override
    public String locationFor(String path) {
        return resolve(path).locationFromRoot(hasTrailingSlash(path));
    }

    private String asLocationFromRoot(Path real, boolean writtenAsDirectory) {
        String written = "/" + withForwardSlashes(findRealRootPath().relativize(real));
        return writtenAsDirectory || Files.isDirectory(real) ? ensureTrailingSlash(written) : written;
    }

    private String ensureTrailingSlash(String written) {
        return written.endsWith("/") ? written : written + "/";
    }

    @Override
    public Optional<FileInformation> describe(String path) {
        Optional<Path> found = findExisting(path);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        try {
            BasicFileAttributes read = Files.readAttributes(found.get(), BasicFileAttributes.class);
            String name = asRebolPath(resolve(path).beforeLinks(), read.isDirectory());
            Optional<Instant> modified = Optional.of(read.lastModifiedTime().toInstant());
            Optional<Instant> accessed = Optional.of(read.lastAccessTime().toInstant());
            Optional<Instant> created = Optional.of(read.creationTime().toInstant());
            return Optional.of(read.isDirectory()
                    ? FileInformation.describeADirectory(name, modified, accessed, created)
                    : FileInformation.describeAFile(name, read.size(), modified, accessed, created));
        } catch (IOException unreadable) {
            throw new Denied("cannot-open", "cannot read the details of " + path);
        }
    }

    private String asRebolPath(Path target, boolean isDirectory) {
        String relative = withForwardSlashes(relativePathRoot.relativize(target));
        return isDirectory ? ensureTrailingSlash(relative) : relative;
    }

    @Override
    public byte[] readBytes(String path) {
        if (hasTrailingSlash(path)) {
            throw new Denied("cannot-open", "cannot read " + path, path);
        }
        try {
            return Files.readAllBytes(resolve(path).afterLinks());
        } catch (IOException unreadable) {
            throw new Denied("cannot-open", "cannot read " + path, path);
        }
    }

    @Override
    public void appendTo(String path, byte[] contents) {
        refuseIfReadOnly();
        try {
            Files.write(resolve(path).afterLinks(), contents,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException unwritable) {
            throw new Denied("cannot-open", "cannot append to " + path);
        }
    }

    @Override
    public void write(String path, byte[] contents) {
        refuseIfReadOnly();
        try {
            Files.write(resolve(path).afterLinks(), contents);
        } catch (IOException unwritable) {
            throw new Denied("cannot-open", "cannot write " + path);
        }
    }

    @Override
    public void writeAt(String path, long position, byte[] contents) {
        refuseIfReadOnly();
        try (FileChannel channel = FileChannel.open(resolve(path).afterLinks(),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.position(position);
            channel.write(ByteBuffer.wrap(contents));
        } catch (IOException unwritable) {
            throw new Denied("cannot-open", "cannot write " + path);
        }
    }

    private void refuseIfReadOnly() {
        if (!writable) {
            throw new Denied(Denied.READ_ONLY, "this filesystem may only be read");
        }
    }

    @Override
    public boolean exists(String path) {
        return findExisting(path).isPresent();
    }

    private Optional<Path> findExisting(String path) {
        try {
            Path landed = resolve(path).afterLinks();
            boolean isThere = hasTrailingSlash(path) ? Files.isDirectory(landed) : Files.exists(landed);
            return isThere ? Optional.of(landed) : Optional.empty();
        } catch (Denied refused) {
            if (refused.isSecurityRefusal()) {
                return Optional.empty();
            }
            throw refused;
        }
    }

    private boolean hasTrailingSlash(String path) {
        return path.endsWith("/");
    }

    private String withForwardSlashes(Path relative) {
        return relative.toString().replace(File.separatorChar, '/');
    }

    private ResolvedPath resolve(String path) {
        if (hostFilesOutsideRoot.contains(path)) {
            return new HostFileOutsideRoot(path);
        }
        return new ContainedPath(placeUnderRoot(path), path);
    }

    private Path placeUnderRoot(String path) {
        try {
            Deque<String> segments = new ArrayDeque<>();
            if (!path.startsWith("/")) {
                addEach(withForwardSlashes(root.relativize(relativePathRoot)), segments);
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

    private Path followLinksFrom(Path underRoot, String writtenPath) {
        Path realRoot = findRealRootPath();
        Path landed = new LinksFollowed(writtenPath).from(realRoot, root.relativize(underRoot));
        if (!landed.startsWith(realRoot)) {
            throw denyOutsideRoot(writtenPath);
        }
        return landed;
    }

    private Path findRealRootPath() {
        try {
            return root.toRealPath();
        } catch (IOException notThereYet) {
            return root;
        }
    }

    private Denied denyOutsideRoot(String path) {
        return new Denied(Denied.SECURITY, path + " leads out of what this script may reach",
                path, Optional.empty());
    }

    private interface ResolvedPath {

        Path beforeLinks();

        Path afterLinks();

        Path entry();

        String locationFromRoot(boolean writtenAsDirectory);

        Value resolvedFile();
    }

    private record HostFileOutsideRoot(String writtenPath) implements ResolvedPath {

        @Override
        public Path beforeLinks() {
            return Path.of(writtenPath);
        }

        @Override
        public Path afterLinks() {
            return beforeLinks();
        }

        @Override
        public Path entry() {
            return beforeLinks();
        }

        @Override
        public String locationFromRoot(boolean writtenAsDirectory) {
            return writtenPath;
        }

        @Override
        public Value resolvedFile() {
            return FileValue.of(writtenPath);
        }
    }

    private final class ContainedPath implements ResolvedPath {

        private final String writtenPath;

        private final Path root;

        private ContainedPath(Path root, String writtenPath) {
            this.root = root;
            this.writtenPath = writtenPath;
        }

        @Override
        public Path beforeLinks() {
            return root;
        }

        @Override
        public Path afterLinks() {
            return followLinksFrom(root, writtenPath);
        }

        @Override
        public Path entry() {
            if (root.equals(FileSystemPort.this.root)) {
                return afterLinks();
            }
            return followLinksFrom(root.getParent(), writtenPath).resolve(root.getFileName().toString());
        }

        @Override
        public String locationFromRoot(boolean writtenAsDirectory) {
            return asLocationFromRoot(afterLinks(), writtenAsDirectory);
        }

        @Override
        public Value resolvedFile() {
            Path real = afterLinks();
            return Files.exists(real) ? FileValue.of(asLocationFromRoot(real, false)) : NoneValue.none();
        }
    }

    private final class LinksFollowed {

        private static final int MOST_LINKS_FOLLOWED = 40;

        private final String writtenPath;

        private final Deque<String> namesStillToWalk = new ArrayDeque<>();

        private int linksFollowed;

        private LinksFollowed(String writtenPath) {
            this.writtenPath = writtenPath;
        }

        private Path from(Path start, Path names) {
            putInFront(names);
            Path current = start;
            while (!namesStillToWalk.isEmpty()) {
                current = stepFrom(current, namesStillToWalk.removeFirst());
            }
            return toRealPathAllowingMissingTail(current);
        }

        private Path stepFrom(Path current, String name) {
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
            Path target = followSymLink(next);
            putInFront(target);
            return target.isAbsolute() ? target.getRoot() : current;
        }

        private Path followSymLink(Path link) {
            linksFollowed++;
            if (linksFollowed > MOST_LINKS_FOLLOWED) {
                throw denyOutsideRoot(writtenPath);
            }
            try {
                return Files.readSymbolicLink(link);
            } catch (IOException unreadable) {
                throw denyOutsideRoot(writtenPath);
            }
        }

        private void putInFront(Path names) {
            for (int at = names.getNameCount() - 1; at >= 0; at--) {
                namesStillToWalk.addFirst(names.getName(at).toString());
            }
        }

        private Path toRealPathAllowingMissingTail(Path walked) {
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

    private void addEach(String path, Deque<String> segments) {
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                dropSegmentAboveOrStayAtRoot(segments);
            } else {
                segments.addLast(segment);
            }
        }
    }

    private void dropSegmentAboveOrStayAtRoot(Deque<String> segments) {
        segments.pollLast();
    }
}
