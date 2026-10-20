package org.jebol.application;

import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ClasspathFiles implements FilePort {

    private static final Map<String, byte[]> READ = new ConcurrentHashMap<>();
    private static final Set<String> CARRIED = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public byte[] readBytes(String path) {
        byte[] held = READ.computeIfAbsent(path, this::readResource);
        if (!CARRIED.contains(path)) {
            throw new Denied("cannot-open", path + " is not carried by this build", path);
        }
        return held.clone();
    }

    @Override
    public boolean exists(String path) {
        READ.computeIfAbsent(path, this::readResource);
        return CARRIED.contains(path);
    }

    @Override
    public boolean isDirectory(String path) {
        return false;
    }

    @Override
    public Value resolvedFile(String path) {
        return exists(path) ? FileValue.of(path) : NoneValue.none();
    }

    @Override
    public Optional<FileInformation> describe(String path) {
        if (!exists(path)) {
            return Optional.empty();
        }
        return Optional.of(FileInformation.describeAFile(path, readBytes(path).length,
                Optional.empty(), Optional.empty(), Optional.empty()));
    }

    @Override
    public String workingDirectory() {
        return "/";
    }

    @Override
    public String hostPath(String path) {
        return path;
    }

    @Override
    public String locationFor(String path) {
        return path;
    }

    @Override
    public void changeDirectory(String path) {
        throw new Denied("cannot-open", "the build's files have no directories to move between", path);
    }

    @Override
    public List<String> listFileNames(String path) {
        throw new Denied("cannot-open", "the build's files cannot be listed", path);
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

    private Denied refuseChange() {
        return new Denied(Denied.READ_ONLY, "the build's files may only be read");
    }

    private byte[] readResource(String path) {
        try (InputStream reading = ClasspathFiles.class.getResourceAsStream(path)) {
            if (reading == null) {
                return new byte[0];
            }
            CARRIED.add(path);
            return reading.readAllBytes();
        } catch (IOException unreadable) {
            return new byte[0];
        }
    }
}
