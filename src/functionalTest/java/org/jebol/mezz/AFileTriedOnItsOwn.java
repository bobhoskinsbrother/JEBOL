package org.jebol.mezz;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class AFileTriedOnItsOwn {

    private AFileTriedOnItsOwn() {
    }

    record Attempt(String file, int definitions, String outcome) {

        boolean loaded() {
            return outcome.equals("loaded");
        }
    }

    static List<Attempt> attemptsIn(Path directory) {
        List<Attempt> attempts = new ArrayList<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".reb"))
                    .sorted().toList()) {
                attempts.add(attempt(file));
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return attempts;
    }

    static String report(List<Attempt> attempts) {
        long loaded = attempts.stream().filter(Attempt::loaded).count();
        StringBuilder said = new StringBuilder();
        said.append(String.format("%n%d of %d files load:%n", loaded, attempts.size()));
        attempts.stream().filter(Attempt::loaded)
                .forEach(attempt -> said.append(String.format("    %s (%d definitions)%n",
                        attempt.file(), attempt.definitions())));
        said.append("what stops the rest:\n");
        attempts.stream().filter(attempt -> !attempt.loaded())
                .sorted(java.util.Comparator.comparingInt(Attempt::definitions).reversed())
                .forEach(attempt -> said.append(String.format("  %-26s %3d defs  %s%n",
                        attempt.file(), attempt.definitions(), attempt.outcome())));
        return said.toString();
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
        body = afterHeader >= 0 ? removeTheHeader(body) : source;

        Interpreter interpreter = AnInterpreter.create();
        try {
            interpreter.defineNewWordsIn(body);
            ScriptOutcome outcome = interpreter.run(body);
            if (outcome.succeeded()) {
                return new Attempt(file.getFileName().toString(), definitions, "loaded");
            }
            String said = outcome.value().toString();
            int detailStartsAt = said.lastIndexOf(": ");
            return new Attempt(file.getFileName().toString(), definitions,
                    outcome.errorId().orElse("failed")
                            + (detailStartsAt >= 0
                                    ? " " + said.substring(detailStartsAt + 2)
                                    : ""));
        } catch (RuntimeException refused) {
            return new Attempt(file.getFileName().toString(), definitions,
                    refused.getClass().getSimpleName());
        }
    }

    private static String removeTheHeader(String withLeadingBlock) {
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
}
