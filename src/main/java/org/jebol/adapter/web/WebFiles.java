package org.jebol.adapter.web;

import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class WebFiles implements FilePort {

    private final Map<String, byte[]> files = new LinkedHashMap<>();

    private final Set<String> directories = new TreeSet<>(Set.of("/"));

    private String whereARelativePathCountsFromNow = "/";

    @Override
    public byte[] readBytes(String path) {
        byte[] held = files.get(absolute(path));
        if (held == null) {
            throw cannotOpen(path);
        }
        return held.clone();
    }

    @Override
    public void write(String path, byte[] contents) {
        String target = absolute(path);
        requireTheParentDirectory(target, path);
        files.put(target, contents.clone());
    }

    @Override
    public void writeAt(String path, long position, byte[] contents) {
        String target = absolute(path);
        requireTheParentDirectory(target, path);
        byte[] held = files.getOrDefault(target, new byte[0]);
        int grown = (int) Math.max(held.length, position + contents.length);
        byte[] written = new byte[grown];
        System.arraycopy(held, 0, written, 0, held.length);
        System.arraycopy(contents, 0, written, (int) position, contents.length);
        files.put(target, written);
    }

    @Override
    public void appendTo(String path, byte[] contents) {
        String target = absolute(path);
        requireTheParentDirectory(target, path);
        byte[] held = files.getOrDefault(target, new byte[0]);
        byte[] grown = new byte[held.length + contents.length];
        System.arraycopy(held, 0, grown, 0, held.length);
        System.arraycopy(contents, 0, grown, held.length, contents.length);
        files.put(target, grown);
    }

    @Override
    public boolean exists(String path) {
        String target = absolute(path);
        return namesADirectory(path) ? directories.contains(target) : files.containsKey(target)
                || directories.contains(target);
    }

    @Override
    public String workingDirectory() {
        return whereARelativePathCountsFromNow.equals("/")
                ? "/"
                : whereARelativePathCountsFromNow + "/";
    }

    @Override
    public String hostPathOf(String path) {
        return absolute(path);
    }

    @Override
    public void changeDirectory(String path) {
        String target = absolute(path);
        if (!directories.contains(target)) {
            throw new Denied("cannot-open", path + " is not a directory", directoryNameOf(target));
        }
        whereARelativePathCountsFromNow = target;
    }

    @Override
    public void makeDirectory(String path, boolean andItsParents) {
        String target = absolute(path);
        if (files.containsKey(target)) {
            throw new Denied("no-create", "cannot make a directory at " + path, path);
        }
        if (directories.contains(target)) {
            return;
        }
        if (!andItsParents && !directories.contains(parentOf(target))) {
            throw new Denied("cannot-open", "cannot make a directory at " + path, path);
        }
        for (String made = parentOf(target); !directories.contains(made); made = parentOf(made)) {
            directories.add(made);
        }
        directories.add(target);
    }

    @Override
    public boolean delete(String path) {
        String target = absolute(path);
        if (namesADirectory(path)) {
            if (!directories.contains(target)) {
                return false;
            }
            if (files.keySet().stream().anyMatch(held -> held.startsWith(target + "/"))
                    || directories.stream().anyMatch(held -> held.startsWith(target + "/"))) {
                throw new Denied("no-delete", "cannot delete " + path, path);
            }
            directories.remove(target);
            return true;
        }
        if (directories.contains(target)) {
            throw new Denied("no-delete", "cannot delete " + path, path);
        }
        return files.remove(target) != null;
    }

    @Override
    public void rename(String from, String to) {
        byte[] held = files.remove(absolute(from));
        if (held == null) {
            throw new Denied("cannot-open", "cannot rename " + from);
        }
        String target = absolute(to);
        requireTheParentDirectory(target, to);
        files.put(target, held);
    }

    @Override
    public List<String> namesIn(String path) {
        String target = withSlash(absolute(path));
        if (!target.equals("/") && !directories.contains(withoutTrailingSlash(target))) {
            throw cannotOpen(path);
        }
        Set<String> names = new TreeSet<>();
        for (String held : files.keySet()) {
            if (held.startsWith(target) && held.indexOf('/', target.length()) < 0) {
                names.add(held.substring(target.length()));
            }
        }
        for (String held : directories) {
            if (!held.equals(target) && held.startsWith(target)
                    && held.indexOf('/', target.length()) < 0) {
                names.add(held.substring(target.length()) + "/");
            }
        }
        return new ArrayList<>(names);
    }

    @Override
    public boolean isDirectory(String path) {
        return directories.contains(absolute(path));
    }

    @Override
    public String canonicalPathOf(String path) {
        String target = absolute(path);
        if (directories.contains(target)) {
            return directoryNameOf(target);
        }
        return files.containsKey(target) ? target : null;
    }

    @Override
    public Optional<FileInformation> informationAbout(String path) {
        String target = absolute(path);
        if (directories.contains(target)) {
            return Optional.of(FileInformation.directory(
                    directoryNameOf(target), Optional.empty(), Optional.empty(), Optional.empty()));
        }
        byte[] held = files.get(target);
        return held == null
                ? Optional.empty()
                : Optional.of(FileInformation.file(target, held.length,
                        Optional.empty(), Optional.empty(), Optional.empty()));
    }

    private void requireTheParentDirectory(String target, String asWritten) {
        if (!directories.contains(parentOf(target))) {
            throw cannotOpen(asWritten);
        }
    }

    private FilePort.Denied cannotOpen(String path) {
        return new FilePort.Denied("cannot-open", "cannot read " + path, path);
    }

    private String absolute(String path) {
        Deque<String> segments = new ArrayDeque<>();
        if (!path.startsWith("/")) {
            addEach(withoutTrailingSlash(whereARelativePathCountsFromNow), segments);
        }
        addEach(path, segments);
        return "/" + String.join("/", segments);
    }

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

    private static String parentOf(String target) {
        int lastSlash = target.lastIndexOf('/');
        return lastSlash <= 0 ? "/" : target.substring(0, lastSlash);
    }

    private static String withSlash(String target) {
        return target.endsWith("/") ? target : target + "/";
    }

    private static String withoutTrailingSlash(String target) {
        return target.length() > 1 && target.endsWith("/")
                ? target.substring(0, target.length() - 1)
                : target;
    }

    private static String directoryNameOf(String target) {
        return target.equals("/") ? "/" : target + "/";
    }

    private static boolean namesADirectory(String path) {
        return path.endsWith("/");
    }
}
