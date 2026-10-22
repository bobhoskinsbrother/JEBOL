package org.jebol.adapter.web;

import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.WindowPort;
import org.jebol.domain.value.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

public final class PickedFiles implements FilePort {

    private static final Set<String> ESCAPING_NAMES = Set.of("", ".", "..");

    private static final int LONGEST_PICK_NUMBER = 17;

    private static final String NOT_KEPT = "the page named a file this interpreter will not keep: ";

    private final FilePort files;
    private final String root;
    private final PageRoundTrip page;
    private final Map<String, String> saveNamesByLocation = new LinkedHashMap<>();

    public PickedFiles(FilePort files, String root, PageRoundTrip page) {
        this.files = files;
        this.root = root.endsWith("/") ? root : root + "/";
        this.page = page;
    }

    public List<String> keepPick(List<PageAnswer> chosen) {
        List<String> names = chosen.stream().map(this::requireName).toList();
        List<byte[]> contents = chosen.stream().map(this::requireOctets).toList();
        return keepInStore(() -> {
            String folder = makePickFolder();
            List<String> kept = new ArrayList<>();
            for (int at = 0; at < names.size(); at++) {
                String path = folder + names.get(at);
                files.write(path, contents.get(at));
                kept.add(files.locationFor(path));
            }
            return kept;
        });
    }

    public String keepFolder(List<PageAnswer> chosen) {
        List<List<String>> paths = chosen.stream().map(this::requireRelativePath).toList();
        List<byte[]> contents = chosen.stream().map(this::requireOctets).toList();
        Optional<String> top = requireSingleTopFolder(paths);
        return keepInStore(() -> {
            String folder = makePickFolder();
            for (int at = 0; at < paths.size(); at++) {
                writeInsideFolder(folder, paths.get(at), contents.get(at));
            }
            return files.locationFor(top.map(name -> folder + name + "/").orElse(folder));
        });
    }

    public String reserveSave(String name) {
        requireSafeName(name);
        return keepInStore(() -> {
            String path = makePickFolder() + name;
            files.write(path, new byte[0]);
            String location = files.locationFor(path);
            saveNamesByLocation.put(location, name);
            return location;
        });
    }

    @Override
    public void finishWriting(String path) {
        files.finishWriting(path);
        Optional.ofNullable(saveNamesByLocation.get(files.locationFor(path)))
                .filter(name -> files.exists(path))
                .ifPresent(name -> sendSave(path, name));
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
    public void acceptFileFromOutsideRoot(String hostFile) {
        files.acceptFileFromOutsideRoot(hostFile);
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
    public boolean delete(String path) {
        return files.delete(path);
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

    private <T> T keepInStore(Supplier<T> keeping) {
        try {
            return keeping.get();
        } catch (Denied refused) {
            throw new WindowPort.Denied("call-fail", "the chosen file could not be kept: " + refused.getMessage());
        }
    }

    private void sendSave(String path, String name) {
        PageAnswer sent = page.ask(new PageAsk("send-saved-file", false)
                .with("name", name)
                .withOctets("octets", files.readBytes(path)));
        if (!sent.isGiven()) {
            throw new Denied("write-error", sent.why(), path);
        }
    }

    private void writeInsideFolder(String folder, List<String> segments, byte[] contents) {
        files.makeDirectory(folder + String.join("/", segments.subList(0, segments.size() - 1)), true);
        files.write(folder + String.join("/", segments), contents);
    }

    private String makePickFolder() {
        String folder = root + (highestPickNumber() + 1) + "/";
        files.makeDirectory(folder, true);
        return folder;
    }

    private long highestPickNumber() {
        if (!files.isDirectory(root)) {
            return 0;
        }
        return files.listFileNames(root).stream()
                .filter(name -> name.endsWith("/"))
                .map(name -> name.substring(0, name.length() - 1))
                .filter(this::isPickNumber)
                .mapToLong(Long::parseLong)
                .max()
                .orElse(0);
    }

    private boolean isPickNumber(String name) {
        return !name.isEmpty() && name.length() <= LONGEST_PICK_NUMBER
                && name.chars().allMatch(Character::isDigit);
    }

    private String requireName(PageAnswer chosen) {
        String name = chosen.text("name").orElseThrow(() -> refuse("a file with no name"));
        requireSafeName(name);
        return name;
    }

    private void requireSafeName(String name) {
        if (ESCAPING_NAMES.contains(name) || name.contains("/") || name.contains("\\")) {
            throw refuse(name);
        }
    }

    private List<String> requireRelativePath(PageAnswer chosen) {
        String path = chosen.text("path").orElseThrow(() -> refuse("a file in a folder with no path"));
        List<String> segments = List.of(path.split("/", -1));
        if (segments.size() < 2 || segments.stream().anyMatch(ESCAPING_NAMES::contains)
                || path.contains("\\")) {
            throw refuse(path);
        }
        return segments;
    }

    private Optional<String> requireSingleTopFolder(List<List<String>> paths) {
        Set<String> tops = new TreeSet<>();
        paths.forEach(segments -> tops.add(segments.getFirst()));
        if (tops.size() > 1) {
            throw refuse(String.join(" and ", tops) + ", which are not one folder");
        }
        return tops.stream().findFirst();
    }

    private byte[] requireOctets(PageAnswer chosen) {
        return chosen.octets("octets").orElseThrow(() -> refuse(chosen.text("name")
                .or(() -> chosen.text("path")).orElse("a file") + ", sent with no bytes"));
    }

    private WindowPort.Denied refuse(String what) {
        return new WindowPort.Denied("call-fail", NOT_KEPT + what);
    }
}
