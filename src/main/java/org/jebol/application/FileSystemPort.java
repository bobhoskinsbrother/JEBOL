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
        return asADirectoryNameFromTheRoot(here);
    }

    private String asADirectoryNameFromTheRoot(Path target) {
        String inside = root.relativize(target).toString()
                .replace(File.separatorChar, '/');
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

    /**
     * Moves, and names the directory it meant when it cannot.
     *
     * <p>The C rewrites its own argument into the absolute path before it
     * tries the move -- {@code SET_FILE(arg, ser)} -- and the trap then reads
     * that slot, so a relative target comes back absolute in the error. Which
     * is the half a caller acts on: {@code %issues/2446} says nothing about
     * where the interpreter was standing when it failed to find it.
     */
    @Override
    public void changeDirectory(String path) {
        Path target = within(path);
        if (!Files.isDirectory(target)) {
            throw new Denied("cannot-open", path + " is not a directory",
                    asADirectoryNameFromTheRoot(target));
        }
        here = target;
    }

    /**
     * {@code mkdir}, which refuses where anything already answers to the name.
     *
     * <p>The refusal is no-create rather than cannot-open, and the two are not
     * interchangeable to a caller: cannot-open says the path could not be
     * reached, no-create says it was reached and the directory could not be
     * made there. Rebol's own MAKE-DIR relies on the difference -- it asks
     * EXISTS? first and answers the path where a directory is already there,
     * so the only way to reach {@code mkdir} is with something else in the way.
     */
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

    /**
     * {@code rmdir} where the name claims a directory, {@code remove} where it
     * does not -- the split {@code Delete_File} makes -- so
     * {@code delete %f.txt/} fails rather than taking the file away.
     *
     * <p>Nothing there is false rather than a failure, and every other reason
     * it could not be done is a failure. The C reads that off errno: only
     * {@code -ENOENT} comes back as false.
     */
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

    /**
     * A directory's path with the trailing slash that makes it one.
     *
     * <p>{@code OS_Real_Path} stats what it resolved and the comment beside
     * the line is the whole of it: "Append the trailing slash if it is a
     * directory". So the answer is about what is there rather than about how
     * the question was spelled, and a caller may join a name onto it.
     */
    private static String dirized(String written) {
        return written.endsWith("/") ? written : written + "/";
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

    /**
     * The path a script named, or empty when nothing answers to that name.
     *
     * <p>A name ending in a slash claims the thing is a directory, and POSIX
     * checks the claim: {@code stat("f.txt/")} fails with ENOTDIR, so a real
     * R3 answers none for {@code exists? %f.txt/} where it answers
     * {@code file} for {@code exists? %f.txt}. A JVM does not check it --
     * {@link Files#exists} trims the slash before it looks -- so the check
     * belongs here.
     *
     * <p>It runs one way only. A name without a slash claims nothing, so it
     * answers for a directory as readily as for a file, which is what lets
     * {@code read %somewhere} list a directory.
     */
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

    /**
     * The path a script named, resolved beneath the root.
     *
     * <p>A path beginning with a slash counts from this port's root and not
     * from the machine's. That is the vocabulary the port already speaks:
     * {@link #canonicalPathOf} hands a script back a path written with a
     * leading slash, and reading it again has to reach the file it named.
     * Resolving it against the machine instead made the round trip fail, and
     * made {@code cd %/} mean somewhere the script may not go rather than the
     * top of what it can see.
     *
     * <p>Which settles what {@code ..} does at the top: it stays there.
     * {@code /..} is {@code /} on a real filesystem -- {@code change-dir %../}
     * at the root of a machine answers {@code %/} and moves nothing -- so a
     * root that refused it would be telling the script it is somewhere other
     * than the top, and Rebol's own port test moves up from where it is
     * standing and expects to arrive.
     *
     * <p>This is confinement rather than a warning. A climbing path cannot
     * reach outside because there is no outside to reach:
     * {@code %../../../etc/passwd} names {@code /etc/passwd} within the root,
     * which is the same thing {@code %/etc/passwd} names and finds nothing for
     * the same reason. Refusing it would say the same thing about safety and a
     * different thing about where the script is standing, and the second is
     * what a script acts on.
     */
    private Path within(String path) {
        try {
            Deque<String> segments = new ArrayDeque<>();
            if (!path.startsWith("/")) {
                addEach(root.relativize(here).toString()
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

    /**
     * Adds one written path's segments, working the dots out as it goes.
     *
     * <p>A {@code ..} with nothing above it is dropped, which is the clamp:
     * {@link Deque#pollLast} on an empty deque does nothing, so no sequence of
     * them can take the walk above the root.
     */
    private static void addEach(String path, Deque<String> segments) {
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                segments.pollLast();
            } else {
                segments.addLast(segment);
            }
        }
    }
}
