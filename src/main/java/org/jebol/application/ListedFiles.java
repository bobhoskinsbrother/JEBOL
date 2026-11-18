package org.jebol.application;

import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

public final class ListedFiles implements FilePort {

    private static final String ROOT = "/";

    private final Set<String> files = new TreeSet<>();

    private final Set<String> directories = new TreeSet<>(Set.of(ROOT));

    private final Function<String, byte[]> reader;

    private ListedFiles(Function<String, byte[]> reader) {
        this.reader = reader;
    }

    public static ListedFiles of(List<String> names, Function<String, byte[]> reader) {
        ListedFiles listed = new ListedFiles(reader);
        for (String name : names) {
            String file = ScriptPaths.absolute(ROOT, name);
            listed.files.add(file);
            for (String parent = ScriptPaths.parent(file); !parent.equals(ROOT); parent = ScriptPaths.parent(parent)) {
                listed.directories.add(parent);
            }
        }
        return listed;
    }

    @Override
    public byte[] readBytes(String path) {
        String file = ScriptPaths.absolute(ROOT, path);
        if (path.endsWith("/") || !files.contains(file)) {
            throw new Denied("cannot-open", "cannot read " + path, path);
        }
        return reader.apply(file.substring(1));
    }

    @Override
    public boolean exists(String path) {
        return isDirectory(path) || (!path.endsWith("/") && files.contains(ScriptPaths.absolute(ROOT, path)));
    }

    @Override
    public boolean isDirectory(String path) {
        return directories.contains(ScriptPaths.directory(ScriptPaths.absolute(ROOT, path)));
    }

    @Override
    public List<String> listFileNames(String path) {
        String directory = ScriptPaths.directory(ScriptPaths.absolute(ROOT, path));
        if (!directories.contains(directory)) {
            throw new Denied("cannot-open", "cannot read the directory at " + path, path);
        }
        Set<String> names = new TreeSet<>();
        directories.stream().filter(held -> isDirectChild(held, directory))
                .forEach(held -> names.add(ScriptPaths.lastName(held) + "/"));
        files.stream().filter(held -> isDirectChild(held, directory))
                .forEach(held -> names.add(ScriptPaths.lastName(held)));
        return List.copyOf(names);
    }

    private static boolean isDirectChild(String held, String directory) {
        return !held.equals(directory) && ScriptPaths.directory(ScriptPaths.parent(held)).equals(directory);
    }

    @Override
    public Optional<FileInformation> describe(String path) {
        String target = ScriptPaths.absolute(ROOT, path);
        if (isDirectory(target)) {
            return Optional.of(FileInformation.describeDirectory(ScriptPaths.directory(target),
                    Optional.empty(), Optional.empty(), Optional.empty()));
        }
        if (path.endsWith("/") || !files.contains(target)) {
            return Optional.empty();
        }
        return Optional.of(FileInformation.describeFile(target, readBytes(target).length,
                Optional.empty(), Optional.empty(), Optional.empty()));
    }

    @Override
    public Value resolvedFile(String path) {
        String target = ScriptPaths.absolute(ROOT, path);
        if (isDirectory(target)) {
            return FileValue.of(ScriptPaths.directory(target));
        }
        return files.contains(target) ? FileValue.of(target) : NoneValue.none();
    }

    @Override
    public String locationFor(String path) {
        String target = ScriptPaths.absolute(ROOT, path);
        return path.endsWith("/") || isDirectory(target) ? ScriptPaths.directory(target) : target;
    }

    @Override
    public String workingDirectory() {
        return ROOT;
    }

    @Override
    public String hostPath(String path) {
        return ScriptPaths.absolute(ROOT, path);
    }

    @Override
    public Optional<String> findScriptPath(String hostPath) {
        return Optional.empty();
    }

    @Override
    public void changeDirectory(String path) {
        throw refuseChange();
    }

    @Override
    public void makeDirectory(String path, boolean andItsParents) {
        throw refuseChange();
    }

    @Override
    public boolean delete(String path) {
        throw refuseChange();
    }

    @Override
    public void rename(String from, String to) {
        throw refuseChange();
    }

    @Override
    public void write(String path, byte[] contents) {
        throw refuseChange();
    }

    @Override
    public void writeAt(String path, long position, byte[] contents) {
        throw refuseChange();
    }

    @Override
    public void appendTo(String path, byte[] contents) {
        throw refuseChange();
    }

    private Denied refuseChange() {
        return new Denied(Denied.READ_ONLY, "the files an app carries may only be read");
    }
}
