package org.jebol.mezz;

import org.jebol.application.Interpreter;
import org.jebol.domain.read.TranscodeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EveryCarriedRebolFileReadsTest {

    private static final List<Path> ROOTS = List.of(
            Path.of("src", "main", "resources", "org", "jebol"),
            Path.of("src", "testFixtures", "resources"),
            Path.of("rebol3-source", "src", "mezz"),
            Path.of("rebol3-source", "src", "modules"),
            Path.of("rebol3-source", "src", "boot"),
            Path.of("examples"),
            Path.of("scripts"));

    private static final Map<String, String> KNOWN_NOT_TO_READ = Map.of(
            "rebol3-source/src/modules/unicode-utils.reb",
            "an open series closed by something else, or not at all",
            "src/testFixtures/resources/rebol-suite/units/files/invalid-decimal.r",
            "characters that begin no known literal");

    @Test
    @DisplayName("every Rebol file this repository carries reads, or is written down as one that cannot")
    void everyFileReads() {
        List<Path> files = filesToRead();
        assertThat(files)
                .as("found too few Rebol files for this to be measuring anything")
                .hasSizeGreaterThan(300);

        Interpreter reader = Interpreter.create();
        Map<String, String> failures = new LinkedHashMap<>();
        for (Path file : files) {
            try {
                TranscodeResult read = reader.read(Files.readString(file));
                if (!read.succeeded()) {
                    failures.put(shown(file), String.valueOf(read.error().orElse(null)));
                }
            } catch (IOException unreadable) {
                failures.put(shown(file), unreadable.getClass().getSimpleName());
            }
        }

        List<String> unexpected = failures.keySet().stream()
                .filter(file -> !KNOWN_NOT_TO_READ.containsKey(file))
                .toList();
        assertThat(unexpected)
                .as("these do not read and are not written down:%n  %s",
                        String.join("\n  ", unexpected))
                .isEmpty();

        List<String> expectedButRead = KNOWN_NOT_TO_READ.keySet().stream()
                .filter(known -> !failures.containsKey(known))
                .toList();
        assertThat(expectedButRead)
                .as("these now read, so the recorded finding is stale and the "
                        + "entry should come off the list:%n  %s",
                        String.join("\n  ", expectedButRead))
                .isEmpty();

        KNOWN_NOT_TO_READ.forEach((file, expected) -> assertThat(failures.get(file))
                .as("%s should still fail on %s", file, expected)
                .contains(expected));

        System.out.printf("%n%d Rebol files read, %d written down as unreadable%n",
                files.size(), failures.size());
        failures.forEach((file, reason) ->
                System.out.printf("  %-70s %s%n", file, firstLine(reason)));
    }

    private static String firstLine(String reason) {
        return reason.lines().findFirst().orElse(reason);
    }

    private static String shown(Path file) {
        return file.toString().replace('\\', '/');
    }

    private static List<Path> filesToRead() {
        List<Path> found = new ArrayList<>();
        for (Path root : ROOTS) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                found.addAll(files.filter(Files::isRegularFile)
                        .filter(EveryCarriedRebolFileReadsTest::isARebolFile)
                        .sorted()
                        .toList());
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }
        return found;
    }

    private static boolean isARebolFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".reb") || name.endsWith(".r3") || name.endsWith(".r");
    }
}
