package org.jebol.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class CarriedFixtures {

    private static final String INDEX = "/jebol-fixtures.txt";
    private static final List<String> LISTED = readTheIndex();
    private static final Map<String, Path> EXTRACTED = new ConcurrentHashMap<>();
    private static final Path EVERY_EXTRACTION = makeTheRoot();

    private CarriedFixtures() {
    }

    public static Path directory(String under) {
        return EXTRACTED.computeIfAbsent(under, CarriedFixtures::extract);
    }

    public static Path file(String path) {
        int slash = path.lastIndexOf('/');
        Path folder = slash < 0 ? directory("") : directory(path.substring(0, slash));
        return folder.resolve(path.substring(slash + 1));
    }

    private static Path extract(String under) {
        String prefix = under.isEmpty() ? "" : under + "/";
        Path into = EVERY_EXTRACTION.resolve(under.isEmpty() ? "all" : under);
        try {
            Files.createDirectories(into);
            for (String listed : LISTED) {
                if (listed.startsWith(prefix)) {
                    Path landing = into.resolve(listed.substring(prefix.length()));
                    Files.createDirectories(landing.getParent());
                    try (InputStream carried = CarriedFixtures.class.getResourceAsStream("/" + listed)) {
                        if (carried == null) {
                            throw new IllegalStateException(listed + " is listed in " + INDEX + " but not carried");
                        }
                        Files.copy(carried, landing, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
        return into;
    }

    private static List<String> readTheIndex() {
        try {
            List<URL> indexes = Collections.list(CarriedFixtures.class.getClassLoader().getResources(INDEX.substring(1)));
            if (indexes.isEmpty()) {
                throw new IllegalStateException(INDEX + " is not on the classpath; the fixtures' build writes it");
            }
            List<String> listed = new ArrayList<>();
            for (URL index : indexes) {
                listed.addAll(readLines(index));
            }
            return List.copyOf(listed);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static List<String> readLines(URL index) throws IOException {
        try (InputStream in = index.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank())
                    .toList();
        }
    }

    private static Path makeTheRoot() {
        try {
            Path root = Files.createTempDirectory("jebol-fixtures");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> removeEverythingUnder(root)));
            return root;
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private static void removeEverythingUnder(Path root) {
        try (Stream<Path> everything = Files.walk(root)) {
            everything.sorted(Comparator.reverseOrder()).forEach(each -> each.toFile().delete());
        } catch (IOException leftBehind) {
            root.toFile().deleteOnExit();
        }
    }
}
