package org.jebol.application;

import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class LayeredFiles implements FilePort {

    private final FilePort store;

    private final FilePort carried;

    public LayeredFiles(FilePort store, FilePort carried) {
        this.store = store;
        this.carried = carried;
    }

    public LayeredFiles withStore(FilePort another) {
        return new LayeredFiles(another, carried);
    }

    @Override
    public byte[] readBytes(String path) {
        if (!storeHasFile(path) && carriedHasFile(path)) {
            return carried.readBytes(absolute(path));
        }
        return store.readBytes(path);
    }

    @Override
    public void write(String path, byte[] contents) {
        makeCarriedParentInStore(path);
        store.write(path, contents);
    }

    @Override
    public void writeAt(String path, long position, byte[] contents) {
        copyCarriedFileIntoStore(path);
        store.writeAt(path, position, contents);
    }

    @Override
    public void appendTo(String path, byte[] contents) {
        copyCarriedFileIntoStore(path);
        store.appendTo(path, contents);
    }

    @Override
    public void finishWriting(String path) {
        store.finishWriting(path);
    }

    @Override
    public Held holdOpen(String path) {
        return store.holdOpen(path);
    }

    @Override
    public boolean exists(String path) {
        return store.exists(path) || carried.exists(absolute(path));
    }

    @Override
    public String workingDirectory() {
        return store.workingDirectory();
    }

    @Override
    public String hostPath(String path) {
        return store.hostPath(path);
    }

    @Override
    public Optional<String> findScriptPath(String hostPath) {
        return store.findScriptPath(hostPath);
    }

    @Override
    public void acceptFileFromOutsideRoot(String hostFile) {
        store.acceptFileFromOutsideRoot(hostFile);
    }

    @Override
    public void changeDirectory(String path) {
        makeCarriedDirectoryInStore(absolute(ScriptPaths.directory(path)));
        store.changeDirectory(path);
    }

    @Override
    public void makeDirectory(String path, boolean andItsParents) {
        makeCarriedParentInStore(path);
        store.makeDirectory(path, andItsParents);
    }

    @Override
    public boolean delete(String path) {
        if (!store.exists(path) && carried.exists(absolute(path))) {
            throw refuseChangeTo(path);
        }
        return store.delete(path);
    }

    @Override
    public void rename(String from, String to) {
        if (!store.exists(from) && carried.exists(absolute(from))) {
            throw refuseChangeTo(from);
        }
        makeCarriedParentInStore(to);
        store.rename(from, to);
    }

    @Override
    public List<String> listFileNames(String path) {
        String directory = absolute(ScriptPaths.directory(path));
        boolean inStore = store.isDirectory(path);
        boolean inCarried = carried.isDirectory(directory);
        if (!inCarried) {
            return store.listFileNames(path);
        }
        Set<String> names = new TreeSet<>(carried.listFileNames(directory));
        if (inStore) {
            names.addAll(store.listFileNames(path));
        }
        return List.copyOf(names);
    }

    @Override
    public boolean ignoresCaseInNames() {
        return store.ignoresCaseInNames();
    }

    @Override
    public boolean isDirectory(String path) {
        return store.isDirectory(path) || carried.isDirectory(absolute(path));
    }

    @Override
    public Value resolvedFile(String path) {
        if (path.isEmpty() || store.exists(path) || !carried.exists(absolute(path))) {
            return store.resolvedFile(path);
        }
        return carried.resolvedFile(absolute(path));
    }

    @Override
    public String locationFor(String path) {
        if (store.exists(path) || !carried.exists(absolute(path))) {
            return store.locationFor(path);
        }
        return carried.locationFor(absolute(path));
    }

    @Override
    public Optional<FileInformation> describe(String path) {
        Optional<FileInformation> stored = store.describe(path);
        if (stored.isPresent()) {
            return stored;
        }
        return carried.describe(absolute(path)).map(this::namedFromWorkingDirectory);
    }

    private FileInformation namedFromWorkingDirectory(FileInformation information) {
        return new FileInformation(ScriptPaths.relativeTo(workingDirectory(), information.name()),
                information.isDirectory(), information.size(), information.modified(),
                information.accessed(), information.created());
    }

    private String absolute(String path) {
        return ScriptPaths.absolute(store.workingDirectory(), path);
    }

    private boolean storeHasFile(String path) {
        return store.exists(path) && !store.isDirectory(path);
    }

    private boolean carriedHasFile(String path) {
        String target = absolute(path);
        return !path.endsWith("/") && carried.exists(target) && !carried.isDirectory(target);
    }

    private void copyCarriedFileIntoStore(String path) {
        if (!storeHasFile(path) && carriedHasFile(path)) {
            makeCarriedParentInStore(path);
            store.write(path, carried.readBytes(absolute(path)));
        }
    }

    private void makeCarriedParentInStore(String path) {
        makeCarriedDirectoryInStore(ScriptPaths.parent(absolute(path)));
    }

    private void makeCarriedDirectoryInStore(String directory) {
        if (carried.isDirectory(directory) && !store.isDirectory(directory)) {
            store.makeDirectory(directory, true);
        }
    }

    private Denied refuseChangeTo(String path) {
        return new Denied(Denied.READ_ONLY, "a file the app carries cannot be changed: " + path, path);
    }
}
