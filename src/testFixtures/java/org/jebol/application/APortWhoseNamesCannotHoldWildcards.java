package org.jebol.application;

import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;

public final class APortWhoseNamesCannotHoldWildcards implements FilePort {

    private final FilePort files;
    private final Optional<Boolean> ignoresCase;

    public APortWhoseNamesCannotHoldWildcards(FilePort files) {
        this(files, Optional.empty());
    }

    private APortWhoseNamesCannotHoldWildcards(FilePort files, Optional<Boolean> ignoresCase) {
        this.files = files;
        this.ignoresCase = ignoresCase;
    }

    public APortWhoseNamesCannotHoldWildcards mindingCase() {
        return new APortWhoseNamesCannotHoldWildcards(files, Optional.of(false));
    }

    public APortWhoseNamesCannotHoldWildcards ignoringCase() {
        return new APortWhoseNamesCannotHoldWildcards(files, Optional.of(true));
    }

    private static boolean holdsWildcard(String path) {
        return path.indexOf('*') >= 0 || path.indexOf('?') >= 0;
    }

    private String refuseWildcards(String path) {
        if (holdsWildcard(path)) {
            throw new Denied(Denied.NAME_NO_FILE_CAN_HAVE, path + " is not a path this port can resolve");
        }
        return path;
    }

    @Override
    public byte[] readBytes(String path) {
        return files.readBytes(refuseWildcards(path));
    }

    @Override
    public void write(String path, byte[] contents) {
        files.write(refuseWildcards(path), contents);
    }

    @Override
    public void writeAt(String path, long position, byte[] contents) {
        files.writeAt(refuseWildcards(path), position, contents);
    }

    @Override
    public void appendTo(String path, byte[] contents) {
        files.appendTo(refuseWildcards(path), contents);
    }

    @Override
    public void finishWriting(String path) {
        files.finishWriting(refuseWildcards(path));
    }

    @Override
    public boolean exists(String path) {
        return !holdsWildcard(path) && files.exists(path);
    }

    @Override
    public String workingDirectory() {
        return files.workingDirectory();
    }

    @Override
    public String hostPath(String path) {
        return files.hostPath(refuseWildcards(path));
    }

    @Override
    public Optional<String> findScriptPath(String hostPath) {
        return files.findScriptPath(hostPath);
    }

    @Override
    public void acceptFileFromOutsideRoot(String hostFile) {
        files.acceptFileFromOutsideRoot(hostFile);
    }

    @Override
    public void changeDirectory(String path) {
        files.changeDirectory(refuseWildcards(path));
    }

    @Override
    public void makeDirectory(String path, boolean andItsParents) {
        files.makeDirectory(refuseWildcards(path), andItsParents);
    }

    @Override
    public boolean delete(String path) {
        return files.delete(refuseWildcards(path));
    }

    @Override
    public void rename(String from, String to) {
        files.rename(refuseWildcards(from), refuseWildcards(to));
    }

    @Override
    public List<String> listFileNames(String path) {
        return files.listFileNames(refuseWildcards(path));
    }

    @Override
    public boolean ignoresCaseInNames() {
        return ignoresCase.orElseGet(files::ignoresCaseInNames);
    }

    @Override
    public boolean isDirectory(String path) {
        return !holdsWildcard(path) && files.isDirectory(path);
    }

    @Override
    public Value resolvedFile(String path) {
        return files.resolvedFile(refuseWildcards(path));
    }

    @Override
    public String locationFor(String path) {
        return files.locationFor(refuseWildcards(path));
    }

    @Override
    public Optional<FileInformation> describe(String path) {
        return holdsWildcard(path) ? Optional.empty() : files.describe(path);
    }
}
