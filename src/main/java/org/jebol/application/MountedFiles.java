package org.jebol.application;

import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class MountedFiles implements FilePort {

    private final FilePort beneath;
    private final String mountFolder;
    private final FilePort mounted;
    private Optional<String> workingDirectoryInMount = Optional.empty();

    public MountedFiles(FilePort beneath, String mountFolder, FilePort mounted) {
        this.beneath = beneath;
        this.mountFolder = ScriptPaths.directory(mountFolder);
        this.mounted = mounted;
    }

    public MountedFiles copyOver(FilePort anotherBeneath) {
        MountedFiles copy = new MountedFiles(anotherBeneath, mountFolder, mounted);
        copy.workingDirectoryInMount = workingDirectoryInMount;
        return copy;
    }

    @Override
    public byte[] readBytes(String path) {
        return isInMount(path) ? mounted.readBytes(absolute(path)) : beneath.readBytes(beneathPath(path));
    }

    @Override
    public void write(String path, byte[] contents) {
        if (isInMount(path)) {
            mounted.write(absolute(path), contents);
        } else {
            beneath.write(beneathPath(path), contents);
        }
    }

    @Override
    public void writeAt(String path, long position, byte[] contents) {
        if (isInMount(path)) {
            mounted.writeAt(absolute(path), position, contents);
        } else {
            beneath.writeAt(beneathPath(path), position, contents);
        }
    }

    @Override
    public void appendTo(String path, byte[] contents) {
        if (isInMount(path)) {
            mounted.appendTo(absolute(path), contents);
        } else {
            beneath.appendTo(beneathPath(path), contents);
        }
    }

    @Override
    public void finishWriting(String path) {
        if (isInMount(path)) {
            mounted.finishWriting(absolute(path));
        } else {
            beneath.finishWriting(beneathPath(path));
        }
    }

    @Override
    public boolean exists(String path) {
        return isInMount(path) ? mounted.exists(absolute(path)) : beneath.exists(beneathPath(path));
    }

    @Override
    public String workingDirectory() {
        return workingDirectoryInMount.orElseGet(beneath::workingDirectory);
    }

    @Override
    public String hostPath(String path) {
        return isInMount(path) ? mounted.hostPath(absolute(path)) : beneath.hostPath(beneathPath(path));
    }

    @Override
    public Optional<String> findScriptPath(String hostPath) {
        return beneath.findScriptPath(hostPath);
    }

    @Override
    public void acceptFileFromOutsideRoot(String hostFile) {
        beneath.acceptFileFromOutsideRoot(hostFile);
    }

    @Override
    public void changeDirectory(String path) {
        String target = ScriptPaths.directory(absolute(path));
        if (isMountPath(target)) {
            if (!mounted.isDirectory(target)) {
                throw new Denied("cannot-open", path + " is not a directory", target);
            }
            workingDirectoryInMount = Optional.of(target);
            return;
        }
        beneath.changeDirectory(target);
        workingDirectoryInMount = Optional.empty();
    }

    @Override
    public void makeDirectory(String path, boolean andItsParents) {
        if (isInMount(path)) {
            mounted.makeDirectory(absolute(path), andItsParents);
        } else {
            beneath.makeDirectory(beneathPath(path), andItsParents);
        }
    }

    @Override
    public boolean delete(String path) {
        return isInMount(path) ? mounted.delete(absolute(path)) : beneath.delete(beneathPath(path));
    }

    @Override
    public void rename(String from, String to) {
        boolean fromMount = isInMount(from);
        String target = ScriptPaths.absolute(ScriptPaths.parent(absolute(from)), to);
        if (fromMount != isMountPath(target)) {
            throw new Denied("cannot-open", "cannot rename " + from + " across " + mountFolder + "'s edge");
        }
        if (fromMount) {
            mounted.rename(absolute(from), target);
        } else {
            beneath.rename(beneathPath(from), beneathPath(to));
        }
    }

    @Override
    public List<String> listFileNames(String path) {
        if (isInMount(path)) {
            return mounted.listFileNames(absolute(path));
        }
        List<String> names = beneath.listFileNames(beneathPath(path));
        String listed = ScriptPaths.directory(absolute(path));
        if (!listed.equals(ScriptPaths.parent(mountFolder))) {
            return names;
        }
        String mountName = ScriptPaths.lastName(mountFolder) + "/";
        Set<String> withMount = new TreeSet<>(names);
        withMount.remove(mountName);
        withMount.remove(ScriptPaths.lastName(mountFolder));
        if (mounted.isDirectory(mountFolder)) {
            withMount.add(mountName);
        }
        return List.copyOf(withMount);
    }

    @Override
    public boolean isDirectory(String path) {
        return isInMount(path) ? mounted.isDirectory(absolute(path)) : beneath.isDirectory(beneathPath(path));
    }

    @Override
    public Value resolvedFile(String path) {
        if (path.isEmpty() || !isInMount(path)) {
            return beneath.resolvedFile(beneathPath(path));
        }
        return mounted.resolvedFile(absolute(path));
    }

    @Override
    public String locationFor(String path) {
        return isInMount(path) ? mounted.locationFor(absolute(path)) : beneath.locationFor(beneathPath(path));
    }

    @Override
    public Optional<FileInformation> describe(String path) {
        if (!isInMount(path)) {
            return beneath.describe(beneathPath(path));
        }
        return mounted.describe(absolute(path)).map(this::namedFromWorkingDirectory);
    }

    private FileInformation namedFromWorkingDirectory(FileInformation information) {
        return new FileInformation(ScriptPaths.relativeTo(workingDirectory(), information.name()),
                information.isDirectory(), information.size(), information.modified(),
                information.accessed(), information.created());
    }

    private boolean isInMount(String path) {
        return isMountPath(absolute(path));
    }

    private boolean isMountPath(String absolute) {
        return ScriptPaths.directory(absolute).startsWith(mountFolder);
    }

    private String absolute(String path) {
        return ScriptPaths.absolute(workingDirectory(), path);
    }

    private String beneathPath(String path) {
        return workingDirectoryInMount.isPresent() ? absolute(path) : path;
    }
}
