package org.jebol.corpus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reads the {@code .corpus} files described in {@code corpus/README.md}.
 *
 * <p>Deliberately not written in terms of the interpreter: the corpus has to
 * be readable when the reader is broken, or it cannot be used to find out that
 * the reader is broken.
 */
public final class CorpusReader {

    private static final Path CORPUS_DIRECTORY = Path.of("corpus");
    private static final String FIELD_PREFIX = "--- ";

    private CorpusReader() {
    }

    /** Every entry in every corpus file, in file then document order. */
    public static List<CorpusEntry> allEntries() {
        try (Stream<Path> files = Files.list(CORPUS_DIRECTORY)) {
            List<CorpusEntry> entries = new ArrayList<>();
            files.filter(path -> path.getFileName().toString().endsWith(".corpus"))
                    .sorted()
                    .forEach(path -> entries.addAll(entriesIn(path)));
            return List.copyOf(entries);
        } catch (IOException problem) {
            throw new UncheckedIOException("cannot list " + CORPUS_DIRECTORY, problem);
        }
    }

    /** The complete REBOL programs used as loader and rendering material. */
    public static List<Path> sourceProgrammes() {
        Path sources = CORPUS_DIRECTORY.resolve("sources");
        try (Stream<Path> files = Files.list(sources)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".r"))
                    .sorted()
                    .toList();
        } catch (IOException problem) {
            throw new UncheckedIOException("cannot list " + sources, problem);
        }
    }

    public static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException problem) {
            throw new UncheckedIOException("cannot read " + file, problem);
        }
    }

    private static boolean isComment(String line) {
        return line.equals("#") || line.startsWith("# ");
    }

    static List<CorpusEntry> entriesIn(Path file) {
        List<CorpusEntry> entries = new ArrayList<>();
        EntryBuilder building = null;

        for (String line : read(file).lines().toList()) {
            // A comment is a hash followed by a space, or a hash alone.
            // "#(true)" is a value, not a comment: construction syntax
            // begins with the same character, and treating it as a comment
            // silently emptied every entry whose expected result was a logic.
            if (isComment(line) || (line.isBlank() && building == null)) {
                continue;
            }
            if (line.startsWith(FIELD_PREFIX + "id ")) {
                if (building != null) {
                    entries.add(building.build(file));
                }
                building = new EntryBuilder(value(line, "id"));
                continue;
            }
            if (building == null) {
                continue;
            }
            if (line.startsWith(FIELD_PREFIX)) {
                building.startField(line);
            } else {
                building.appendBodyLine(line);
            }
        }
        if (building != null) {
            entries.add(building.build(file));
        }
        return entries;
    }

    private static String value(String line, String field) {
        return line.substring(FIELD_PREFIX.length() + field.length() + 1).trim();
    }

    /** Accumulates one entry as its lines arrive. */
    private static final class EntryBuilder {

        private final String id;
        private final Set<String> requires = new LinkedHashSet<>();
        private final List<String> notes = new ArrayList<>();
        private final List<String> bodyLines = new ArrayList<>();

        private String origin = "";
        private String openBodyField = "";
        private String code = "";
        private String result;
        private String prints;
        private String error;
        private String types;

        EntryBuilder(String id) {
            this.id = id;
        }

        void startField(String line) {
            closeOpenBodyField();
            String withoutPrefix = line.substring(FIELD_PREFIX.length());
            int space = withoutPrefix.indexOf(' ');
            String name = space < 0 ? withoutPrefix : withoutPrefix.substring(0, space);
            String inlineValue = space < 0 ? "" : withoutPrefix.substring(space + 1).trim();

            switch (name) {
                case "origin" -> origin = inlineValue;
                case "requires" -> requires.addAll(List.of(inlineValue.split("\\s+")));
                case "note" -> notes.add(inlineValue);
                case "code", "result", "prints", "error", "types" -> {
                    openBodyField = name;
                    if (!inlineValue.isEmpty()) {
                        bodyLines.add(inlineValue);
                    }
                }
                default -> throw new IllegalStateException(
                        "unknown corpus field \"" + name + "\" in entry " + id);
            }
        }

        void appendBodyLine(String line) {
            if (!openBodyField.isEmpty()) {
                bodyLines.add(line);
            }
        }

        private void closeOpenBodyField() {
            if (openBodyField.isEmpty()) {
                return;
            }
            String body = String.join("\n", bodyLines).strip();
            switch (openBodyField) {
                case "code" -> code = body;
                case "result" -> result = body;
                case "prints" -> prints = body;
                case "error" -> error = body;
                case "types" -> types = body;
                default -> throw new IllegalStateException(openBodyField);
            }
            bodyLines.clear();
            openBodyField = "";
        }

        CorpusEntry build(Path file) {
            closeOpenBodyField();
            if (code.isEmpty()) {
                throw new IllegalStateException(
                        "corpus entry " + id + " in " + file + " has no code");
            }
            return new CorpusEntry(
                    id,
                    origin,
                    requires,
                    notes,
                    code,
                    Optional.ofNullable(result),
                    Optional.ofNullable(prints),
                    Optional.ofNullable(error),
                    Optional.ofNullable(types).map(text -> List.of(text.split("\\s+"))));
        }
    }
}
