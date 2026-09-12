package org.jebol.mezz;

import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MezzanineLoadTest {

    private static final Path SOURCE = Path.of(
            System.getProperty("java.io.tmpdir"), "rebol3-src", "src", "mezz");

    private record Attempt(String file, int definitions, String outcome) {
    }

    @Test
    @DisplayName("what happens when Rebol's own mezzanine is loaded")
    void theMezzanineIsLoaded() {
        if (!Files.isDirectory(SOURCE)) {
            System.out.println("no Rebol source at " + SOURCE + "; nothing to measure");
            return;
        }
        List<Attempt> attempts = new ArrayList<>();
        try (var files = Files.list(SOURCE)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".reb"))
                    .sorted().toList()) {
                attempts.add(attempt(file));
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }

        long loaded = attempts.stream().filter(a -> a.outcome().equals("loaded")).count();
        System.out.printf("%n%d of %d mezzanine files load:%n", loaded, attempts.size());
        attempts.stream().filter(a -> a.outcome().equals("loaded"))
                .forEach(a -> System.out.printf("    %s (%d definitions)%n",
                        a.file(), a.definitions()));
        System.out.println("what stops the rest:");
        attempts.stream().filter(a -> !a.outcome().equals("loaded"))
                .sorted(java.util.Comparator.comparingInt(
                        (Attempt a) -> a.definitions()).reversed())
                .forEach(a -> System.out.printf("  %-26s %3d defs  %s%n",
                        a.file(), a.definitions(), a.outcome()));

        assertThat(attempts).as("found no mezzanine files to try").isNotEmpty();
    }

    private static String withHeaderRemoved(String withLeadingBlock) {
        int depth = 0;
        for (int at = 0; at < withLeadingBlock.length(); at++) {
            char character = withLeadingBlock.charAt(at);
            if (character == '[') {
                depth++;
            } else if (character == ']' && --depth == 0) {
                return withLeadingBlock.substring(at + 1);
            }
        }
        return withLeadingBlock;
    }

    private static Attempt attempt(Path file) {
        String source;
        try {
            source = Files.readString(file);
        } catch (IOException unreadable) {
            return new Attempt(file.getFileName().toString(), 0, "unreadable");
        }
        int definitions = (int) source.lines()
                .filter(line -> line.matches("^[a-z][a-z0-9?!*+-]*:.*"))
                .count();
        String body = source.replaceFirst("(?is)^\\s*rebol\\s*\\[", "[");
        int afterHeader = body.indexOf(']');
        body = afterHeader >= 0 ? withHeaderRemoved(body) : source;

        Interpreter interpreter = Interpreter.create();
        try {
            interpreter.defineFreshWordsIn(body);
            ScriptOutcome outcome = interpreter.run(body);
            if (outcome.succeeded()) {
                return new Attempt(file.getFileName().toString(), definitions, "loaded");
            }
            String said = outcome.value().toString();
            int named = said.lastIndexOf(": ");
            return new Attempt(file.getFileName().toString(), definitions,
                    outcome.errorId().orElse("failed")
                            + (named >= 0 ? " " + said.substring(named + 2) : ""));
        } catch (RuntimeException refused) {
            return new Attempt(file.getFileName().toString(), definitions,
                    refused.getClass().getSimpleName());
        }
    }
}
