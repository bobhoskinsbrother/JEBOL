package org.jebol.adapter.android;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class PickedDocuments {

    public static final String MOUNT_FOLDER = "/picked/";

    private static final String FIELD_SEPARATOR = "\t";
    private static final String FOLDER = "folder";
    private static final String FILE = "file";
    private static final int FIELDS = 4;

    private final Path table;
    private final Map<Long, Pick> picks;

    private PickedDocuments(Path table, Map<Long, Pick> picks) {
        this.table = table;
        this.picks = picks;
    }

    public record Pick(long number, String name, String reference, boolean isFolder) {

        public String path() {
            return MOUNT_FOLDER + number + "/" + name + (isFolder ? "/" : "");
        }
    }

    public static PickedDocuments keptIn(Path table) {
        return new PickedDocuments(table, readTable(table));
    }

    public synchronized Pick keep(String reference, String name, boolean isFolder) {
        reread();
        long number = picks.keySet().stream().mapToLong(Long::longValue).max().orElse(0) + 1;
        Pick kept = new Pick(number, name, reference, isFolder);
        picks.put(number, kept);
        save();
        return kept;
    }

    public synchronized Optional<Pick> find(long number) {
        reread();
        return Optional.ofNullable(picks.get(number));
    }

    public synchronized List<Pick> all() {
        reread();
        return List.copyOf(picks.values());
    }

    public synchronized void rename(long number, String reference, String name) {
        reread();
        Optional.ofNullable(picks.get(number)).ifPresent(was -> {
            picks.put(number, new Pick(number, name, reference, was.isFolder()));
            save();
        });
    }

    public synchronized void forget(long number) {
        reread();
        if (picks.remove(number) != null) {
            save();
        }
    }

    private void reread() {
        picks.clear();
        picks.putAll(readTable(table));
    }

    private static Map<Long, Pick> readTable(Path table) {
        Map<Long, Pick> read = new TreeMap<>();
        if (!Files.exists(table)) {
            return read;
        }
        try {
            for (String line : Files.readAllLines(table, StandardCharsets.UTF_8)) {
                readLine(line).ifPresent(pick -> read.put(pick.number(), pick));
            }
        } catch (IOException unreadable) {
            return new TreeMap<>();
        }
        return read;
    }

    private static Optional<Pick> readLine(String line) {
        String[] fields = line.split(FIELD_SEPARATOR, FIELDS);
        if (fields.length != FIELDS || !(fields[1].equals(FILE) || fields[1].equals(FOLDER))) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Pick(Long.parseLong(fields[0]), fields[3], fields[2], fields[1].equals(FOLDER)));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private void save() {
        List<String> lines = new ArrayList<>();
        for (Pick pick : picks.values()) {
            lines.add(String.join(FIELD_SEPARATOR, Long.toString(pick.number()), pick.isFolder() ? FOLDER : FILE,
                    pick.reference(), pick.name().replace("\n", " ")));
        }
        try {
            Files.createDirectories(table.getParent());
            Path written = Files.write(table.resolveSibling(table.getFileName() + ".new"), lines, StandardCharsets.UTF_8);
            Files.move(written, table, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
