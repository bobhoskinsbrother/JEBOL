package org.jebol.application;

import org.jebol.domain.eval.LocalFileConvention;
import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class DriveLetterFiles implements FilePort {

    private static final String ROOT = "/";

    private final Map<String, FilePort> drives;
    private String workingDirectory;

    public DriveLetterFiles(Map<String, FilePort> drives, String workingDirectory) {
        this.drives = new TreeMap<>();
        drives.forEach((letter, drive) -> this.drives.put(letter.toUpperCase(Locale.ROOT), drive));
        this.workingDirectory = ScriptPaths.directory(workingDirectory);
    }

    public DriveLetterFiles copyInWorkingDirectory() {
        return new DriveLetterFiles(drives, workingDirectory);
    }

    @Override
    public byte[] readBytes(String path) {
        OnADrive on = requireDrive(path);
        return on.drive().readBytes(on.path());
    }

    @Override
    public void write(String path, byte[] contents) {
        OnADrive on = requireDrive(path);
        on.drive().write(on.path(), contents);
    }

    @Override
    public void writeAt(String path, long position, byte[] contents) {
        OnADrive on = requireDrive(path);
        on.drive().writeAt(on.path(), position, contents);
    }

    @Override
    public void appendTo(String path, byte[] contents) {
        OnADrive on = requireDrive(path);
        on.drive().appendTo(on.path(), contents);
    }

    @Override
    public void finishWriting(String path) {
        OnADrive on = requireDrive(path);
        on.drive().finishWriting(on.path());
    }

    @Override
    public Held holdOpen(String path) {
        return findDrive(path).map(on -> on.drive().holdOpen(on.path())).orElse(Held.NOTHING);
    }

    @Override
    public boolean exists(String path) {
        if (isRoot(path)) {
            return true;
        }
        return findDrive(path).map(on -> on.drive().exists(on.path())).orElse(false);
    }

    @Override
    public String workingDirectory() {
        return workingDirectory;
    }

    @Override
    public String hostPath(String path) {
        OnADrive on = requireDrive(path);
        return on.drive().hostPath(on.path());
    }

    @Override
    public Optional<String> findScriptPath(String hostPath) {
        return LocalFileConvention.WINDOWS.readLocalPath(hostPath)
                .filter(written -> written.startsWith(ROOT));
    }

    @Override
    public void changeDirectory(String path) {
        String target = ScriptPaths.directory(absolute(path));
        if (target.equals(ROOT)) {
            workingDirectory = ROOT;
            return;
        }
        OnADrive on = requireDrive(target);
        if (!on.drive().isDirectory(on.path())) {
            throw new Denied("cannot-open", path + " is not a directory", target);
        }
        workingDirectory = target;
    }

    @Override
    public void makeDirectory(String path, boolean andItsParents) {
        OnADrive on = requireDrive(path);
        on.drive().makeDirectory(on.path(), andItsParents);
    }

    @Override
    public boolean delete(String path) {
        OnADrive on = requireDrive(path);
        return on.drive().delete(on.path());
    }

    @Override
    public void rename(String from, String to) {
        OnADrive source = requireDrive(from);
        OnADrive target = requireDrive(to);
        if (source.letter().equalsIgnoreCase(target.letter())) {
            source.drive().rename(source.path(), target.path());
            return;
        }
        if (source.drive().isDirectory(source.path())) {
            throw new Denied("cannot-open", "cannot move the directory " + from + " to another drive");
        }
        target.drive().write(target.path(), source.drive().readBytes(source.path()));
        source.drive().delete(source.path());
    }

    @Override
    public List<String> listFileNames(String path) {
        if (isRoot(path)) {
            return drives.keySet().stream().map(letter -> letter + ROOT).toList();
        }
        OnADrive on = requireDrive(path);
        return on.drive().listFileNames(on.path());
    }

    @Override
    public boolean ignoresCaseInNames() {
        return true;
    }

    @Override
    public boolean isDirectory(String path) {
        if (isRoot(path)) {
            return true;
        }
        return findDrive(path).map(on -> on.drive().isDirectory(on.path())).orElse(false);
    }

    @Override
    public Value resolvedFile(String path) {
        if (isRoot(path)) {
            return FileValue.of(ROOT);
        }
        OnADrive on = requireDrive(path);
        Value resolved = on.drive().resolvedFile(on.path());
        return resolved instanceof FileValue file
                ? FileValue.of(on.putDriveInFront(file.text()))
                : resolved;
    }

    @Override
    public String locationFor(String path) {
        if (isRoot(path)) {
            return ROOT;
        }
        OnADrive on = requireDrive(path);
        return on.putDriveInFront(on.drive().locationFor(on.path()));
    }

    @Override
    public Optional<FileInformation> describe(String path) {
        if (isRoot(path)) {
            return Optional.of(FileInformation.describeDirectory(
                    ROOT, Optional.empty(), Optional.empty(), Optional.empty()));
        }
        return findDrive(path).flatMap(on -> on.drive().describe(on.path())
                .map(described -> namedFromWorkingDirectory(on, described)));
    }

    private FileInformation namedFromWorkingDirectory(OnADrive on, FileInformation described) {
        String fromDriveRoot = described.name().startsWith(ROOT) ? described.name() : ROOT + described.name();
        String name = ScriptPaths.relativeTo(workingDirectory, on.putDriveInFront(fromDriveRoot));
        return new FileInformation(name, described.isDirectory(), described.size(),
                described.modified(), described.accessed(), described.created());
    }

    private boolean isRoot(String path) {
        return absolute(path).equals(ROOT);
    }

    private String absolute(String path) {
        return ScriptPaths.absolute(workingDirectory, path);
    }

    private OnADrive requireDrive(String path) {
        if (isRoot(path)) {
            throw new Denied("cannot-open", path + " is the list of drives, where nothing else can be", path);
        }
        return findDrive(path).orElseThrow(() ->
                new Denied("cannot-open", path + " is on no drive this machine has", path));
    }

    private Optional<OnADrive> findDrive(String path) {
        String withoutRoot = absolute(path).substring(ROOT.length());
        int slash = withoutRoot.indexOf(ROOT);
        String letter = slash < 0 ? withoutRoot : withoutRoot.substring(0, slash);
        String onTheDrive = slash < 0 ? ROOT : withoutRoot.substring(slash);
        return Optional.ofNullable(drives.get(letter.toUpperCase(Locale.ROOT)))
                .map(drive -> new OnADrive(letter, drive, onTheDrive));
    }

    private record OnADrive(String letter, FilePort drive, String path) {

        String putDriveInFront(String fromDriveRoot) {
            return ROOT + letter + fromDriveRoot;
        }
    }
}
