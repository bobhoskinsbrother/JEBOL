package org.jebol.application;

import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AHostThatLocksOpenFiles implements FilePort {

    private final FilePort files;
    private final List<String> held = new ArrayList<>();

    public AHostThatLocksOpenFiles(FilePort files) {
        this.files = files;
    }

    public int countHeld() {
        return held.size();
    }

    @Override
    public Held holdOpen(String path) {
        String absolute = ScriptPaths.absolute(files.workingDirectory(), path);
        held.add(absolute);
        return () -> held.remove(absolute);
    }

    private boolean isHeldAtOrBeneath(String path) {
        String absolute = ScriptPaths.absolute(files.workingDirectory(), path);
        return held.stream().anyMatch(one -> one.equals(absolute) || (absolute.endsWith("/") && one.startsWith(absolute)));
    }

    @Override
    public boolean delete(String path) {
        if (isHeldAtOrBeneath(path)) {
            throw new Denied("no-delete", path + " is open, and this host keeps an open file where it is", path);
        }
        return files.delete(path);
    }

    @Override
    public byte[] readBytes(String path) {
        return files.readBytes(path);
    }

    @Override
    public void write(String path, byte[] contents) {
        files.write(path, contents);
    }

    @Override
    public void writeAt(String path, long position, byte[] contents) {
        files.writeAt(path, position, contents);
    }

    @Override
    public void appendTo(String path, byte[] contents) {
        files.appendTo(path, contents);
    }

    @Override
    public void finishWriting(String path) {
        files.finishWriting(path);
    }

    @Override
    public boolean exists(String path) {
        return files.exists(path);
    }

    @Override
    public String workingDirectory() {
        return files.workingDirectory();
    }

    @Override
    public String hostPath(String path) {
        return files.hostPath(path);
    }

    @Override
    public Optional<String> findScriptPath(String hostPath) {
        return files.findScriptPath(hostPath);
    }

    @Override
    public void changeDirectory(String path) {
        files.changeDirectory(path);
    }

    @Override
    public void makeDirectory(String path, boolean andItsParents) {
        files.makeDirectory(path, andItsParents);
    }

    @Override
    public void rename(String from, String to) {
        files.rename(from, to);
    }

    @Override
    public List<String> listFileNames(String path) {
        return files.listFileNames(path);
    }

    @Override
    public boolean ignoresCaseInNames() {
        return files.ignoresCaseInNames();
    }

    @Override
    public boolean isDirectory(String path) {
        return files.isDirectory(path);
    }

    @Override
    public Value resolvedFile(String path) {
        return files.resolvedFile(path);
    }

    @Override
    public String locationFor(String path) {
        return files.locationFor(path);
    }

    @Override
    public Optional<FileInformation> describe(String path) {
        return files.describe(path);
    }
}
